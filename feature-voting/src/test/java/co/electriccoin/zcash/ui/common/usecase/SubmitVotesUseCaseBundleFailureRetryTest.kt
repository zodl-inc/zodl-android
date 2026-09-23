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
import cash.z.ecc.android.sdk.model.voting.VotingRoundStepFailure
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionResult
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSessionHolder
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Coverage for the bundle-failure auto-retry mechanism added in `134528bb9`
 * ([SubmitVotesUseCase.runRoundWithBundleFailureRetry] + its private
 * [SubmitVotesUseCase.hasOnlyRetryableBundleFailures] extension), which shipped with zero test
 * coverage. Both functions are private, so this drives them indirectly through the full
 * [SubmitVotesUseCase.invoke] non-Keystone path and asserts on how many times the mocked
 * [VotingRoundSession.run] was invoked plus the resulting outcome -- the same approach
 * `SubmitVotesUseCaseProposalSelectionLockTest` uses for other private-mechanism coverage in this
 * class.
 *
 * Fixture-building pattern copied from `SubmitVotesUseCaseProposalSelectionLockTest` (current,
 * 9-dependency constructor; MockK `mockk<...>()`/`coEvery`/`every`; its `votingSession(...)`/
 * `zashiAccount()` helpers) -- see that file's own doc comment for why the older deleted
 * `SubmitVotesUseCaseRecoveryTest.kt` fixture pattern does not apply here. One addition needed
 * beyond that file's fixtures: `VotingServiceConfig.pirLayout` must carry a non-zero `polyLen`
 * (`VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048)`, the same
 * values `WarmVotingPirProofsUseCaseTest` uses) -- the sibling file's default `VotingPirLayout()`
 * is the crate's UNKNOWN sentinel and throws via `requireKnownPolyLen()` when building
 * `VotingDelegationInputs`, which that file never reaches (its own scenarios fail earlier, before
 * `roundSession.run` is ever called) but every scenario here must reach to invoke `run`.
 *
 * Only the retry mechanism itself is covered here -- proposal-selection locking and other
 * `SubmitVotesUseCase` behavior already has dedicated coverage in
 * `SubmitVotesUseCaseProposalSelectionLockTest`. Only the non-Keystone path
 * (`roundSession.run`) is exercised; `VotingKeystoneSessionHolder.runToCompletion` shares the
 * exact same private `runRoundWithBundleFailureRetry`/`hasOnlyRetryableBundleFailures` call, so a
 * second Keystone-flavored copy of these scenarios would not exercise any additional branch of
 * the mechanism under test.
 */
class SubmitVotesUseCaseBundleFailureRetryTest {
    @Test
    fun `a Failures report with only retryable kinds retries once and the successful retry's report wins`() =
        runTest {
            val roundSession = mockk<VotingRoundSession>(relaxed = true)
            val retryableFailureReport =
                runReport(
                    quiescence = VotingRoundQuiescence.Failures,
                    failures = listOf(stepFailure(kind = "Transport"), stepFailure(kind = "BUSY")),
                    completedProposals = 0
                )
            val successReport =
                runReport(
                    quiescence = VotingRoundQuiescence.NoWorkLeft,
                    failures = emptyList(),
                    completedProposals = 1
                )
            coEvery { roundSession.run(any(), any()) } returnsMany listOf(retryableFailureReport, successReport)

            val result = invokeWithMockedRun(roundSession)

            coVerify(exactly = 2) { roundSession.run(any(), any()) }
            assertEquals(1, result.submittedProposalCount)
        }

