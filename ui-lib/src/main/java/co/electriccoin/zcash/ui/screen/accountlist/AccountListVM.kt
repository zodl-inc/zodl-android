package co.electriccoin.zcash.ui.screen.accountlist

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveAccountIconUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavigator
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.screen.ExternalUrl
import co.electriccoin.zcash.ui.screen.accountlist.options.WalletOptionsArgs
import co.electriccoin.zcash.ui.screen.editaccount.EditAccountArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AccountListVM(
    private val getWalletAccounts: GetWalletAccountsUseCase,
    private val selectWalletAccount: SelectWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val viewOnlyNavigator: ViewOnlyNavigator,
    private val viewOnlyContent: ViewOnlyContent,
    private val observeAccountIcon: ObserveAccountIconUseCase,
) : ViewModel() {
    /**
     * One row callback per account, kept across emissions so an unchanged row's state stays equal
     * and the row skips recomposition on every balance update. A callback carries only the account's
     * UUID and resolves the account itself when it runs. Each emission drops the callbacks of
     * accounts that are gone. Touched on the main thread only.
     */
    private val onAccountClicks = mutableMapOf<AccountUuid, () -> Unit>()
    private val onOptionsClicks = mutableMapOf<AccountUuid, () -> Unit>()

    val state: StateFlow<AccountListState> =
        combine(getWalletAccounts.observe(), observeAccountIcon.observeAll(), ::createState)
            .stateIn(viewModel = this, initialValue = createState(null, emptyMap()))

    private fun createState(
        accounts: List<WalletAccount>?,
        colors: Map<AccountUuid, Color>,
    ): AccountListState {
        accounts?.let(::forgetRemovedAccounts)
        return AccountListState.create(
            entries = accounts?.map { toEntry(it, observeAccountIcon.iconOf(colors, it)) },
            viewOnlySection = viewOnlyContent.accountListSection,
            viewOnlySectionPlural = viewOnlyContent.accountListSectionPlural,
            viewOnlyLabel = viewOnlyContent.topBarSubtitle,
            addAccountText = viewOnlyContent.addAccountButton,
            addAccountIcon = viewOnlyContent.addAccountButtonIcon,
            onKeystonePromoClick = ::onKeystonePromoClick,
            onAddAccountClick = ::onAddAccountClick,
            onBack = ::onBack,
        )
    }

    private fun forgetRemovedAccounts(accounts: List<WalletAccount>) {
        val current = accounts.mapTo(HashSet()) { it.sdkAccount.accountUuid }
        onAccountClicks.keys.retainAll(current)
        onOptionsClicks.keys.retainAll(current)
    }

    private fun toEntry(
        account: WalletAccount,
        icon: ImageResource,
    ) =
        AccountListEntry(
            kind =
                when (account) {
                    is ZodlAccount -> AccountListKind.ZODL
                    is KeystoneAccount -> AccountListKind.KEYSTONE
                    is LedgerAccount -> AccountListKind.LEDGER
                    is ViewOnlyAccount -> AccountListKind.VIEW_ONLY
                },
            icon = icon,
            name = account.name,
            balance = account.totalBalance,
            isSelected = account.isSelected,
            onClick =
                onAccountClicks.getOrPut(account.sdkAccount.accountUuid) {
                    onAccountClick(account.sdkAccount.accountUuid)
                },
            onOptionsClick =
                when (account) {
                    is ZodlAccount -> {
                        null
                    }

                    is KeystoneAccount, is LedgerAccount -> {
                        onOptionsClicks.getOrPut(account.sdkAccount.accountUuid) {
                            onHardwareWalletOptionsClick(account.sdkAccount.accountUuid)
                        }
                    }

                    is ViewOnlyAccount -> {
                        onOptionsClicks.getOrPut(account.sdkAccount.accountUuid) {
                            onEditClick(account.sdkAccount.accountUuid)
                        }
                    }
                },
        )

    /**
     * Selects the account and closes the screen. The account is looked up when the row is tapped,
     * so the selection always uses its current state.
     */
    private fun onAccountClick(accountUuid: AccountUuid): () -> Unit =
        {
            viewModelScope.launch {
                getWalletAccounts
                    .observe()
                    .value
                    ?.firstOrNull { it.sdkAccount.accountUuid == accountUuid }
                    ?.let { selectWalletAccount(it) }
            }
        }

    private fun onHardwareWalletOptionsClick(accountUuid: AccountUuid): () -> Unit =
        { navigationRouter.forward(WalletOptionsArgs(accountKeyId = accountUuid.toStorageKeyId())) }

    /** Opens Edit Account, which changes a view-only account's colour and name. */
    private fun onEditClick(accountUuid: AccountUuid): () -> Unit =
        { navigationRouter.forward(EditAccountArgs(accountKeyId = accountUuid.toStorageKeyId())) }

    /**
     * Opens the Keystone shop in the in-app browser on top of this screen, so closing the browser
     * returns to the wallet list.
     */
    private fun onKeystonePromoClick() = navigationRouter.forward(ExternalUrl(KEYSTONE_PROMO_URL))

    /**
     * Opens the "Add Account" options, which offer a hardware wallet only while a vendor is still
     * unconnected and always offer importing a viewing key.
     */
    private fun onAddAccountClick() = viewOnlyNavigator.forwardToAddAccount()

    private fun onBack() = navigationRouter.back()

    companion object {
        const val KEYSTONE_PROMO_URL = "https://keyst.one/shop/products/keystone-3-pro?discount=Zodl"
    }
}
