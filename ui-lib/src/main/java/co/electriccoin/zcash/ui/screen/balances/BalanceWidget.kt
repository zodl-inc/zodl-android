package co.electriccoin.zcash.ui.screen.balances

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ShimmerTextPlaceholder
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.StyledBalance
import co.electriccoin.zcash.ui.design.component.StyledBalanceDefaults
import co.electriccoin.zcash.ui.design.component.rememberZashiShimmer
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.screen.balances.BalanceTag.BALANCE_VIEWS
import co.electriccoin.zcash.ui.screen.exchangerate.widget.StyledExchangeBalance
import com.valentinilk.shimmer.shimmer

@Composable
fun BalanceWidget(state: BalanceWidgetState, modifier: Modifier = Modifier) {
    Column(
        modifier =
            Modifier
                .wrapContentSize()
                .then(modifier)
                .testTag(BALANCE_VIEWS),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BalanceWidgetHeader(
            modifier =
                state.onBalanceClick
                    ?.let { onClick ->
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                    }
                    ?: Modifier,
            zatoshi = state.totalBalance,
            showDust = state.showDust
        )

        state.button?.let {
            Spacer(12.dp)
            BalanceWidgetButton(it)
        }

        if (state.exchangeRate != null) {
            if (state.exchangeRate is ExchangeRateState.Data) {
                Spacer(12.dp)
            }
            StyledExchangeBalance(state = state.exchangeRate, zatoshi = state.totalBalance)
        }
    }
}

@Composable
fun BalanceWidgetHeader(
    zatoshi: Zatoshi?,
    modifier: Modifier = Modifier,
    isHideBalances: Boolean = LocalBalancesAvailable.current.not(),
    showDust: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_balance_zec),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ZashiColors.Text.textPrimary)
        )
        Spacer(6.dp)

        if (zatoshi == null) {
            ShimmerTextPlaceholder(
                sampleText = "0.000",
                style = ZashiTypography.header2.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.shimmer(rememberZashiShimmer()),
            )
        } else {
            StyledBalance(
                showDust = showDust,
                balance = zatoshi,
                isHideBalances = isHideBalances,
                textStyle =
                    StyledBalanceDefaults.textStyles(
                        mostSignificantPart = ZashiTypography.header2.copy(fontWeight = FontWeight.SemiBold),
                        leastSignificantPart = ZashiTypography.textXs.copy(fontWeight = FontWeight.SemiBold),
                    )
            )
        }
    }
}

@PreviewScreens
@Composable
private fun LoadingPreview() = Preview(BalanceWidgetState.loadingPreview)

@PreviewScreens
@Composable
private fun CompletePreview() = Preview(BalanceWidgetState.completePreview)

@Composable
private fun Preview(state: BalanceWidgetState) {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface(modifier = Modifier.fillMaxWidth()) {
            BalanceWidget(state)
        }
    }
}
