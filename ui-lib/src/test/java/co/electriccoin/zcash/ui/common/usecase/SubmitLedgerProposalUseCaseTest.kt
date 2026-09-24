package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import co.electriccoin.zcash.ui.common.repository.SubmitProposalState
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * [SubmitLedgerProposalUseCase] submits the proposal the device has signed, off-thread, and replaces
 * the whole back stack with Transaction Progress in one command. Swap proposals additionally run
 * [ProcessSwapTransactionUseCase], and the send prefill is always cleared once submission settles,
 * success or failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubmitLedgerProposalUseCaseTest {
    @Test
    fun submitsClearsSwapStateAndNavigatesToProgress() =
        runTest {
            val fx = useCase()
            fx.givenProposal(mockk<TransactionProposal>())

            fx.useCase()

            coVerify(exactly = 1) { fx.ledgerProposalRepository.submit() }
            verify(exactly = 1) { fx.swapRepository.clear() }
            verify(exactly = 1) { fx.navigationRouter.replaceAll(TransactionProgressArgs) }
        }

    @Test
    fun aSecondCallIsIgnoredWhileASubmissionIsAlreadyRunning() =
        runTest {
            val fx = useCase()
            fx.givenProposal(mockk<TransactionProposal>())
            every { fx.ledgerProposalRepository.submitState } returns
                MutableStateFlow(SubmitProposalState.Submitting)

            fx.useCase()

            coVerify(exactly = 0) { fx.ledgerProposalRepository.getTransactionProposal() }
            coVerify(exactly = 0) { fx.ledgerProposalRepository.submit() }
            verify(exactly = 0) { fx.navigationRouter.replaceAll(*anyVararg()) }
        }

    @Test
    fun swapProposalsRunPostProcessing() =
        runTest {
            val fx = useCase()
            val proposal = mockk<SwapTransactionProposal>(relaxed = true)
            val result = mockk<SubmitResult>(relaxed = true)
            fx.givenProposal(proposal, result)

            fx.useCase()

            coVerify(exactly = 1) { fx.processSwapTransaction(proposal, result) }
        }

    @Test
    fun nonSwapProposalsSkipPostProcessing() =
        runTest {
            val fx = useCase()
            fx.givenProposal(mockk<TransactionProposal>())

            fx.useCase()

            coVerify(exactly = 0) { fx.processSwapTransaction(any(), any()) }
        }

    @Test
    fun theSendPrefillIsClearedEvenWhenSubmitFails() =
        runTest {
            val fx = useCase()
            fx.givenProposal(mockk<TransactionProposal>())
            coEvery { fx.ledgerProposalRepository.submit() } throws RuntimeException("boom")

            fx.useCase()

            verify(exactly = 1) { fx.prefillSend.clear() }
            verify(exactly = 1) { fx.navigationRouter.replaceAll(TransactionProgressArgs) }
        }

    private fun Fixtures.givenProposal(
        proposal: TransactionProposal,
        submitResult: SubmitResult = mockk(relaxed = true),
    ) {
        coEvery { ledgerProposalRepository.getTransactionProposal() } returns proposal
        coEvery { ledgerProposalRepository.submit() } returns submitResult
    }

    /** Pins the use case's off-thread submit scope to the test scheduler so it runs eagerly. */
    private fun TestScope.useCase(): Fixtures {
        val fx = Fixtures()
        fx.useCase.scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return fx
    }

    private class Fixtures {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)
        val ledgerProposalRepository =
            mockk<LedgerProposalRepository>(relaxed = true) {
                every { submitState } returns MutableStateFlow<SubmitProposalState?>(null)
            }
        val swapRepository = mockk<SwapRepository>(relaxed = true)
        val processSwapTransaction = mockk<ProcessSwapTransactionUseCase>(relaxed = true)
        val prefillSend = mockk<PrefillSendUseCase>(relaxed = true)
        val useCase =
            SubmitLedgerProposalUseCase(
                ledgerProposalRepository = ledgerProposalRepository,
                navigationRouter = navigationRouter,
                swapRepository = swapRepository,
                processSwapTransaction = processSwapTransaction,
                prefillSend = prefillSend,
            )
    }
}
