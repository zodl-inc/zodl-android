package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.VotingRoundSession
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingRoundQuiescence
import cash.z.ecc.android.sdk.model.voting.VotingRoundRunReport
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSessionHolder
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Core non-Keystone success-path coverage for [SubmitVotesUseCase.invoke] -- fixture pattern
 * copied from `SubmitVotesUseCaseBundleFailureRetryTest` (current, 11-dependency constructor;
 * MockK `mockk<...>()`/`coEvery`/`every`). That file and `SubmitVotesUseCaseProposalSelectionLockTest`
 * cover the retry mechanism and the proposal-selection lock respectively; neither asserts on the
 * plain happy path itself -- what gets called, in what order, and what the recovery-repository
 * side effects are on a clean successful submission. This fills that gap (nit #13 of Milan's
 * review of PR #6: several pre-4.0 test files covering this class were deleted during the
 * round-driver port with only partial replacement coverage).
 */
class SubmitVotesUseCaseSuccessPathTest {
    @Test
    fun `a successful submission records every recovery side effect and returns the report's completed count`() =
        runTest {
            val env = Env()
            val report = env.runReport(quiescence = VotingRoundQuiescence.NoWorkLeft, completedProposals = 1)
            env.stubRun(report)

            val result = env.invoke(choices = mapOf(1 to 0))

            assertEquals(1, result.submittedProposalCount)
            coVerify(exactly = 1) {
                env.votingRecoveryRepository.setPhase(
                    env.accountUuidString,
                    env.roundId,
                    VotingRecoveryPhase.VOTES_SUBMITTED
                )
            }
            coVerify(exactly = 1) {
                env.votingRecoveryRepository.markProposalSubmitted(env.accountUuidString, env.roundId, 1)
            }
            coVerify(exactly = 1) {
                env.votingRecoveryRepository.storeSubmittedAt(env.accountUuidString, env.roundId, any())
            }
            coVerify(exactly = 1) { env.votingShareTrackingScheduler.schedule(env.roundId) }
        }

