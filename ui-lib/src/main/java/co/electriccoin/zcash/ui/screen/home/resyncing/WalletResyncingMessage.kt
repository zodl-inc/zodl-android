package co.electriccoin.zcash.ui.screen.home.resyncing

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.LocalZashiCircularProgressIndicatorColors
import co.electriccoin.zcash.ui.design.component.ZashiCircularProgressIndicatorDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper

@Suppress("ModifierNaming")
@Composable
fun WalletResyncingMessage(
    contentPadding: PaddingValues,
    state: WalletResyncingMessageState,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        start = {
            val colors =
                LocalZashiCircularProgressIndicatorColors.current
                    ?: ZashiCircularProgressIndicatorDefaults.colors()
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.progressColor,
                trackColor = colors.trackColor,
                strokeWidth = 3.dp,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.home_message_resyncing_title),
            )
        },
        subtitle = {
            Text(
                text = stringResource(R.string.home_message_resyncing_subtitle),
            )
        },
        end = null
    )
}

class WalletResyncingMessageState(
    val onClick: () -> Unit,
) : HomeMessageState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            WalletResyncingMessage(
                contentPadding = PaddingValues(16.dp),
                state =
                    WalletResyncingMessageState(
                        onClick = {}
                    )
            )
        }
    }
