package co.electriccoin.zcash.ui.screen.exchangerateunavailable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExchangeRateUnavailableView(
    state: ExchangeRateUnavailableState?,
) {
    ZashiScreenModalBottomSheet(
        state = state,
        content = { innerState, contentPadding ->
            BottomSheetContent(
                state = innerState,
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f, false)
            )
        },
    )
}

@Composable
private fun BottomSheetContent(
    state: ExchangeRateUnavailableState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_reset_zashi_warning),
            contentDescription = null
        )
        Spacer(12.dp)
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.textXl,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(4.dp)
        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Center
        )
        Spacer(32.dp)
        ZashiButton(
            state = state.switchToUsdButton,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(12.dp)
        ZashiButton(
            state = state.continueInZecButton,
            modifier = Modifier.fillMaxWidth(),
            defaultSecondaryColors =
                ZashiButtonDefaults.secondaryColors(
                    borderColor = ZashiColors.Btns.Secondary.btnSecondaryBorder
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun ExchangeRateUnavailablePreview() =
    ZcashTheme {
        ExchangeRateUnavailableView(
            state = ExchangeRateUnavailableState.preview
        )
    }
