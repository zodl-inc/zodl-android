package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount

class SelectWalletAccountUseCase(
    private val accountDataSource: AccountDataSource,
    private val navigationRouter: NavigationRouter
) {
    /**
     * @param navigateBack Whether selecting also pops the screen that offered the choice, which is
     *        what the account picker wants. A caller that navigates somewhere else afterwards
     *        passes false, so one tap issues one navigation command.
     */
    suspend operator fun invoke(
        account: WalletAccount,
        navigateBack: Boolean = true
    ) {
        accountDataSource.selectAccount(account)
        if (navigateBack) {
            navigationRouter.back()
        }
    }
}
