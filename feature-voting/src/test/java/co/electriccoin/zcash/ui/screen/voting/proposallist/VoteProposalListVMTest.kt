package co.electriccoin.zcash.ui.screen.voting.proposallist

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.PrecomputeVotingSnapshotBundlesUseCase
import co.electriccoin.zcash.ui.common.usecase.PrepareVotingRoundUseCase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * voting-5.0.0 background-precompute port (Task 4 fix round): covers the snapshot-bundle
 * precompute trigger added to [VoteProposalListVM]'s `init` block for `REVIEW` mode -- the same
 * VM/screen also serves `VOTING` and `VOTED` modes, neither of which should trigger it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoteProposalListVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `review mode precomputes snapshot bundles for the round`() =
        runTest {
            val precomputeVotingSnapshotBundles = mockk<PrecomputeVotingSnapshotBundlesUseCase>(relaxed = true)

            vm(
                args = VoteProposalListArgs(roundId = ROUND_ID, mode = VoteProposalListMode.REVIEW),
                precomputeVotingSnapshotBundles = precomputeVotingSnapshotBundles
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { precomputeVotingSnapshotBundles(ROUND_ID) }
        }

    @Test
    fun `voted mode does not precompute snapshot bundles`() =
        runTest {
            val precomputeVotingSnapshotBundles = mockk<PrecomputeVotingSnapshotBundlesUseCase>(relaxed = true)

            vm(
                args = VoteProposalListArgs(roundId = ROUND_ID, mode = VoteProposalListMode.VOTED),
                precomputeVotingSnapshotBundles = precomputeVotingSnapshotBundles
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { precomputeVotingSnapshotBundles(any()) }
        }

    @Test
    fun `review mode with an empty round id does not precompute snapshot bundles`() =
        runTest {
            val precomputeVotingSnapshotBundles = mockk<PrecomputeVotingSnapshotBundlesUseCase>(relaxed = true)

            vm(
                args = VoteProposalListArgs(roundId = "", mode = VoteProposalListMode.REVIEW),
                precomputeVotingSnapshotBundles = precomputeVotingSnapshotBundles
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { precomputeVotingSnapshotBundles(any()) }
        }

    private fun vm(
        args: VoteProposalListArgs,
        precomputeVotingSnapshotBundles: PrecomputeVotingSnapshotBundlesUseCase,
        votingSessionStore: VotingSessionStore = mockk(relaxed = true),
        votingApiRepository: VotingApiRepository = mockk(relaxed = true),
        votingRecoveryRepository: VotingRecoveryRepository = mockk(relaxed = true),
        prepareVotingRound: PrepareVotingRoundUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        getVersionInfo: GetVersionInfoProvider = mockk(relaxed = true),
        observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase = mockk(relaxed = true),
    ) = VoteProposalListVM(
        votingSessionStore = votingSessionStore,
        args = args,
        votingApiRepository = votingApiRepository,
        votingRecoveryRepository = votingRecoveryRepository,
        prepareVotingRound = prepareVotingRound,
        precomputeVotingSnapshotBundles = precomputeVotingSnapshotBundles,
        navigationRouter = navigationRouter,
        getVersionInfo = getVersionInfo,
        observeSelectedWalletAccount = observeSelectedWalletAccount,
    )

    private companion object {
        const val ROUND_ID = "round-id"
    }
}
