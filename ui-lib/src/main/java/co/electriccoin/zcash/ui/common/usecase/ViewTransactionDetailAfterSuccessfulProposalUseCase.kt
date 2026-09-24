package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionDetailArgs

class ViewTransactionDetailAfterSuccessfulProposalUseCase(
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val ledgerProposalRepository: LedgerProposalRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val navigationRouter: NavigationRouter,
) {
    operator fun invoke(txId: String) {
        zashiProposalRepository.clear()
        keystoneProposalRepository.clear()
        ledgerProposalRepository.clear()
        navigationRouter.replaceAll(TransactionDetailArgs(txId))
    }
}
