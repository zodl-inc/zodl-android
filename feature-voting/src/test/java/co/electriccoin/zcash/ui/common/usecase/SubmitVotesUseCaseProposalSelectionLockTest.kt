package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.VotingRoundSession
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
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
 * Regression test for Task 10 of the round-driver production-completion plan: before
 * [SubmitVotesUseCase] locks in ballot intents with the round driver, it must durably record the
 * proposal choices via [VotingRecoveryRepository.storeProposalSelections]. That call is the single
 * owner of the selection-lock invariant, and it throws [VotingSubmissionRecoverableException] with
 * [VotingErrors.ConflictingProposalSelection] when a retry supplies a different choice than what
 * was already locked in for a proposal. This test confirms the use case does NOT swallow that
 * exception -- it must propagate all the way out of `invoke`, same as every other
 * [VotingSubmissionRecoverableException] raised in this method.
 *
 * Fixture-building pattern recovered from `PrepareVotingRoundUseCaseVoteEndTest` (the closest
 * still-current test for this rewritten round-driver architecture) rather than from the deleted
 * `SubmitVotesUseCaseRecoveryTest.kt` (`git show e84a2f2b1^:...`) -- that deleted file targeted
 * the pre-rewrite ~1700-line per-bundle-per-question implementation with an entirely different,
 * much larger constructor (VotingApiProvider, PirSnapshotResolver, VotingSessionStore, etc.) that
 * no longer exists; its fixture wiring does not carry over to the current 9-dependency
 * constructor. `PrepareVotingRoundUseCaseVoteEndTest`, by contrast, already mocks the current
 * sibling dependencies (`ResolveVotingRoundSessionUseCase`, `VotingRoundSessionContext`,
 * `ZashiAccount`) against this same rewritten architecture.
 */
class SubmitVotesUseCaseProposalSelectionLockTest {
    @Test
    fun `invoke propagates ConflictingProposalSelection from the recovery repository`() =
        runTest {
            val roundIdBytes = ByteArray(32) { 0x0A }
            val roundId = roundIdBytes.toHex()
            val voteEndTime = Instant.parse("2026-09-21T12:00:00Z")
            val session = votingSession(voteRoundId = roundIdBytes, voteEndTime = voteEndTime, snapshotHeight = 100L)
            val selectedAccount = zashiAccount()

            val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
            val prepareVotingRound = mockk<PrepareVotingRoundUseCase>()
            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
            val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>()
            val votingShareTrackingScheduler = mockk<VotingShareTrackingScheduler>(relaxed = true)
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
            val getWalletSeedBytes = mockk<GetWalletSeedBytesUseCase>()

            val serviceConfig =
                VotingServiceConfig(
                    voteServers = listOf(VotingServiceConfig.ServiceEndpoint(url = "https://vote.example", label = "v1"))
                )
            val synchronizer = mockk<Synchronizer>()
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { synchronizer.getTreeState(any()) } returns ByteArray(32)
            coEvery { synchronizer.getVotingTorRuntimeHandle() } returns 0L

            val roundSession = mockk<VotingRoundSession>(relaxed = true)

            coEvery { resolveVotingRoundSession(roundId) } returns
                VotingRoundSessionContext(session = session, serviceConfig = serviceConfig)
            coEvery { getSelectedWalletAccount() } returns selectedAccount
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
            coEvery {
                votingRecoveryRepository.storeProposalSelections(any(), any(), any())
            } throws
                VotingSubmissionRecoverableException(
                    VotingErrors.ConflictingProposalSelection(roundId = roundId, proposalId = 1)
                )

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
                    votingRecoveryRepository = votingRecoveryRepository
                )

            val exception =
                assertFailsWith<VotingSubmissionRecoverableException> {
                    useCase.invoke(roundId = roundId, choices = mapOf(1 to 0))
                }

            // Pin down the specific failure, not just the exception class -- otherwise this test
            // would also (wrongly) pass if some unrelated VotingSubmissionRecoverableException
            // happened to propagate from later in the method.
            val failure = exception.failure
            assertIs<VotingErrors.ConflictingProposalSelection>(failure)
            assertEquals(roundId, failure.roundId)
            assertEquals(1, failure.proposalId)

            // The lock must be checked BEFORE ballot intents are handed to the round driver --
            // confirms the call site's placement, not just that the exception exists somewhere.
            coVerify(exactly = 0) { roundSession.setBallotIntents(any()) }
        }

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
