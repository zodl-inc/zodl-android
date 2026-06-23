package co.electriccoin.zcash.ui.screen.exchangerate.widget

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import cash.z.ecc.android.sdk.model.FiatCurrencyConversion
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlin.time.Clock

@Suppress("LongParameterList", "ComplexCondition")
@Composable
fun StyledExchangeLabel(
    zatoshi: Zatoshi,
    state: ExchangeRateState,
    modifier: Modifier = Modifier,
    isHideBalances: Boolean = false,
    hiddenBalancePlaceholder: StringResource =
        stringRes(co.electriccoin.zcash.ui.design.R.string.general_hideBalancesMost),
    style: TextStyle = ZcashTheme.typography.secondary.headlineSmall,
    textColor: Color = ZcashTheme.colors.textFieldHint,
) {
    when (state) {
        is ExchangeRateState.Data -> {
            if (!state.isStale && state.currencyConversion != null) {
                Text(
                    modifier = modifier,
                    text = createExchangeRateText(state, hiddenBalancePlaceholder, zatoshi, isHideBalances),
                    maxLines = 1,
                    color = textColor,
                    style = style,
                )
            }
        }

        is ExchangeRateState.OptIn -> {
            // do not show anything
        }

        ExchangeRateState.OptedOut -> {
            // do not show anything
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun DefaultPreview() =
    ZcashTheme {
        BlankSurface {
            StyledExchangeLabel(
                isHideBalances = false,
                modifier = Modifier,
                zatoshi = Zatoshi(1),
                state =
                    ExchangeRateState.Data(
                        isLoading = false,
                        isStale = false,
                        currencyConversion =
                            FiatCurrencyConversion(
                                timestamp = Clock.System.now(),
                                priceOfZec = 25.0
                            ),
                        onRefresh = {}
                    )
            )
        }
    }
