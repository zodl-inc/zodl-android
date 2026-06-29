package co.electriccoin.zcash.ui.screen.swap.info

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapInfoView(state: SwapInfoState) {
    InfoBottomSheetView(
        onBack = state.onBack,
        primaryButton =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                onClick = state.onBack,
            ),
    ) {
        Row(
            modifier =
                androidx.compose.ui.Modifier
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = stringResource(R.string.swapAndPay_help_swapWith),
                style = ZashiTypography.textXl,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(10.dp)
            // MOB-1396: swaps now aggregate multiple providers — show every active provider's logo.
            Image(painterResource(R.drawable.ic_provider_near), contentDescription = null)
            Spacer(6.dp)
            Image(painterResource(R.drawable.ic_provider_maya), contentDescription = null)
        }
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.swap_info_message, CURRENCY_TICKER, CURRENCY_TICKER, CURRENCY_TICKER),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() = ZcashTheme { SwapInfoView(state = SwapInfoState(onBack = {})) }
