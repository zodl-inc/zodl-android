package co.electriccoin.zcash.ui.screen.home.ohttpoptin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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

/** No close button by design: it stays until the user opts out on the opt-in screen or turns Private on. */
@Suppress("ModifierNaming")
@Composable
fun OhttpOptInMessage(
    contentPadding: PaddingValues,
    state: OhttpOptInMessageState,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        start = {
            Image(
                painter = painterResource(R.drawable.ic_ohttp_incognito),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current)
            )
        },
        title = { Text(state.title.getValue()) },
        subtitle = { Text(state.subtitle.getValue()) },
        end = {
            ZashiButton(
                minHeight = 36.dp,
                state =
                    ButtonState(
                        onClick = state.onButtonClick,
                        text = state.buttonText
                    )
            )
        }
    )
}

data class OhttpOptInMessageState(
    val title: StringResource,
    val subtitle: StringResource,
    val buttonText: StringResource,
    val onClick: () -> Unit,
    val onButtonClick: () -> Unit,
) : HomeMessageState {
    companion object {
        val preview =
            OhttpOptInMessageState(
                title = stringRes(R.string.ohttpOptIn_widgetTitle),
                subtitle = stringRes(R.string.ohttpOptIn_widgetSubtitle),
                buttonText = stringRes(R.string.ohttpOptIn_widgetView),
                onClick = {},
                onButtonClick = {},
            )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            OhttpOptInMessage(
                state = OhttpOptInMessageState.preview,
                contentPadding = PaddingValues()
            )
        }
    }
