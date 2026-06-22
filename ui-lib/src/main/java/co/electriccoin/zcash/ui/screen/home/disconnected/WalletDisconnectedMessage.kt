package co.electriccoin.zcash.ui.screen.home.disconnected

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
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
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper

@Suppress("ModifierNaming")
@Composable
fun WalletDisconnectedMessage(
    contentPadding: PaddingValues,
    state: WalletDisconnectedMessageState,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        start = {
            Image(
                painter = painterResource(R.drawable.ic_message_disconnected),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current)
            )
        },
        title = {
            Text(
                stringResource(R.string.home_message_wallet_disconnected_title),
            )
        },
        subtitle = {
            Text(
                text = stringResource(R.string.smartBanner_content_disconnected_info),
            )
        },
        end = null
    )
}

class WalletDisconnectedMessageState(
    val onClick: () -> Unit
) : HomeMessageState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            WalletDisconnectedMessage(
                contentPadding = PaddingValues(16.dp),
                state =
                    WalletDisconnectedMessageState(
                        onClick = {}
                    )
            )
        }
    }
