package co.electriccoin.zcash.ui.common.appbar

import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState.AccountType
import co.electriccoin.zcash.ui.design.component.IconButtonState

data class ZashiMainTopAppBarState(
    val accountSwitchState: AccountSwitchState?,
    val balanceVisibilityButton: IconButtonState,
    val moreButton: IconButtonState
) {
    enum class AccountType { ZASHI, KEYSTONE, LEDGER }
}

data class AccountSwitchState(
    val onAccountTypeClick: (() -> Unit)?,
    val accountType: AccountType,
)
