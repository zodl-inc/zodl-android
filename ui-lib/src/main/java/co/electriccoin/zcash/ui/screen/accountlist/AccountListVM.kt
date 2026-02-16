package co.electriccoin.zcash.ui.screen.accountlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.ExternalUrl
import co.electriccoin.zcash.ui.screen.addressbook.ADDRESS_MAX_LENGTH
import co.electriccoin.zcash.ui.screen.connectkeystone.ConnectKeystoneArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.map

class AccountListVM(
    getWalletAccounts: GetWalletAccountsUseCase,
    private val selectWalletAccount: SelectWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    @Suppress("SpreadOperator")
    val state =
        getWalletAccounts
            .observe()
            .map { accounts ->
                val items =
                    listOfNotNull(
                        *accounts
                            .orEmpty()
                            .map<WalletAccount, AccountListItem> { account ->
                                AccountListItem.Account(
                                    ZashiAccountListItemState(
                                        title = account.name,
                                        subtitle =
                                            stringRes(
                                                "${account.unified.address.address.take(ADDRESS_MAX_LENGTH)}...",
                                            ),
                                        icon =
                                            when (account) {
                                                is KeystoneAccount -> R.drawable.ic_item_keystone
                                                is ZashiAccount -> R.drawable.ic_item_zashi
                                            },
                                        isSelected = account.isSelected,
                                        onClick = { onAccountClicked(account) }
                                    )
                                )
                            }.toTypedArray(),
                        AccountListItem
                            .Other(
                                ListItemState(
                                    title =
                                        stringRes(
                                            co.electriccoin.zcash.ui.R.string.account_list_keystone_promo_title,
                                        ),
                                    subtitle =
                                        stringRes(
                                            co.electriccoin.zcash.ui.R.string.account_list_keystone_promo_subtitle,
                                        ),
                                    onClick = ::onShowKeystonePromoClicked
                                )
                            ).takeIf {
                                accounts.orEmpty().none { it is KeystoneAccount }
                            }
                    )

                AccountListState(
                    items = items,
                    isLoading = accounts == null,
                    onBack = ::onBack,
                    addWalletButton =
                        ButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.R.string.account_list_keystone_primary),
                            onClick = ::onAddWalletButtonClicked
                        ).takeIf {
                            accounts.orEmpty().none { it is KeystoneAccount }
                        }
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Companion.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private fun onShowKeystonePromoClicked() =
        navigationRouter.replace(ExternalUrl("https://keyst.one/shop/products/keystone-3-pro?discount=Zashi"))

    private fun onAccountClicked(account: WalletAccount) =
        viewModelScope.launch {
            selectWalletAccount(account)
        }

    private fun onAddWalletButtonClicked() = navigationRouter.forward(ConnectKeystoneArgs)

    private fun onBack() = navigationRouter.back()
}