    @Test
    fun `a Failures report with only retryable kinds that never clears stops after the max retry budget`() =
        runTest {
            val roundSession = mockk<VotingRoundSession>(relaxed = true)
            val alwaysRetryableFailureReport =
                runReport(
                    quiescence = VotingRoundQuiescence.Failures,
                    failures = listOf(stepFailure(kind = "transport")),
                    completedProposals = 0
                )
            // Every call (the first, plus every retry) returns the same still-failing report --
            // MAX_BUNDLE_FAILURE_RETRIES is 2, so the loop must stop at 1 initial call + 2 retries
            // = 3 total calls rather than looping forever.
            coEvery { roundSession.run(any(), any()) } returns alwaysRetryableFailureReport

            val exception =
                assertFailsWith<VotingSubmissionRecoverableException> {
                    invokeWithMockedRun(roundSession)
                }

            coVerify(exactly = 3) { roundSession.run(any(), any()) }
            // The exhausted-retry report is still just a Failures quiescence -- it surfaces
            // through the normal toVotingErrorOrNull mapping (VotingRoundStepFailure.kind
            // "transport" doesn't match any of that mapper's specific text patterns, so it falls
            // through to the generic case), not a dedicated "retries exhausted"
            // exception/message of its own.
            assertIs<VotingErrors.UnexpectedSdkResponse>(exception.failure)
        }

    @Test
    fun `a Failures report containing even one non-retryable kind does not retry`() =
        runTest {
            val roundSession = mockk<VotingRoundSession>(relaxed = true)
            val mixedFailureReport =
                runReport(
                    quiescence = VotingRoundQuiescence.Failures,
                    failures =
                        listOf(
                            stepFailure(kind = "Transport"),
                            stepFailure(kind = "DelegationTargetMismatch")
                        ),
                    completedProposals = 0
                )
            coEvery { roundSession.run(any(), any()) } returns mixedFailureReport

            assertFailsWith<VotingSubmissionRecoverableException> {
                invokeWithMockedRun(roundSession)
            }

            coVerify(exactly = 1) { roundSession.run(any(), any()) }
        }

    @Test
    fun `a non-Failures quiescence such as PersistedChainTerminal is surfaced immediately without retry`() =
        runTest {
            val roundSession = mockk<VotingRoundSession>(relaxed = true)
            val chainTerminalReport =
                runReport(
                    quiescence = VotingRoundQuiescence.PersistedChainTerminal,
                    failures = emptyList(),
                    completedProposals = 0
                )
            coEvery { roundSession.run(any(), any()) } returns chainTerminalReport

            assertFailsWith<VotingSubmissionRecoverableException> {
                invokeWithMockedRun(roundSession)
            }

            coVerify(exactly = 1) { roundSession.run(any(), any()) }
        }

    /**
     * Wires up the full non-Keystone [SubmitVotesUseCase.invoke] path against [roundSession],
     * whose `run(...)` stubbing each test configures for its own scenario. Everything else is the
     * minimal fixed fixture every scenario shares.
     */
    private suspend fun invokeWithMockedRun(roundSession: VotingRoundSession): VotingSubmissionResult {
        val roundIdBytes = ByteArray(32) { 0x0C }
        val roundId = roundIdBytes.toHex()
        val voteEndTime = Instant.parse("2026-09-23T12:00:00Z")
        val session = votingSession(voteRoundId = roundIdBytes, voteEndTime = voteEndTime, snapshotHeight = 100L)
        val selectedAccount = zashiAccount()

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

        val serviceConfig =
            VotingServiceConfig(
                voteServers = listOf(VotingServiceConfig.ServiceEndpoint(url = "https://vote.example", label = "v1")),
                pirEndpoints = listOf(VotingServiceConfig.ServiceEndpoint(url = "https://pir.example", label = "p1")),
                pirLayout = VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048)
            )
        val synchronizer = mockk<Synchronizer>()
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

        val useCase =
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

        return useCase.invoke(roundId = roundId, choices = mapOf(1 to 0))
    }

    private fun runReport(
        quiescence: VotingRoundQuiescence,
        failures: List<VotingRoundStepFailure>,
        completedProposals: Int
    ): VotingRoundRunReport =
        VotingRoundRunReport(
            quiescence = quiescence,
            plan = null,
            completedProposals = completedProposals,
            totalProposals = 1,
            remainingObligations = 0,
            failures = failures,
            skippedBundles = emptyList(),
            chainOutcomes = emptyList(),
            shareDeliveries = emptyList(),
            delegationsSignedCount = 0
        )

    private fun stepFailure(kind: String): VotingRoundStepFailure =
        VotingRoundStepFailure(
            step = null,
            bundleIndex = 0,
            kind = kind,
            message = "synthetic failure: $kind"
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
