package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.model.TransparentInfo
import co.electriccoin.zcash.ui.common.model.UnifiedInfo
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingGovernancePczt
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.RoundsListResult
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.usecase.ResolveVotingRoundSessionUseCase
import com.keystone.module.DecodeResult
import com.keystone.module.ZcashAccounts
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VotingKeystoneRepositoryTest {
    @Test
    fun duplicateSignedPcztIsRejectedBeforeSpendAuthExtraction() =
        runTest {
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
        runTest {
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
        runTest {
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

            // The crate-side keystone_signatures preservation guard must also be populated,
            // using the exact same rk/sighash/spendAuthSig triple, so a subsequent round-wide
            // resetVotingSessionState preserves this bundle instead of wiping it.
            val crateCall = fixture.crypto.storeKeystoneSignatureCalls.single()
            assertEquals(DB_HANDLE, crateCall.dbHandle)
            assertEquals(ROUND_ID, crateCall.roundId)
            assertEquals(CURRENT_BUNDLE_INDEX, crateCall.bundleIndex)
            assertContentEquals(spendAuthSig, crateCall.keystoneSig)
            assertContentEquals(expectedSighash, crateCall.keystoneSighash)
            assertContentEquals(EXPECTED_RK, crateCall.rk)
            assertEquals(listOf(VOTING_DB_PATH), fixture.crypto.openVotingDbCalls)
            assertEquals(listOf(DB_HANDLE), fixture.crypto.closeVotingDbCalls)
        }

    @Test
    fun matchingSignedPcztWithoutRkSkipsCrateSidePersistence() =
        runTest {
            val expectedSighash = byteArrayOf(0x0b)
            val spendAuthSig = byteArrayOf(0x0c)
            val fixture =
                repositoryFixture(
                    scannedSighash = expectedSighash,
                    expectedSighash = expectedSighash,
                    spendAuthSig = spendAuthSig,
                    rk = null
                )

            fixture.storeBundleSignature()

            val stored = fixture.recovery.storedSignatures.single()
            assertContentEquals(spendAuthSig, stored.spendAuthSig)
            assertContentEquals(expectedSighash, stored.sighash)
            assertNull(stored.rk)
            assertTrue(fixture.crypto.storeKeystoneSignatureCalls.isEmpty())
            assertTrue(fixture.crypto.openVotingDbCalls.isEmpty())
        }

    @Test
    fun missingBundleCountIsRejectedBeforeSpendAuthExtraction() =
        runTest {
            val fixture =
                repositoryFixture(
                    scannedSighash = byteArrayOf(0x07),
                    expectedSighash = byteArrayOf(0x08),
                    bundleCount = null
                )

            val failure =
                assertFailsWith<IllegalStateException> {
                    fixture.storeBundleSignature()
                }

            assertEquals("Voting round $ROUND_ID has no prepared bundle count", failure.message)
            assertEquals(0, fixture.crypto.extractSighashCalls)
            assertEquals(0, fixture.crypto.extractSpendAuthCalls)
            assertEquals(emptyList(), fixture.recovery.storedSignatures)
        }

    @Test
    fun resetAndRebuildPathMarksBundleRebuiltSinceProof() =
        runTest {
            val selectedAccount = keystoneAccount()
            val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
            val session = resetRebuildSession(RESET_REBUILD_ROUND_ID_HEX)

            val recovery =
                FakeVotingRecoveryRepository(
                    VotingRecoverySnapshot(
                        accountUuid = accountUuid,
                        roundId = RESET_REBUILD_ROUND_ID_HEX,
                        bundleCount = 1
                    )
                )

            val votingApiProvider = mockk<VotingApiProvider>(relaxed = true)
            coEvery { votingApiProvider.fetchServiceConfig() } returns
                VotingServiceConfig.EMPTY.copy(pirLayout = TEST_PIR_LAYOUT)
            coEvery { votingApiProvider.fetchAllRounds() } returns
                RoundsListResult(
                    rounds = emptyList(),
                    sessionsByRoundId = mapOf(RESET_REBUILD_ROUND_ID_HEX to session)
                )
            val resolveVotingRoundSession =
                ResolveVotingRoundSessionUseCase(
                    votingApiProvider = votingApiProvider,
                    votingApiRepository = mockk(relaxed = true),
                    votingConfigRepository = mockk(relaxed = true)
                )

            val synchronizer = mockk<Synchronizer>(relaxed = true)
            every { synchronizer.network } returns ZcashNetwork.Mainnet
            coEvery { synchronizer.redactPcztForSigner(any()) } returns Pczt(byteArrayOf(0x50))
            val synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true)
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns VOTING_WALLET_DB_PATH

            val rebuiltPczt =
                VotingGovernancePczt(
                    pcztBytes = byteArrayOf(0x60),
                    rk = byteArrayOf(0x61),
                    sighash = byteArrayOf(0x62),
                    actionIndex = 3
                )
            val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
            coEvery {
                votingCryptoClient.getWalletNotesJson(any(), any(), any(), any())
            } returns """[{"value":20000000,"position":0}]"""
            coEvery {
                votingCryptoClient.generateNoteWitnessesJson(any(), any(), any(), any(), any(), any())
            } returns "{}"
            coEvery { votingCryptoClient.extractOrchardFvkFromUfvk(any(), any()) } returns byteArrayOf(0x01)
            coEvery { votingCryptoClient.openVotingDb(any()) } returns DB_HANDLE
            coEvery {
                votingCryptoClient.buildGovernancePczt(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("Setup refusing to overwrite existing bundle data") andThen rebuiltPczt

            val encoder = mockk<UREncoder>()
            val keystoneSDKProvider = mockk<KeystoneSDKProvider>(relaxed = true)
            every { keystoneSDKProvider.generatePczt(any()) } returns encoder

            val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>(relaxed = true)
            coEvery { votingHotkeySeedProvider.get(accountUuid) } returns byteArrayOf(0x70)

            val repository =
                VotingKeystoneRepositoryImpl(
                    accountDataSource = FakeAccountDataSource(selectedAccount),
                    resolveVotingRoundSession = resolveVotingRoundSession,
                    votingRecoveryRepository = recovery,
                    votingCryptoClient = votingCryptoClient,
                    votingHotkeySeedProvider = votingHotkeySeedProvider,
                    votingProofPrecomputeRepository = mockk(relaxed = true),
                    synchronizerProvider = synchronizerProvider,
                    keystoneSDKProvider = keystoneSDKProvider
                )

            val signingBundle =
                repository.createPcztEncoder(
                    accountUuid = accountUuid,
                    roundId = RESET_REBUILD_ROUND_ID_HEX
                )

            assertEquals(0, signingBundle.bundleIndex)
            assertEquals(rebuiltPczt.actionIndex, signingBundle.actionIndex)
            val markedCall = recovery.markRebuiltSinceProofCalls.single()
            assertEquals(accountUuid, markedCall.accountUuid)
            assertEquals(RESET_REBUILD_ROUND_ID_HEX, markedCall.roundId)
            assertEquals(0, markedCall.bundleIndex)
        }

    private fun resetRebuildSession(roundIdHex: String) =
        VotingSession(
            voteRoundId = roundIdHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            snapshotHeight = 1L,
            snapshotBlockhash = ByteArray(32) { 0x01 },
            proposalsHash = ByteArray(32) { 0x02 },
            voteEndTime = Instant.ofEpochSecond(3),
            ceremonyStart = Instant.ofEpochSecond(2),
            eaPK = ByteArray(32) { 0x03 },
            vkZkp1 = ByteArray(32) { 0x04 },
            vkZkp2 = ByteArray(32) { 0x05 },
            vkZkp3 = ByteArray(32) { 0x06 },
            ncRoot = ByteArray(32) { 0x07 },
            nullifierIMTRoot = ByteArray(32) { 0x08 },
            creator = "creator",
            title = "Round",
            description = "Round description",
            discussionUrl = null,
            proposals =
                listOf(
                    Proposal(
                        id = 1,
                        title = "Proposal",
                        description = "Proposal description",
                        options =
                            listOf(
                                VoteOption(id = 0, label = "Support"),
                                VoteOption(id = 1, label = "Oppose")
                            )
                    )
                ),
            status = SessionStatus.ACTIVE,
            createdAtHeight = 1L
        )

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
        spendAuthSig: ByteArray = byteArrayOf(0x09),
        bundleCount: Int? = BUNDLE_COUNT,
        rk: ByteArray? = EXPECTED_RK
    ): RepositoryFixture {
        val selectedAccount = keystoneAccount()
        val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
        val recovery =
            FakeVotingRecoveryRepository(
                VotingRecoverySnapshot(
                    accountUuid = accountUuid,
                    roundId = ROUND_ID,
                    bundleCount = bundleCount,
                    keystoneBundleSignatures = existingSignatures,
                    pendingKeystoneRequest =
                        VotingPendingKeystoneRequest(
                            bundleIndex = CURRENT_BUNDLE_INDEX,
                            actionIndex = ACTION_INDEX,
                            redactedPcztBase64 = encode(byteArrayOf(0x10)),
                            expectedSighashBase64 = encode(expectedSighash),
                            expectedRkBase64 = rk?.let(::encode)
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
                    synchronizerProvider = FakeSynchronizerProvider(VOTING_WALLET_DB_PATH),
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
            ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
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
        val markRebuiltSinceProofCalls = mutableListOf<RebuiltSinceProofCall>()
        val clearRebuiltSinceProofCalls = mutableListOf<RebuiltSinceProofCall>()

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
        ) {
            val current = snapshot ?: VotingRecoverySnapshot(accountUuid = accountUuid, roundId = roundId)
            snapshot =
                current.copy(
                    pendingKeystoneRequest =
                        VotingPendingKeystoneRequest(
                            bundleIndex = bundleIndex,
                            actionIndex = actionIndex,
                            redactedPcztBase64 = Base64.getEncoder().encodeToString(redactedPczt),
                            expectedSighashBase64 = Base64.getEncoder().encodeToString(expectedSighash),
                            expectedRkBase64 = expectedRk?.let(Base64.getEncoder()::encodeToString)
                        )
                )
        }

        override suspend fun markBundleRebuiltSinceProof(
            accountUuid: String,
            roundId: String,
            bundleIndex: Int
        ) {
            markRebuiltSinceProofCalls += RebuiltSinceProofCall(accountUuid, roundId, bundleIndex)
            val current = snapshot ?: VotingRecoverySnapshot(accountUuid = accountUuid, roundId = roundId)
            snapshot = current.withBundleRebuiltSinceProof(bundleIndex)
        }

        override suspend fun clearBundleRebuiltSinceProof(
            accountUuid: String,
            roundId: String,
            bundleIndex: Int
        ) {
            clearRebuiltSinceProofCalls += RebuiltSinceProofCall(accountUuid, roundId, bundleIndex)
            snapshot = snapshot?.withBundleRebuiltSinceProofCleared(bundleIndex)
        }

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

    private data class RebuiltSinceProofCall(
        val accountUuid: String,
        val roundId: String,
        val bundleIndex: Int
    )

    private class FakeVotingCryptoClient(
        private val scannedSighash: ByteArray,
        private val spendAuthSig: ByteArray
    ) {
        var extractSighashCalls = 0
        var extractSpendAuthCalls = 0
        val openVotingDbCalls = mutableListOf<String>()
        val closeVotingDbCalls = mutableListOf<Long>()
        val setWalletIdCalls = mutableListOf<SetWalletIdCall>()
        val storeKeystoneSignatureCalls = mutableListOf<StoreKeystoneSignatureCall>()

        val client: VotingCryptoClient =
            Proxy.newProxyInstance(
                VotingCryptoClient::class.java.classLoader,
                arrayOf(VotingCryptoClient::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "extractPcztSighash" -> {
                        extractSighashCalls++
                        scannedSighash
                    }

                    "extractSpendAuthSignatureFromSignedPczt" -> {
                        extractSpendAuthCalls++
                        spendAuthSig
                    }

                    "openVotingDb" -> {
                        openVotingDbCalls += args.valueAt<String>(0)
                        DB_HANDLE
                    }

                    "closeVotingDb" -> {
                        closeVotingDbCalls += args.valueAt<Long>(0)
                        Unit
                    }

                    "setWalletId" -> {
                        setWalletIdCalls +=
                            SetWalletIdCall(
                                dbHandle = args.valueAt(0),
                                walletId = args.valueAt(1),
                                networkId = args.valueAt(2)
                            )
                        Unit
                    }

                    "storeKeystoneSignature" -> {
                        storeKeystoneSignatureCalls +=
                            StoreKeystoneSignatureCall(
                                dbHandle = args.valueAt(0),
                                roundId = args.valueAt(1),
                                bundleIndex = args.valueAt(2),
                                keystoneSig = args.valueAt(3),
                                keystoneSighash = args.valueAt(4),
                                rk = args.valueAt(5)
                            )
                        Unit
                    }

                    else -> {
                        unsupported()
                    }
                }
            } as VotingCryptoClient
    }

    private data class SetWalletIdCall(
        val dbHandle: Long,
        val walletId: String,
        val networkId: Int
    )

    private data class StoreKeystoneSignatureCall(
        val dbHandle: Long,
        val roundId: String,
        val bundleIndex: Int,
        val keystoneSig: ByteArray,
        val keystoneSighash: ByteArray,
        val rk: ByteArray
    )

    private class FakeSynchronizerProvider(
        private val walletDbPath: String
    ) : SynchronizerProvider {
        override val error: StateFlow<SynchronizerError?> = MutableStateFlow(null)
        override val synchronizer: StateFlow<Synchronizer?> = MutableStateFlow(null)
        override val walletBalances: Flow<Map<AccountUuid, AccountBalance>?> = flowOf(null)

        private val fakeSynchronizer: Synchronizer =
            mockk<Synchronizer>(relaxed = true).also { every { it.network } returns ZcashNetwork.Mainnet }

        override suspend fun getSynchronizer(): Synchronizer = fakeSynchronizer

        override suspend fun getSynchronizerOrNull(): Synchronizer? = unsupported()

        override suspend fun getVotingWalletDbPath(): String = walletDbPath

        override fun resetSynchronizer() = unsupported()
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
        const val DB_HANDLE = 7L
        const val VOTING_WALLET_DB_PATH = "/tmp/wallet/data.sqlite3"
        const val VOTING_DB_PATH = "/tmp/wallet/voting.sqlite3"
        val EXPECTED_RK = byteArrayOf(0x30)
        val SIGNED_PCZT_BYTES = byteArrayOf(0x40)
        const val RESET_REBUILD_ROUND_ID_HEX = "aa000000000000000000000000000000000000000000000000000000000000bb"
        val TEST_PIR_LAYOUT = VotingPirLayout(pirDepth = 1, tier0Layers = 1, tier1Layers = 1, polyLen = 4096)
    }
}

private fun <T> Array<Any?>?.valueAt(index: Int): T =
    @Suppress("UNCHECKED_CAST")
    (requireNotNull(this)[index] as T)

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
