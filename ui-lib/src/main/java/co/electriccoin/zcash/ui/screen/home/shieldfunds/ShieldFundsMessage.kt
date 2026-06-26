package co.electriccoin.zcash.ui.screen.home.shieldfunds

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
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER

@Suppress("ModifierNaming")
@Composable
fun ShieldFundsMessage(
    contentPadding: PaddingValues,
    state: ShieldFundsMessageState,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        start = {
            Image(
                painter = painterResource(R.drawable.ic_message_transparent_balance),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current)
            )
        },
        title = {
            Text(
                stringResource(R.string.smartBanner_content_shield_title),
            )
        },
        subtitle = {
            Text(
                text = state.subtitle.getValue(),
            )
        },
        end = {
            ZashiButton(
                modifier = Modifier.height(36.dp),
                state =
                    ButtonState(
                        onClick = state.onButtonClick,
                        text = stringRes(stringResource(R.string.smartBanner_content_shield_button))
                    )
            )
        }
    )
}

class ShieldFundsMessageState(
    val subtitle: StringResource,
    val onClick: (() -> Unit)?,
    val onButtonClick: () -> Unit,
) : HomeMessageState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ShieldFundsMessage(
                state =
                    ShieldFundsMessageState(
                        subtitle =
                            stringRes(
                                R.string.home_message_transparent_balance_subtitle,
                                stringRes(Zatoshi(10000), HIDDEN),
                                CURRENCY_TICKER
                            ),
                        onClick = {},
                        onButtonClick = {}
                    ),
                contentPadding = PaddingValues()
            )
        }
    }
