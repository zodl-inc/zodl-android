package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository

class ViewTransactionsAfterSuccessfulProposalUseCase(
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val ledgerProposalRepository: LedgerProposalRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val navigationRouter: NavigationRouter,
    private val prefillSend: PrefillSendUseCase,
) {
    operator fun invoke() {
        zashiProposalRepository.clear()
        keystoneProposalRepository.clear()
        ledgerProposalRepository.clear()
        prefillSend.clear()
        navigationRouter.backToRoot()
    }
}
