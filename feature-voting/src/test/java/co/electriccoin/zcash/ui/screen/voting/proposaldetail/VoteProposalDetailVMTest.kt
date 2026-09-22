package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.PrecomputeVotingSnapshotBundlesUseCase
import co.electriccoin.zcash.ui.common.usecase.WarmVotingPirProofsUseCase
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
 * voting-5.0.0 background-precompute port (Task 4 fix round): covers the screen-entry trigger
 * wiring added to [VoteProposalDetailVM]'s `init` block, which was previously untested. Most
 * importantly, asserts the `!args.isReadOnly` eligibility-confirmed proxy actually gates
 * [PrecomputeVotingSnapshotBundlesUseCase] -- a read-only (already-voted) visit must not trigger
 * it, while an active voting/review visit must.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoteProposalDetailVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `read-only proposal detail warms PIR proofs but does not precompute snapshot bundles`() =
        runTest {
            val warmVotingPirProofs = mockk<WarmVotingPirProofsUseCase>(relaxed = true)
            val precomputeVotingSnapshotBundles = mockk<PrecomputeVotingSnapshotBundlesUseCase>(relaxed = true)

            vm(
                args = args(isReadOnly = true),
                warmVotingPirProofs = warmVotingPirProofs,
                precomputeVotingSnapshotBundles = precomputeVotingSnapshotBundles
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { warmVotingPirProofs() }
            coVerify(exactly = 0) { precomputeVotingSnapshotBundles(any()) }
        }

    @Test
    fun `active voting proposal detail warms PIR proofs and precomputes snapshot bundles`() =
        runTest {
            val warmVotingPirProofs = mockk<WarmVotingPirProofsUseCase>(relaxed = true)
            val precomputeVotingSnapshotBundles = mockk<PrecomputeVotingSnapshotBundlesUseCase>(relaxed = true)

            vm(
                args = args(isReadOnly = false),
                warmVotingPirProofs = warmVotingPirProofs,
                precomputeVotingSnapshotBundles = precomputeVotingSnapshotBundles
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { warmVotingPirProofs() }
            coVerify(exactly = 1) { precomputeVotingSnapshotBundles(ROUND_ID) }
        }

    @Test
    fun `review-edit proposal detail is not read-only and precomputes snapshot bundles`() =
        runTest {
            val warmVotingPirProofs = mockk<WarmVotingPirProofsUseCase>(relaxed = true)
            val precomputeVotingSnapshotBundles = mockk<PrecomputeVotingSnapshotBundlesUseCase>(relaxed = true)

            vm(
                args = args(isReadOnly = false, isEditingFromReview = true),
                warmVotingPirProofs = warmVotingPirProofs,
                precomputeVotingSnapshotBundles = precomputeVotingSnapshotBundles
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { precomputeVotingSnapshotBundles(ROUND_ID) }
        }

    private fun args(
        isReadOnly: Boolean,
        isEditingFromReview: Boolean = false
    ) = VoteProposalDetailArgs(
        proposalId = 1,
        roundId = ROUND_ID,
        isEditingFromReview = isEditingFromReview,
        isReadOnly = isReadOnly
    )

    private fun vm(
        args: VoteProposalDetailArgs,
        warmVotingPirProofs: WarmVotingPirProofsUseCase,
        precomputeVotingSnapshotBundles: PrecomputeVotingSnapshotBundlesUseCase,
        votingApiRepository: VotingApiRepository = mockk(relaxed = true),
        configurationRepository: ConfigurationRepository = mockk(relaxed = true),
        votingChainConfigRepository: VotingChainConfigRepository = mockk(relaxed = true),
        votingRecoveryRepository: VotingRecoveryRepository = mockk(relaxed = true),
        votingSessionStore: VotingSessionStore = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase = mockk(relaxed = true),
    ) = VoteProposalDetailVM(
        args = args,
        votingApiRepository = votingApiRepository,
        configurationRepository = configurationRepository,
        votingChainConfigRepository = votingChainConfigRepository,
        votingRecoveryRepository = votingRecoveryRepository,
        votingSessionStore = votingSessionStore,
        navigationRouter = navigationRouter,
        warmVotingPirProofs = warmVotingPirProofs,
        precomputeVotingSnapshotBundles = precomputeVotingSnapshotBundles,
        observeSelectedWalletAccount = observeSelectedWalletAccount,
    )

    private companion object {
        const val ROUND_ID = "round-id"
    }
}
