package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.usecase.ResolveVotingRoundSessionUseCase
import com.sparrowwallet.hummingbird.UR
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for [VotingKeystoneRepositoryImpl.storeBundleSignature]'s three still-current
 * scenarios from the deleted pre-5.0.0 `VotingKeystoneRepositoryTest.kt`
 * (`matchingSignedPcztStoresSpendAuthSignature`, `matchingSignedPcztWithoutRkSkipsCrateSidePersistence`,
 * `missingBundleCountIsRejectedBeforeSpendAuthExtraction`) -- the duplicate/wrong-signature
 * rejection scenarios from that same file are already covered by
 * [VotingKeystoneRepositoryRejectMismatchTest] (via the standalone [rejectMismatchedKeystoneSighash]
 * function this method delegates to), and its sixth scenario
 * (`resetAndRebuildPathMarksBundleRebuiltSinceProof`) tested the removed pre-5.0.0
 * `buildGovernancePczt`/`VotingGovernancePczt` flow directly -- genuinely obsolete, not ported.
 * Fills the rest of nit #13 of Milan's review of PR #6 for this class.
 */
class VotingKeystoneRepositoryStoreBundleSignatureTest {
    @Test
    fun `a matching signature with an rk persists both crate-side and to recovery`() =
        runTest {
            val expectedSighash = byteArrayOf(0x05)
            val spendAuthSig = byteArrayOf(0x06)
            val expectedRk = byteArrayOf(0x07)
            val env =
                Env(
                    expectedSighash = expectedSighash,
                    expectedRk = expectedRk
                )
            env.stubSignedPczt(scannedSighash = expectedSighash, spendAuthSig = spendAuthSig)

            env.storeBundleSignature()

            coVerify(exactly = 1) {
                env.votingRecoveryRepository.storeKeystoneBundleSignature(
                    accountUuid = env.accountUuid,
                    roundId = env.roundId,
                    bundleIndex = env.bundleIndex,
                    spendAuthSig = spendAuthSig,
                    sighash = expectedSighash,
                    rk = expectedRk
                )
            }
            // The crate-side keystone_signatures preservation guard is also populated, with the
            // exact same rk/sighash/spendAuthSig triple, so a subsequent round-wide
            // resetVotingSessionState preserves this bundle instead of wiping it.
            coVerify(exactly = 1) {
                env.votingCryptoClient.setWalletId(env.dbHandle, env.accountUuid, any())
            }
            coVerify(exactly = 1) {
                env.votingCryptoClient.storeKeystoneSignatures(
                    dbHandle = env.dbHandle,
                    roundId = env.roundId,
                    signatures =
                        listOf(
                            cash.z.ecc.android.sdk.model.voting.VotingKeystoneSignatureInput(
                                bundleIndex = env.bundleIndex,
                                sig = spendAuthSig,
                                sighash = expectedSighash,
                                rk = expectedRk
                            )
                        )
                )
            }
            coVerify(exactly = 1) { env.votingCryptoClient.openVotingDb(any()) }
            coVerify(exactly = 1) { env.votingCryptoClient.closeVotingDb(env.dbHandle) }
        }

    @Test
    fun `a matching signature without an rk skips crate-side persistence entirely`() =
        runTest {
            val expectedSighash = byteArrayOf(0x0b)
            val spendAuthSig = byteArrayOf(0x0c)
            val env =
                Env(
                    expectedSighash = expectedSighash,
                    expectedRk = null
                )
            env.stubSignedPczt(scannedSighash = expectedSighash, spendAuthSig = spendAuthSig)

            env.storeBundleSignature()

            coVerify(exactly = 1) {
                env.votingRecoveryRepository.storeKeystoneBundleSignature(
                    accountUuid = env.accountUuid,
                    roundId = env.roundId,
                    bundleIndex = env.bundleIndex,
                    spendAuthSig = spendAuthSig,
                    sighash = expectedSighash,
                    rk = null
                )
            }
            coVerify(exactly = 0) { env.votingCryptoClient.storeKeystoneSignatures(any(), any(), any()) }
            coVerify(exactly = 0) { env.votingCryptoClient.openVotingDb(any()) }
        }

    @Test
    fun `a missing prepared bundle count is rejected before any sighash or signature extraction`() =
        runTest {
            val env =
                Env(
                    expectedSighash = byteArrayOf(0x07),
                    expectedRk = byteArrayOf(0x08),
                    bundleCount = null
                )

            val failure =
                assertFailsWith<IllegalStateException> {
                    env.storeBundleSignature()
                }

            assertEquals("Voting round ${env.roundId} has no prepared bundle count", failure.message)
            coVerify(exactly = 0) { env.votingCryptoClient.extractPcztSighash(any()) }
            coVerify(exactly = 0) { env.votingCryptoClient.extractSpendAuthSig(any(), any()) }
            coVerify(exactly = 0) {
                env.votingRecoveryRepository.storeKeystoneBundleSignature(any(), any(), any(), any(), any(), any())
            }
        }

