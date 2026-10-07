package co.electriccoin.zcash.ui.screen.home.orswap

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper

@Suppress("ModifierNaming")
@Composable
fun ORSwapMessage(
    state: ORSwapMessageState,
    contentPadding: PaddingValues,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        start = {
            Image(
                painter = painterResource(R.drawable.ic_message_orswap),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current)
            )
        },
        title = {
            Text(
                "Pending Swap to ZEC"
            )
        },
        subtitle = {
            Text(
                text = state.subtitle.getValue()
            )
        },
        end = {
            ZashiButton(
                minHeight = 36.dp,
                state =
                    ButtonState(
                        onClick = state.onButtonClick,
                        text = stringRes("Check")
                    )
            )
        }
    )
}

data class ORSwapMessageState(
    val subtitle: StringResource,
    val onClick: () -> Unit,
    val onButtonClick: () -> Unit,
) : HomeMessageState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ORSwapMessage(
                state =
                    ORSwapMessageState(
                        onClick = {},
                        onButtonClick = {},
                        subtitle = stringRes(Zatoshi(0))
                    ),
                contentPadding = PaddingValues()
            )
        }
    }
