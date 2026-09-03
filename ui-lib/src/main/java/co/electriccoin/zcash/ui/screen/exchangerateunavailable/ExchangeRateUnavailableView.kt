package co.electriccoin.zcash.ui.screen.exchangerateunavailable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
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
        dragHandle = null,
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
    val hazeState = rememberZashiFrostState()
    var headerHeight by remember { mutableStateOf(0.dp) }
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .zashiFrostSource(hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = headerHeight,
                        bottom = contentPadding.calculateBottomPadding()
                    ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

        ZashiFrostedSheetHeader(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            onHeightChanged = { headerHeight = it },
            title = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
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
                }
            }
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