    /** Shared fixture wiring for [VotingKeystoneRepositoryImpl.storeBundleSignature]. */
    private class Env(
        expectedSighash: ByteArray,
        expectedRk: ByteArray?,
        bundleCount: Int? = 2
    ) {
        val roundId = "round-store-signature"
        val bundleIndex = 1
        val actionIndex = 0
        val dbHandle = 99L
        val walletDbPath = "/tmp/voting-store-signature/wallet.db"

        // persistKeystoneSignatureCrateSide derives its own DB path from walletDbPath via
        // VotingKeystoneRepositoryImpl's private deriveVotingDbPath (sibling "voting.sqlite3"
        // file, not walletDbPath itself) -- computed the same way here rather than hardcoded, so
        // openVotingDb's stub/assertions match what the real code actually passes.
        val votingDbPath =
            java.io
                .File(walletDbPath)
                .parentFile!!
                .resolve("voting.sqlite3")
                .absolutePath
        val accountUuid: String

        val accountDataSource = mockk<AccountDataSource>()
        val votingKeystoneSessionHolder = mockk<VotingKeystoneSessionHolder>(relaxed = true)
        val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
        val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>()
        val synchronizerProvider = mockk<SynchronizerProvider>()
        val keystoneSDKProvider = mockk<KeystoneSDKProvider>()
        val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        val signedPcztUr = mockk<UR>()

        private val repository =
            VotingKeystoneRepositoryImpl(
                accountDataSource = accountDataSource,
                votingKeystoneSessionHolder = votingKeystoneSessionHolder,
                votingCryptoClient = votingCryptoClient,
                resolveVotingRoundSession = resolveVotingRoundSession,
                votingRecoveryRepository = votingRecoveryRepository,
                votingHotkeySeedProvider = votingHotkeySeedProvider,
                synchronizerProvider = synchronizerProvider,
                keystoneSDKProvider = keystoneSDKProvider,
                votingProofPrecomputeRepository = votingProofPrecomputeRepository
            )

        init {
            val selectedAccount = keystoneAccount()
            accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            coEvery { accountDataSource.getSelectedAccount() } returns selectedAccount
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns walletDbPath
            val synchronizer = mockk<Synchronizer>()
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { votingCryptoClient.openVotingDb(votingDbPath) } returns dbHandle

            val pendingRequest =
                VotingPendingKeystoneRequest(
                    bundleIndex = bundleIndex,
                    actionIndex = actionIndex,
                    redactedPcztBase64 = encode(byteArrayOf(0x01)),
                    expectedSighashBase64 = encode(expectedSighash),
                    expectedRkBase64 = expectedRk?.let(::encode)
                )
            coEvery { votingRecoveryRepository.get(accountUuid, roundId) } returns
                VotingRecoverySnapshot(
                    accountUuid = accountUuid,
                    roundId = roundId,
                    bundleCount = bundleCount,
                    pendingKeystoneRequest = pendingRequest
                )
        }

        fun stubSignedPczt(
            scannedSighash: ByteArray,
            spendAuthSig: ByteArray
        ) {
            val signedPcztBytes = byteArrayOf(0x20)
            every { keystoneSDKProvider.parsePczt(signedPcztUr) } returns signedPcztBytes
            coEvery { votingCryptoClient.extractPcztSighash(signedPcztBytes) } returns scannedSighash
            coEvery {
                votingCryptoClient.extractSpendAuthSig(signedPcztBytes = signedPcztBytes, actionIndex = actionIndex)
            } returns spendAuthSig
        }

        suspend fun storeBundleSignature() =
            repository.storeBundleSignature(
                accountUuid = accountUuid,
                roundId = roundId,
                bundleIndex = bundleIndex,
                actionIndex = actionIndex,
                signedPcztUr = signedPcztUr
            )

        private fun encode(bytes: ByteArray): String =
            java.util.Base64
                .getEncoder()
                .encodeToString(bytes)

        private fun keystoneAccount(): KeystoneAccount =
            KeystoneAccount(
                sdkAccount = AccountFixture.new(),
                unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
                transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
                orchardBalance = WalletBalanceFixture.newLong(),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparentBalance = Zatoshi(0),
                isSelected = true
            )
    }
}
