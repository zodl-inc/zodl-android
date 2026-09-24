package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.ShieldTransactionProposal
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository

/**
 * Leaves the sign sheet for the confirm surface beneath it, which keeps its proposal. A shield
 * proposal has no confirm surface holding it, so it is dropped with the session.
 */
class CancelLedgerSigningUseCase(
    private val ledgerProposalRepository: LedgerProposalRepository,
    private val navigationRouter: NavigationRouter,
) {
    operator fun invoke() {
        if (ledgerProposalRepository.transactionProposal.value is ShieldTransactionProposal) {
            ledgerProposalRepository.clear()
        } else {
            ledgerProposalRepository.cancelSigning()
        }
        navigationRouter.back()
    }
}
