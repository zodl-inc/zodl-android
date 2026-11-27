package co.electriccoin.zcash.ui.screen.accountlist.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.HWWalletAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.screen.disconnect.DisconnectArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Offers the options of one hardware wallet. Choosing to disconnect first hides the sheet and only
 * then replaces it with the disconnect screen for that account, so the sheet animates away and
 * leaves no hidden destination on the back stack; back from the disconnect screen returns to the
 * Wallets & Hardware screen.
 */
internal class WalletOptionsVM(
    private val args: WalletOptionsArgs,
    getWalletAccounts: GetWalletAccountsUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val isHidden = MutableStateFlow(false)

    private var navigateJob: Job? = null

    init {
        viewModelScope.launch {
            if (getWalletAccounts.require().first().findAccount() == null) navigationRouter.back()
        }
    }

    val state: StateFlow<WalletOptionsState?> =
        combine(getWalletAccounts.observe(), isHidden) { accounts, isHidden ->
            val account = accounts?.findAccount()
            if (account == null || isHidden) {
                null
            } else {
                WalletOptionsState.create(
                    isLedger = account is LedgerAccount,
                    onDisconnectClick = ::onDisconnectClick,
                    onBack = ::onBack,
                )
            }
        }.stateIn(this)

    private fun List<WalletAccount>.findAccount(): HWWalletAccount? =
        firstOrNull { it.sdkAccount.accountUuid.toStorageKeyId() == args.accountKeyId } as? HWWalletAccount

    private fun onDisconnectClick() {
        if (navigateJob?.isActive == true) return
        navigateJob =
            viewModelScope.launch {
                isHidden.update { true }
                delay(350.milliseconds)
                navigationRouter.replace(DisconnectArgs(accountKeyId = args.accountKeyId))
            }
    }

    private fun onBack() {
        if (navigateJob?.isActive == true) return
        navigationRouter.back()
    }
}
