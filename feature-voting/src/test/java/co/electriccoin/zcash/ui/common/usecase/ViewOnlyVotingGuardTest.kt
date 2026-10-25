package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.howtovote.VoteHowToVoteVM
import co.electriccoin.zcash.voting.VotingHomeHooksImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * A view-only account holds no spend authority, so it can never vote: submitting and preparing a
 * round refuse it with [AccountOperationUnsupportedException] of kind VIEW_ONLY before any voting
 * work starts, background share tracking has nothing to do for it, the "How to vote" screen
 * leaves at once, and Home never restores a pending voting route for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewOnlyVotingGuardTest {
    private val viewOnly =
        ViewOnlyAccount(
            sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() })),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = 0,
        )

    private val getSelectedWalletAccount =
        mockk<GetSelectedWalletAccountUseCase> {
            coEvery { this@mockk() } returns viewOnly
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submittingVotesIsRefused() =
        runTest {
            val prepareVotingRound = mockk<PrepareVotingRoundUseCase>(relaxed = true)
            val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
            val useCase =
                SubmitVotesUseCase(
                    resolveVotingRoundSession = mockk(relaxed = true),
                    votingCryptoClient = mockk(relaxed = true),
                    votingHotkeySeedProvider = mockk(relaxed = true),
                    synchronizerProvider = mockk(relaxed = true),
                    getSelectedWalletAccount = getSelectedWalletAccount,
                    getWalletSeedBytes = mockk(relaxed = true),
                    prepareVotingRound = prepareVotingRound,
                    votingShareTrackingScheduler = mockk(relaxed = true),
                    votingRecoveryRepository = mockk(relaxed = true),
                    votingKeystoneSessionHolder = mockk(relaxed = true),
                    votingProofPrecomputeRepository = votingProofPrecomputeRepository,
                )

            val thrown =
                assertFailsWith<AccountOperationUnsupportedException> {
                    useCase(roundId = "round", choices = mapOf(0 to 1))
                }

            assertEquals(AccountOperationUnsupportedException.Kind.VIEW_ONLY, thrown.kind)
            coVerify(exactly = 0) { prepareVotingRound(any()) }
            coVerify(exactly = 0) { votingProofPrecomputeRepository.cancelAndAwaitPrecompute(any(), any()) }
        }

    @Test
    fun preparingARoundIsRefused() =
        runTest {
            val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
            val useCase =
                PrepareVotingRoundUseCase(
                    resolveVotingRoundSession = mockk(relaxed = true),
                    votingRecoveryRepository = mockk(relaxed = true),
                    votingSessionStore = mockk(relaxed = true),
                    votingCryptoClient = mockk(relaxed = true),
                    votingHotkeySeedProvider = mockk(relaxed = true),
                    votingProofPrecomputeRepository = votingProofPrecomputeRepository,
                    synchronizerProvider = mockk(relaxed = true),
                    getSelectedWalletAccount = getSelectedWalletAccount,
                )

            val thrown = assertFailsWith<AccountOperationUnsupportedException> { useCase("round") }

            assertEquals(AccountOperationUnsupportedException.Kind.VIEW_ONLY, thrown.kind)
            coVerify(exactly = 0) { votingProofPrecomputeRepository.warmProvingCaches() }
        }

    @Test
    fun shareTrackingHasNothingToDo() =
        runTest {
            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository = votingRecoveryRepository,
                    votingCryptoClient = mockk(relaxed = true),
                    votingApiProvider = mockk(relaxed = true),
                    synchronizerProvider = mockk(relaxed = true),
                    getSelectedWalletAccount = getSelectedWalletAccount,
                )

            assertEquals(VotingShareTrackingResult.Completed, useCase("round"))
            coVerify(exactly = 0) { votingRecoveryRepository.get(any(), any()) }
        }

    @Test
    fun theHowToVoteScreenLeavesAtOnce() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)

            VoteHowToVoteVM(
                navigationRouter = navigationRouter,
                hasSeenHowToVote = mockk(relaxed = true),
                hasSeenHowToVoteKeystone = mockk(relaxed = true),
                getSelectedWalletAccount = getSelectedWalletAccount,
            )

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun homeNeverRestoresAPendingVotingRoute() =
        runTest {
            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val hooks =
                VotingHomeHooksImpl(
                    votingRecoveryRepository = votingRecoveryRepository,
                    votingApiRepository = mockk(relaxed = true),
                    votingSessionStore = mockk(relaxed = true),
                    getSelectedWalletAccount = getSelectedWalletAccount,
                    refreshActiveVotingSession = mockk(relaxed = true),
                    votingShareTrackingScheduler = mockk(relaxed = true),
                    navigationRouter = navigationRouter,
                )

            assertFalse(hooks.recoverPendingRouteIfNeeded())
            coVerify(exactly = 0) { votingRecoveryRepository.getRoundIdsWithPendingKeystoneRequest(any()) }
            verify(exactly = 0) { navigationRouter.replaceAll(*anyVararg()) }
        }
}
