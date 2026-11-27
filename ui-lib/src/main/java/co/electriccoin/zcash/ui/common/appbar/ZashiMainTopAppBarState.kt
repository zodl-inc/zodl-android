package co.electriccoin.zcash.ui.common.appbar

import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes

data class ZashiMainTopAppBarState(
    val accountSwitchState: AccountSwitchState?,
    val balanceVisibilityButton: IconButtonState,
    val moreButton: IconButtonState
) {
    companion object {
        val preview: ZashiMainTopAppBarState =
            preview(
                icon = R.drawable.ic_item_zashi,
                name = stringRes(co.electriccoin.zcash.ui.R.string.accounts_zashi),
            )

        val previewKeystone: ZashiMainTopAppBarState =
            preview(
                icon = R.drawable.ic_item_keystone,
                name = stringRes(co.electriccoin.zcash.ui.R.string.accounts_keystone),
            )

        val previewLedger: ZashiMainTopAppBarState =
            preview(
                icon = R.drawable.ic_item_ledger,
                name = stringRes(co.electriccoin.zcash.ui.R.string.accounts_ledger),
            )

        val previewWithSubtitle: ZashiMainTopAppBarState =
            preview(
                icon = R.drawable.ic_item_zashi,
                name = stringRes("Account"),
                subtitle = stringRes("Subtitle"),
            )

        val previewLoading: ZashiMainTopAppBarState = preview.copy(accountSwitchState = null)

        private fun preview(
            icon: Int,
            name: StringResource,
            subtitle: StringResource? = null,
        ) = ZashiMainTopAppBarState(
            accountSwitchState =
                AccountSwitchState(
                    icon = imageRes(icon),
                    name = name,
                    subtitle = subtitle,
                    onAccountTypeClick = {}
                ),
            balanceVisibilityButton = IconButtonState(R.drawable.ic_app_bar_balances_hide) {},
            moreButton = IconButtonState(R.drawable.ic_app_bar_settings) {}
        )
    }
}

/**
 * The selected account as the top app bar shows it.
 *
 * @param icon The account's avatar.
 * @param name The account's name.
 * @param subtitle A small label above [name], or null for an account that needs none.
 */
data class AccountSwitchState(
    val icon: ImageResource,
    val name: StringResource,
    val subtitle: StringResource?,
    val onAccountTypeClick: (() -> Unit)?,
)
