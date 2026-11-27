package co.electriccoin.zcash.ui.screen.viewingkey.addaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.canConnectHardwareWallet
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.screen.accountlist.options.WalletOptionsState
import co.electriccoin.zcash.ui.screen.choosehwwallet.ChooseHWWalletArgs
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Offers the ways to add an account. Connecting a hardware wallet is offered while the wallet holds
 * no account of some vendor, the same rule the vendor picker lists the vendors by. Picking an option
 * first hides the sheet and only then replaces it with the chosen flow, so the sheet animates away
 * instead of vanishing under the next screen and leaves no hidden destination behind on the back
 * stack.
 */
internal class AddVKAccountVM(
    getWalletAccounts: GetWalletAccountsUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val isHidden = MutableStateFlow(false)

    private var navigateJob: Job? = null

    val state: StateFlow<WalletOptionsState?> =
        combine(getWalletAccounts.observe(), isHidden) { accounts, isHidden ->
            if (accounts == null || isHidden) {
                null
            } else {
                WalletOptionsState.addAccount(
                    canConnectHardwareWallet = accounts.canConnectHardwareWallet(),
                    onConnectHardwareWalletClick = { hideThenReplaceWith(ChooseHWWalletArgs) },
                    onImportClick = { hideThenReplaceWith(ImportVKArgs) },
                    onBack = ::onBack,
                )
            }
        }.stateIn(this)

    private fun hideThenReplaceWith(route: Any) {
        if (navigateJob?.isActive == true) return
        navigateJob =
            viewModelScope.launch {
                isHidden.update { true }
                delay(350.milliseconds)
                navigationRouter.replace(route)
            }
    }

    private fun onBack() {
        if (navigateJob?.isActive == true) return
        navigationRouter.back()
    }
}
