package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository

class CancelSwapQuoteUseCase(
    private val swapRepository: SwapRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val ledgerProposalRepository: LedgerProposalRepository,
    private val navigationRouter: NavigationRouter,
) {
    operator fun invoke() {
        zashiProposalRepository.clear()
        keystoneProposalRepository.clear()
        ledgerProposalRepository.clear()
        swapRepository.clearQuote()
        navigationRouter.back()
    }
}