    @Test
    fun `empty choices short-circuits before touching any session, DB, or recovery state`() =
        runTest {
            val env = Env()

            val result = env.invoke(choices = emptyMap())

            assertEquals(0, result.submittedProposalCount)
            coVerify(exactly = 0) { env.votingCryptoClient.openVotingDb(any()) }
            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.cancelAndAwaitPrecompute(any(), any()) }
            coVerify(exactly = 0) { env.votingRecoveryRepository.setPhase(any(), any(), any()) }
        }

    @Test
    fun `background precompute is cancelled and awaited before the round session opens`() =
        runTest {
            val env = Env()
            env.stubRun(env.runReport(quiescence = VotingRoundQuiescence.NoWorkLeft, completedProposals = 1))

            env.invoke(choices = mapOf(1 to 0))

            // Milan's review of PR #6, should-fix #5's own regression: this call must happen
            // before openRoundSession, or a still-running precompute job contends with it for the
            // same native (dbPath, walletId) lock -- see SubmitVotesUseCase's own "Important #1"
            // comment.
            coVerifyOrder {
                env.votingProofPrecomputeRepository.cancelAndAwaitPrecompute(env.accountUuidString, env.roundId)
                env.votingCryptoClient.openRoundSession(
                    dbHandle = any(),
                    torRuntime = any(),
                    roundId = any(),
                    proposals = any(),
                    hotkeySecret = any(),
                    chainEndpoints = any(),
                    operationEpoch = any(),
                    configuredHelperUrls = any(),
                    voteTreeNodeUrls = any(),
                    ceremonyStartSeconds = any(),
                    voteEndTimeSeconds = any()
                )
            }
        }

    @Test
    fun `a pinned non-zero Tor runtime handle is released exactly once when the round session closes`() =
        runTest {
            val env = Env()
            env.stubRun(env.runReport(quiescence = VotingRoundQuiescence.NoWorkLeft, completedProposals = 1))
            coEvery { env.synchronizer.getVotingTorRuntimeHandle() } returns TOR_RUNTIME_HANDLE
            coEvery { env.synchronizer.releaseVotingTorRuntimeHandle() } returns Unit

            env.invoke(choices = mapOf(1 to 0))

            coVerify(exactly = 1) { env.synchronizer.releaseVotingTorRuntimeHandle() }
        }

    @Test
    fun `a zero Tor runtime handle (Tor disabled) never triggers a release call`() =
        runTest {
            val env = Env()
            env.stubRun(env.runReport(quiescence = VotingRoundQuiescence.NoWorkLeft, completedProposals = 1))
            // Env's default is already 0L (Tor unavailable) -- explicit here for clarity.
            coEvery { env.synchronizer.getVotingTorRuntimeHandle() } returns 0L

            env.invoke(choices = mapOf(1 to 0))

            coVerify(exactly = 0) { env.synchronizer.releaseVotingTorRuntimeHandle() }
        }

    /**
     * Shared fixture wiring for the non-Keystone [SubmitVotesUseCase.invoke] path, one instance
     * per test. Mirrors `SubmitVotesUseCaseBundleFailureRetryTest`'s own fixture exactly (same
     * mock set, same default stubs) so this file stays a drop-in sibling rather than a divergent
     * pattern.
     */
    private class Env {
        val roundIdBytes = ByteArray(32) { 0x0D }
        val roundId = roundIdBytes.toHex()
        val accountUuidString: String

        val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        val prepareVotingRound = mockk<PrepareVotingRoundUseCase>()
        val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
        val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>()
        val votingShareTrackingScheduler = mockk<VotingShareTrackingScheduler>(relaxed = true)
        val votingKeystoneSessionHolder = mockk<VotingKeystoneSessionHolder>(relaxed = true)
        val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        val synchronizerProvider = mockk<SynchronizerProvider>()
        val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
        val getWalletSeedBytes = mockk<GetWalletSeedBytesUseCase>()
        val synchronizer = mockk<Synchronizer>()
        val roundSession = mockk<VotingRoundSession>(relaxed = true)

        private val useCase =
            SubmitVotesUseCase(
                resolveVotingRoundSession = resolveVotingRoundSession,
                votingCryptoClient = votingCryptoClient,
                votingHotkeySeedProvider = votingHotkeySeedProvider,
                synchronizerProvider = synchronizerProvider,
                getSelectedWalletAccount = getSelectedWalletAccount,
                getWalletSeedBytes = getWalletSeedBytes,
                prepareVotingRound = prepareVotingRound,
                votingShareTrackingScheduler = votingShareTrackingScheduler,
                votingRecoveryRepository = votingRecoveryRepository,
                votingKeystoneSessionHolder = votingKeystoneSessionHolder,
                votingProofPrecomputeRepository = votingProofPrecomputeRepository
            )

        init {
            val voteEndTime = Instant.parse("2026-09-23T12:00:00Z")
            val session = votingSession(voteRoundId = roundIdBytes, voteEndTime = voteEndTime, snapshotHeight = 100L)
            val selectedAccount = zashiAccount()
            accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            val serviceConfig =
                VotingServiceConfig(
                    voteServers =
                        listOf(VotingServiceConfig.ServiceEndpoint(url = "https://vote.example", label = "v1")),
                    pirEndpoints =
                        listOf(VotingServiceConfig.ServiceEndpoint(url = "https://pir.example", label = "p1")),
                    pirLayout = VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048)
                )
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { synchronizer.getTreeState(any()) } returns ByteArray(32)
            coEvery { synchronizer.getVotingTorRuntimeHandle() } returns 0L

            coEvery { resolveVotingRoundSession(roundId) } returns
                VotingRoundSessionContext(session = session, serviceConfig = serviceConfig)
            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { getWalletSeedBytes() } returns ByteArray(32)
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/voting-wallet.db"
            coEvery { prepareVotingRound(roundId) } returns
                VotingRoundPreparationResult.Ready(
                    roundId = roundId,
                    bundleCount = 1,
                    eligibleWeight = 1L,
                    hotkeyAddress = "hotkey-address"
                )
            coEvery { votingHotkeySeedProvider.get(any()) } returns ByteArray(32)
            coEvery { votingCryptoClient.openVotingDb(any()) } returns 1L
            coEvery {
                votingCryptoClient.openRoundSession(
                    dbHandle = any(),
                    torRuntime = any(),
                    roundId = any(),
                    proposals = any(),
                    hotkeySecret = any(),
                    chainEndpoints = any(),
                    operationEpoch = any(),
                    configuredHelperUrls = any(),
                    voteTreeNodeUrls = any(),
                    ceremonyStartSeconds = any(),
                    voteEndTimeSeconds = any()
                )
            } returns roundSession
        }

        fun stubRun(report: VotingRoundRunReport) {
            coEvery { roundSession.run(any(), any()) } returns report
        }

        suspend fun invoke(choices: Map<Int, Int>) = useCase.invoke(roundId = roundId, choices = choices)

        fun runReport(
            quiescence: VotingRoundQuiescence,
            completedProposals: Int
        ): VotingRoundRunReport =
            VotingRoundRunReport(
                quiescence = quiescence,
                plan = null,
                completedProposals = completedProposals,
                totalProposals = 1,
                remainingObligations = 0,
                failures = emptyList(),
                skippedBundles = emptyList(),
                chainOutcomes = emptyList(),
                shareDeliveries = emptyList(),
                delegationsSignedCount = 0
            )

        private fun zashiAccount(): ZashiAccount =
            ZashiAccount(
                sdkAccount = AccountFixture.new(),
                unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
                transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
                saplingAddress = WalletAddressFixture.SAPLING_ADDRESS_STRING,
                orchardBalance = WalletBalanceFixture.newLong(),
                saplingBalance = WalletBalanceFixture.newLong(0, 0, 0),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparentBalance = Zatoshi(0),
                isSelected = true
            )

        private fun votingSession(
            voteRoundId: ByteArray,
            voteEndTime: Instant,
            snapshotHeight: Long
        ) = VotingSession(
            voteRoundId = voteRoundId,
            snapshotHeight = snapshotHeight,
            snapshotBlockhash = ByteArray(32) { 2 },
            proposalsHash = ByteArray(32) { 3 },
            voteEndTime = voteEndTime,
            ceremonyStart = voteEndTime.minusSeconds(3_600),
            eaPK = ByteArray(32) { 4 },
            vkZkp1 = ByteArray(32) { 5 },
            vkZkp2 = ByteArray(32) { 6 },
            vkZkp3 = ByteArray(32) { 7 },
            ncRoot = ByteArray(32) { 8 },
            nullifierIMTRoot = ByteArray(32) { 9 },
            creator = "creator",
            title = "Voting Round",
            description = "Voting round description",
            discussionUrl = null,
            proposals =
                listOf(
                    Proposal(
                        id = 1,
                        title = "Proposal",
                        description = "Proposal description",
                        options =
                            listOf(
                                VoteOption(id = 0, label = "No"),
                                VoteOption(id = 1, label = "Yes")
                            )
                    )
                ),
            status = SessionStatus.ACTIVE,
            createdAtHeight = 1
        )
    }
}

private const val TOR_RUNTIME_HANDLE = 42L
