package co.electriccoin.zcash.ui.fixture

import co.electriccoin.zcash.ui.common.appbar.AccountSwitchState
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes

object ZashiMainTopAppBarStateFixture {
    fun new(
        accountSwitchState: AccountSwitchState =
            AccountSwitchState(
                accountType = ZashiMainTopAppBarState.AccountType.ZASHI,
                onAccountTypeClick = {}
            ),
        balanceVisibilityButton: IconButtonState =
            IconButtonState(
                icon = R.drawable.ic_app_bar_balances_hide,
                contentDescription = stringRes(co.electriccoin.zcash.ui.R.string.hide_balances_content_description)
            ) {},
        settingsButton: IconButtonState =
            IconButtonState(
                icon = R.drawable.ic_app_bar_settings,
                contentDescription = stringRes(co.electriccoin.zcash.ui.R.string.scan_openSettings)
            ) {},
    ) = ZashiMainTopAppBarState(
        accountSwitchState = accountSwitchState,
        balanceVisibilityButton = balanceVisibilityButton,
        moreButton = settingsButton
    )
}
