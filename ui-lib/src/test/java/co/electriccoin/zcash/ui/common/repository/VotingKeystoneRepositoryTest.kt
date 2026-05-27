package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.TransparentInfo
import co.electriccoin.zcash.ui.common.model.UnifiedInfo
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.usecase.ResolveVotingRoundSessionUseCase
import com.keystone.module.DecodeResult
import com.keystone.module.ZcashAccounts
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VotingKeystoneRepositoryTest {
    @Test
    fun duplicateSignedPcztIsRejectedBeforeSpendAuthExtraction() =
        runBlocking {
            val duplicateSighash = byteArrayOf(0x01)
            val fixture =
                repositoryFixture(
                    scannedSighash = duplicateSighash,
                    expectedSighash = byteArrayOf(0x02),
                    existingSignatures = mapOf(0 to signature(sighash = duplicateSighash))
                )

            val failure =
                assertFailsWith<VotingKeystoneDuplicateSignatureException> {
                    fixture.storeBundleSignature()
                }

            assertEquals(0, failure.signedBundleIndex)
            assertEquals(1, failure.currentBundleIndex)
            assertEquals(2, failure.bundleCount)
            assertEquals(1, fixture.crypto.extractSighashCalls)
            assertEquals(0, fixture.crypto.extractSpendAuthCalls)
            assertEquals(emptyList(), fixture.recovery.storedSignatures)
        }

    @Test
    fun wrongSignedPcztIsRejectedBeforeSpendAuthExtraction() =
        runBlocking {
            val fixture =
                repositoryFixture(
                    scannedSighash = byteArrayOf(0x03),
                    expectedSighash = byteArrayOf(0x04)
                )

            val failure =
                assertFailsWith<VotingKeystoneWrongSignatureException> {
                    fixture.storeBundleSignature()
                }

            assertEquals(1, failure.currentBundleIndex)
            assertEquals(2, failure.bundleCount)
            assertEquals(1, fixture.crypto.extractSighashCalls)
            assertEquals(0, fixture.crypto.extractSpendAuthCalls)
            assertEquals(emptyList(), fixture.recovery.storedSignatures)
        }

    @Test
    fun matchingSignedPcztStoresSpendAuthSignature() =
        runBlocking {
            val expectedSighash = byteArrayOf(0x05)
            val spendAuthSig = byteArrayOf(0x06)
            val fixture =
                repositoryFixture(
                    scannedSighash = expectedSighash,
                    expectedSighash = expectedSighash,
                    spendAuthSig = spendAuthSig
                )

            fixture.storeBundleSignature()

            assertEquals(1, fixture.crypto.extractSighashCalls)
            assertEquals(1, fixture.crypto.extractSpendAuthCalls)
            val stored = fixture.recovery.storedSignatures.single()
            assertEquals(1, stored.bundleIndex)
            assertContentEquals(spendAuthSig, stored.spendAuthSig)
            assertContentEquals(expectedSighash, stored.sighash)
            assertContentEquals(EXPECTED_RK, stored.rk)
        }

    private suspend fun RepositoryFixture.storeBundleSignature() {
        repository.storeBundleSignature(
            accountUuid = accountUuid,
            roundId = ROUND_ID,
            bundleIndex = CURRENT_BUNDLE_INDEX,
            actionIndex = ACTION_INDEX,
            signedPcztUr = UR("bytes", byteArrayOf(0x01))
        )
    }

    private suspend fun repositoryFixture(
        scannedSighash: ByteArray,
        expectedSighash: ByteArray,
        existingSignatures: Map<Int, VotingKeystoneBundleSignature> = emptyMap(),
        spendAuthSig: ByteArray = byteArrayOf(0x09)
    ): RepositoryFixture {
        val selectedAccount = keystoneAccount()
        val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
        val recovery =
            FakeVotingRecoveryRepository(
                VotingRecoverySnapshot(
                    accountUuid = accountUuid,
                    roundId = ROUND_ID,
                    bundleCount = BUNDLE_COUNT,
                    keystoneBundleSignatures = existingSignatures,
                    pendingKeystoneRequest =
                        VotingPendingKeystoneRequest(
                            bundleIndex = CURRENT_BUNDLE_INDEX,
                            actionIndex = ACTION_INDEX,
                            redactedPcztBase64 = encode(byteArrayOf(0x10)),
                            expectedSighashBase64 = encode(expectedSighash),
                            expectedRkBase64 = encode(EXPECTED_RK)
                        )
                )
            )
        val crypto =
            FakeVotingCryptoClient(
                scannedSighash = scannedSighash,
                spendAuthSig = spendAuthSig
            )

        return RepositoryFixture(
            accountUuid = accountUuid,
            repository =
                VotingKeystoneRepositoryImpl(
                    accountDataSource = FakeAccountDataSource(selectedAccount),
                    resolveVotingRoundSession = unsupportedResolveVotingRoundSession(),
                    votingRecoveryRepository = recovery,
                    votingCryptoClient = crypto.client,
                    votingHotkeySeedProvider = unsupportedProxy(),
                    votingProofPrecomputeRepository = unsupportedProxy(),
                    synchronizerProvider = unsupportedProxy(),
                    keystoneSDKProvider = FakeKeystoneSDKProvider()
                ),
            recovery = recovery,
            crypto = crypto
        )
    }

    private fun unsupportedResolveVotingRoundSession() =
        ResolveVotingRoundSessionUseCase(
            votingApiProvider = unsupportedProxy(),
            votingApiRepository = unsupportedProxy(),
            votingConfigRepository = unsupportedProxy()
        )

    private suspend fun keystoneAccount(): KeystoneAccount =
        KeystoneAccount(
            sdkAccount = AccountFixture.new(),
            unified =
                UnifiedInfo(
                    address = WalletAddressFixture.unified(),
                    balance = WalletBalanceFixture.newLong()
                ),
            transparent =
                TransparentInfo(
                    address = WalletAddressFixture.transparent(),
                    balance = Zatoshi(0)
                ),
            isSelected = true
        )

    private fun signature(sighash: ByteArray) =
        VotingKeystoneBundleSignature(
            spendAuthSigBase64 = encode(byteArrayOf(0x20)),
            sighashBase64 = encode(sighash),
            rkBase64 = encode(byteArrayOf(0x21))
        )

    private data class RepositoryFixture(
        val accountUuid: String,
        val repository: VotingKeystoneRepository,
        val recovery: FakeVotingRecoveryRepository,
        val crypto: FakeVotingCryptoClient
    )

    private class FakeAccountDataSource(
        private val selectedAccountValue: WalletAccount
    ) : AccountDataSource {
        override val allAccounts: StateFlow<List<WalletAccount>?> = MutableStateFlow(listOf(selectedAccountValue))
        override val selectedAccount: Flow<WalletAccount?> = flowOf(selectedAccountValue)
        override val zashiAccount: Flow<ZashiAccount?> = flowOf(null)

        override suspend fun getAllAccounts(): List<WalletAccount> = listOf(selectedAccountValue)

        override suspend fun getSelectedAccount(): WalletAccount = selectedAccountValue

        override suspend fun getZashiAccount(): ZashiAccount = unsupported()

        override suspend fun selectAccount(account: Account) = unsupported()

        override suspend fun selectAccount(account: WalletAccount) = unsupported()

        override suspend fun importKeystoneAccount(
            ufvk: String,
            seedFingerprint: String,
            index: Long,
            birthday: BlockHeight?
        ): Account = unsupported()

        override suspend fun requestNextShieldedAddress(): WalletAddress.Unified = unsupported()

        override suspend fun deleteAccount(account: WalletAccount) = unsupported()
    }

    private class FakeVotingRecoveryRepository(
        private var snapshot: VotingRecoverySnapshot?
    ) : VotingRecoveryRepository {
        val storedSignatures = mutableListOf<StoredSignature>()

        override fun observe(
            accountUuid: String,
            roundId: String
        ): Flow<VotingRecoverySnapshot?> = flowOf(snapshot)

        override suspend fun get(
            accountUuid: String,
            roundId: String
        ): VotingRecoverySnapshot? = snapshot

        override suspend fun store(snapshot: VotingRecoverySnapshot) {
            this.snapshot = snapshot
        }

        override suspend fun storeKeystoneBundleSignature(
            accountUuid: String,
            roundId: String,
            bundleIndex: Int,
            spendAuthSig: ByteArray,
            sighash: ByteArray,
            rk: ByteArray?
        ) {
            storedSignatures += StoredSignature(bundleIndex, spendAuthSig, sighash, rk)
        }

        override suspend fun setPhase(
            accountUuid: String,
            roundId: String,
            phase: VotingRecoveryPhase
        ) = unsupported()

        override suspend fun storeBundleSetup(
            accountUuid: String,
            roundId: String,
            bundleCount: Int,
            eligibleWeight: Long,
            bundleWeights: List<Long>
        ) = unsupported()

        override suspend fun setEligibleWeight(
            accountUuid: String,
            roundId: String,
            eligibleWeight: Long
        ) = unsupported()

        override suspend fun storeVoteEndEpochSeconds(
            accountUuid: String,
            roundId: String,
            voteEndEpochSeconds: Long
        ) = unsupported()

        override suspend fun storeSubmittedAt(
            accountUuid: String,
            roundId: String,
            submittedAtEpochSeconds: Long
        ) = unsupported()

        override suspend fun storeHotkey(
            accountUuid: String,
            roundId: String,
            hotkeyAddress: String
        ) = unsupported()

        override suspend fun storeVoteServerUrls(
            accountUuid: String,
            roundId: String,
            voteServerUrls: List<String>
        ) = unsupported()

        override suspend fun storeDraftChoices(
            accountUuid: String,
            roundId: String,
            draftChoices: Map<Int, Int>
        ) = unsupported()

        override suspend fun storeProposalSelections(
            accountUuid: String,
            roundId: String,
            proposalSelections: Map<Int, VotingProposalSelection>
        ) = unsupported()

        override suspend fun storePendingKeystoneRequest(
            accountUuid: String,
            roundId: String,
            bundleIndex: Int,
            actionIndex: Int,
            redactedPczt: ByteArray,
            expectedSighash: ByteArray,
            expectedRk: ByteArray?
        ) = unsupported()

        override suspend fun setPendingKeystoneRouteStage(
            accountUuid: String,
            roundId: String,
            routeStage: VotingKeystoneRouteStage
        ) = unsupported()

        override suspend fun storePendingKeystoneScanNotice(
            accountUuid: String,
            roundId: String,
            scanNotice: VotingKeystoneScanNotice
        ) = unsupported()

        override suspend fun clearPendingKeystoneScanNotice(
            accountUuid: String,
            roundId: String
        ) = unsupported()

        override suspend fun clearPendingKeystoneRequest(
            accountUuid: String,
            roundId: String
        ) = unsupported()

        override suspend fun skipRemainingKeystoneBundles(
            accountUuid: String,
            roundId: String,
            keepCount: Int
        ): VotingRecoverySnapshot = unsupported()

        override suspend fun storeSingleShareMode(
            accountUuid: String,
            roundId: String,
            singleShareMode: Boolean
        ) = unsupported()

        override suspend fun markProposalSubmitted(
            accountUuid: String,
            roundId: String,
            proposalId: Int
        ) = unsupported()

        override suspend fun clearRound(
            accountUuid: String,
            roundId: String
        ) = unsupported()

        override suspend fun getRoundIdsRequiringShareTracking(accountUuid: String): List<String> = unsupported()
    }

    private data class StoredSignature(
        val bundleIndex: Int,
        val spendAuthSig: ByteArray,
        val sighash: ByteArray,
        val rk: ByteArray?
    )

    private class FakeVotingCryptoClient(
        private val scannedSighash: ByteArray,
        private val spendAuthSig: ByteArray
    ) {
        var extractSighashCalls = 0
        var extractSpendAuthCalls = 0

        val client: VotingCryptoClient =
            Proxy.newProxyInstance(
                VotingCryptoClient::class.java.classLoader,
                arrayOf(VotingCryptoClient::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "extractPcztSighash" -> {
                        extractSighashCalls++
                        scannedSighash
                    }

                    "extractSpendAuthSignatureFromSignedPczt" -> {
                        extractSpendAuthCalls++
                        spendAuthSig
                    }

                    else -> {
                        unsupported()
                    }
                }
            } as VotingCryptoClient
    }

    private class FakeKeystoneSDKProvider : KeystoneSDKProvider {
        override fun decodeQR(result: String): DecodeResult = unsupported()

        override fun resetQRDecoder() = unsupported()

        override fun parseZcashAccounts(ur: UR): ZcashAccounts = unsupported()

        override fun generatePczt(pczt: ByteArray): UREncoder = unsupported()

        override fun parsePczt(ur: UR): ByteArray = SIGNED_PCZT_BYTES
    }

    private companion object {
        const val ROUND_ID = "round"
        const val ACTION_INDEX = 7
        const val CURRENT_BUNDLE_INDEX = 1
        const val BUNDLE_COUNT = 2
        val EXPECTED_RK = byteArrayOf(0x30)
        val SIGNED_PCZT_BYTES = byteArrayOf(0x40)
    }
}

private inline fun <reified T> unsupportedProxy(): T =
    Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "toString" -> "Unsupported ${T::class.java.simpleName}"
            "hashCode" -> 0
            "equals" -> false
            else -> unsupported()
        }
    } as T

private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

private fun unsupported(): Nothing = error("Unexpected call in fake")
