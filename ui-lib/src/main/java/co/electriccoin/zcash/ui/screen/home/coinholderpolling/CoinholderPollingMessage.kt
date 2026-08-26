package co.electriccoin.zcash.ui.screen.home.coinholderpolling

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper

/**
 * Coinholder Polling (CHP) nudge in the wallet-status widget (MOB-1805). No dismiss ("X")
 * affordance by design — it's meant to persist until the user acts; see
 * `HomeMessageData.CoinholderPolling`'s doc for how it disappears on its own instead (voting
 * disabled, round ended, or the account has already voted). Colors are the [HomeMessageWrapper]
 * defaults, which are already the fixed (non-theme-swapping) Purple.500 -> Purple.900 gradient
 * this design calls for — see HomeMessageWrapper.kt.
 */
@Suppress("ModifierNaming")
@Composable
fun CoinholderPollingMessage(
    contentPadding: PaddingValues,
    state: CoinholderPollingMessageState,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        // Figma (node 2223:3884/2223:4274): widget row padding is 14dp/24dp, not the 16dp/18dp
        // default the other home messages use.
        contentHorizontalPadding = 24.dp,
        contentVerticalPadding = 14.dp,
        start = {
            Image(
                painter = painterResource(R.drawable.ic_message_coinholder_polling),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current)
            )
        },
        title = {
            Text(
                stringResource(R.string.coinVote_common_screenTitle)
            )
        },
        subtitle = {
            Text(
                text = stringResource(R.string.home_message_coinholder_polling_subtitle),
            )
        },
        end = {
            ZashiButton(
                modifier = Modifier.height(36.dp),
                state =
                    ButtonState(
                        onClick = state.onButtonClick,
                        text = stringRes(R.string.smartBanner_content_backup_button)
                    )
            )
        }
    )
}

class CoinholderPollingMessageState(
    val onClick: () -> Unit,
    val onButtonClick: () -> Unit,
) : HomeMessageState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            CoinholderPollingMessage(
                state =
                    CoinholderPollingMessageState(
                        onClick = {},
                        onButtonClick = {}
                    ),
                contentPadding = PaddingValues()
            )
        }
    }
