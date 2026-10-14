package co.electriccoin.zcash.ui.common.usecase

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SubmitLedgerProposalUseCase(
    private val ledgerProposalRepository: LedgerProposalRepository,
    private val navigationRouter: NavigationRouter,
    private val swapRepository: SwapRepository,
    private val processSwapTransaction: ProcessSwapTransactionUseCase,
    private val prefillSend: PrefillSendUseCase,
) {
    @set:RestrictTo(RestrictTo.Scope.TESTS)
    @VisibleForTesting
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Submits the proposal the device has signed and replaces the whole back stack with the
     * Transaction Progress screen. An empty repository leaves nothing to submit, so the session
     * fails instead of staying on its signed state with Cancel disabled.
     */
    suspend operator fun invoke() {
        val proposal = ledgerProposalRepository.transactionProposal.value
        if (proposal == null) {
            Twig.warn { "Ledger signing: signed without a proposal to submit" }
            ledgerProposalRepository.failSignedSessionWithoutProposal()
            return
        }
        swapRepository.clear()
        submitLedgerProposal(proposal)
        navigationRouter.replaceAll(TransactionProgressArgs)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun submitLedgerProposal(proposal: TransactionProposal) {
        scope.launch {
            try {
                val result = ledgerProposalRepository.submit()
                if (proposal is SwapTransactionProposal) {
                    processSwapTransaction(proposal, result)
                }
            } catch (e: Exception) {
                Twig.warn { "Ledger proposal submission failed: ${e.javaClass.simpleName}" }
            } finally {
                prefillSend.clear()
            }
        }
    }
}
