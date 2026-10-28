package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase

/**
 * Guards the entry points into flows whose only outcome is spending from the selected account.
 */
class RejectIfSelectedAccountCannotSpendUseCase(
    private val accountDataSource: AccountDataSource,
    private val navigateToError: NavigateToErrorUseCase,
) {
    /**
     * Shows the view-only unsupported-operation error, through [navigate], when the selected account
     * cannot spend, and returns true; returns false otherwise. Waits for the selected account when
     * the accounts have not loaded yet, so a cold start never opens a spend flow for a view-only
     * account.
     */
    suspend operator fun invoke(navigate: NavigationRouter.(Any) -> Unit = { forward(it) }): Boolean {
        if (accountDataSource.getSelectedAccount().isSpendable) return false
        navigateToError(
            ErrorArgs.General(AccountOperationUnsupportedException.viewOnly()),
            navigate
        )
        return true
    }
}
