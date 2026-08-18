package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.ExactInputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ExactOutputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.MigrationSweepTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ShieldTransactionProposal
import co.electriccoin.zcash.ui.common.migration.MigrationNavigator
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.pay.PayArgs
import co.electriccoin.zcash.ui.screen.send.Send
import co.electriccoin.zcash.ui.screen.swap.SwapArgs

class CancelProposalFlowUseCase(
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val navigationRouter: NavigationRouter,
    private val observeClearSend: ObserveClearSendUseCase,
    private val accountDataSource: AccountDataSource,
    private val swapRepository: SwapRepository,
    private val migrationNavigator: MigrationNavigator,
) {
    suspend operator fun invoke(clearSendForm: Boolean = true) {
        val proposal =
            when (accountDataSource.getSelectedAccount()) {
                is ZashiAccount -> zashiProposalRepository.getTransactionProposal()
                is KeystoneAccount -> keystoneProposalRepository.getTransactionProposal()
            }

        zashiProposalRepository.clear()
        keystoneProposalRepository.clear()

        when (proposal) {
            is ExactInputSwapTransactionProposal -> {
                swapRepository.clearQuote()
                navigationRouter.backTo(SwapArgs::class)
            }

            is ExactOutputSwapTransactionProposal -> {
                swapRepository.clearQuote()
                navigationRouter.backTo(PayArgs::class)
            }

            is MigrationSweepTransactionProposal -> {
                // Reached via MigrationReviewVM's IMMEDIATE-mode Keystone branch, never via the
                // ordinary Send flow — Send was never on this back stack, so falling through to
                // the `else` branch's `backTo(Send::class)` would silently no-op (no matching
                // destination to pop to), leaving the user stuck on the Sign/reject sheet.
                migrationNavigator.backToMigrationReview()
            }

            is ShieldTransactionProposal -> {
                // Reached via ShieldFundsUseCase's Keystone branch, which (unlike the Zashi
                // branch) never pops/replaces the screen the shield prompt was shown on before
                // pushing the Sign screen. So that origin screen (ShieldFundsInfo dialog,
                // Balances, or Home) is still directly beneath us on the back stack — falling
                // through to the `else` branch's `backTo(Send::class)` would silently no-op
                // (Send was never pushed for this flow), leaving the user stuck on the
                // Sign/reject sheet. A plain back() pops the Sign screen and reveals it again.
                navigationRouter.back()
            }

            else -> {
                if (clearSendForm) observeClearSend.requestClear()
                navigationRouter.backTo(Send::class)
            }
        }
    }
}
