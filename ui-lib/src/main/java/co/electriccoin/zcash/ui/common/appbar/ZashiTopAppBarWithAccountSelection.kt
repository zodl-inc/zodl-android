package co.electriccoin.zcash.ui.common.appbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState.AccountType
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

@Composable
fun ZashiTopAppBarWithAccountSelection(
    state: ZashiMainTopAppBarState?,
    showHideBalances: Boolean = true
) {
    if (state == null) return

    Box {
        ZashiSmallTopAppBar(
            windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
            hamburgerMenuActions = {
                if (showHideBalances) {
                    ZashiIconButton(state.balanceVisibilityButton, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(4.dp))
                }
                ZashiIconButton(state.moreButton, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(20.dp))
            },
            navigationAction = {
                AccountSwitch(state.accountSwitchState)
            },
        )
    }
}

@Composable
private fun AccountSwitch(state: AccountSwitchState) {
    val clickModifier =
        if (state.onAccountTypeClick != null) {
            Modifier.clickable(onClick = state.onAccountTypeClick)
        } else {
            Modifier
        }

    Row(
        modifier =
            Modifier
                .defaultMinSize(40.dp, 40.dp)
                .padding(start = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                then clickModifier then Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier.size(32.dp),
            painter =
                painterResource(
                    when (state.accountType) {
                        AccountType.ZASHI -> R.drawable.ic_item_zashi
                        AccountType.KEYSTONE -> R.drawable.ic_item_keystone
                    }
                ),
            contentDescription = null
        )
        Spacer(8.dp)
        Text(
            when (state.accountType) {
                AccountType.ZASHI -> stringResource(co.electriccoin.zcash.ui.R.string.zashi_wallet_name)
                AccountType.KEYSTONE -> stringResource(co.electriccoin.zcash.ui.R.string.keystone_wallet_name)
            },
            style =
                TextStyle(
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.W600,
                    color = ZashiColors.Text.textPrimary,
                    textAlign = TextAlign.Center,
                )
        )
        if (state.onAccountTypeClick != null) {
            Spacer(Modifier.width(8.dp))
            Image(
                painter = painterResource(R.drawable.ic_app_bar_arrow_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Btns.Ghost.btnGhostFg)
            )
        }
    }
}

@Immutable
data class ZashiMainTopAppBarState(
    val accountSwitchState: AccountSwitchState,
    val balanceVisibilityButton: IconButtonState,
    val moreButton: IconButtonState
) {
    enum class AccountType { ZASHI, KEYSTONE }
}

@Immutable
data class AccountSwitchState(
    val onAccountTypeClick: (() -> Unit)?,
    val accountType: AccountType,
)

@PreviewScreens
@Composable
private fun ZashiMainTopAppBarPreview() =
    ZcashTheme {
        ZashiTopAppBarWithAccountSelection(
            state =
                ZashiMainTopAppBarState(
                    accountSwitchState =
                        AccountSwitchState(
                            accountType = AccountType.ZASHI,
                            onAccountTypeClick = {}
                        ),
                    balanceVisibilityButton = IconButtonState(R.drawable.ic_app_bar_balances_hide) {},
                    moreButton = IconButtonState(R.drawable.ic_app_bar_settings) {}
                )
        )
    }

@PreviewScreens
@Composable
private fun KeystoneMainTopAppBarPreview() =
    ZcashTheme {
        ZashiTopAppBarWithAccountSelection(
            state =
                ZashiMainTopAppBarState(
                    accountSwitchState =
                        AccountSwitchState(
                            accountType = AccountType.KEYSTONE,
                            onAccountTypeClick = {},
                        ),
                    balanceVisibilityButton = IconButtonState(R.drawable.ic_app_bar_balances_hide) {},
                    moreButton = IconButtonState(R.drawable.ic_app_bar_settings) {}
                )
        )
    }

@PreviewScreens
@Composable
private fun MainTopAppBarWithSubtitlePreview() =
    ZcashTheme {
        ZashiTopAppBarWithAccountSelection(
            state =
                ZashiMainTopAppBarState(
                    accountSwitchState =
                        AccountSwitchState(
                            accountType = AccountType.KEYSTONE,
                            onAccountTypeClick = {},
                        ),
                    balanceVisibilityButton = IconButtonState(R.drawable.ic_app_bar_balances_hide) {},
                    moreButton = IconButtonState(R.drawable.ic_app_bar_settings) {}
                )
        )
    }
