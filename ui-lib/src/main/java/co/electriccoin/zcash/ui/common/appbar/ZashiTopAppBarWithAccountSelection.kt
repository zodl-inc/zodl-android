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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState.AccountType
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.ShimmerableImage
import co.electriccoin.zcash.ui.design.component.ShimmerableText
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.rememberZashiShimmer
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import com.valentinilk.shimmer.shimmer

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
                ZashiIconButton(
                    state.moreButton,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .testTag(ZashiTopAppBarWithAccountSelectionTag.MORE)
                )
                Spacer(Modifier.width(20.dp))
            },
            navigationAction = {
                AccountSwitch(state.accountSwitchState)
            },
        )
    }
}

@Composable
private fun AccountSwitch(state: AccountSwitchState?) {
    val onAccountTypeClick = state?.onAccountTypeClick
    val clickModifier =
        if (onAccountTypeClick != null) {
            Modifier.clickable(onClick = onAccountTypeClick)
        } else {
            Modifier
        }

    val painter =
        when (state?.accountType) {
            AccountType.ZASHI -> painterResource(R.drawable.ic_item_zashi)
            AccountType.KEYSTONE -> painterResource(R.drawable.ic_item_keystone)
            AccountType.LEDGER -> painterResource(R.drawable.ic_item_ledger)
            null -> null
        }
    val text =
        when (state?.accountType) {
            AccountType.ZASHI -> stringResource(co.electriccoin.zcash.ui.R.string.accounts_zashi)
            AccountType.KEYSTONE -> stringResource(co.electriccoin.zcash.ui.R.string.accounts_keystone)
            AccountType.LEDGER -> stringResource(co.electriccoin.zcash.ui.R.string.accounts_ledger)
            null -> null
        }

    Row(
        modifier =
            Modifier
                .defaultMinSize(40.dp, 40.dp)
                .then(if (state == null) Modifier.shimmer(rememberZashiShimmer()) else Modifier)
                .padding(start = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                then clickModifier then Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerableImage(
            modifier = Modifier.size(32.dp),
            painter = painter
        )
        Spacer(8.dp)
        ShimmerableText(
            text = text,
            shimmerText = stringResource(co.electriccoin.zcash.ui.R.string.accounts_zashi),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (onAccountTypeClick != null) {
            Spacer(Modifier.width(8.dp))
            Image(
                painter = painterResource(R.drawable.ic_app_bar_arrow_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Btns.Ghost.btnGhostFg)
            )
        }
    }
}

object ZashiTopAppBarWithAccountSelectionTag {
    const val MORE = "HOME_MORE"
}

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
