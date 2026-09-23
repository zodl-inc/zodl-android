package co.electriccoin.zcash.ui.screen.connectledger.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiInScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerErrorSheet(state: LedgerErrorSheetState?) {
    ZashiInScreenModalBottomSheet(state = state) { sheetState ->
        Column(
            modifier =
                Modifier
                    .weight(1f, false)
                    .padding(horizontal = 24.dp)
                    .testTag(LedgerDeviceScanTag.ERROR_SHEET),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(ZashiColors.Surfaces.bgSecondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.ic_ledger_alert_circle),
                    contentDescription = null,
                    tint = ZashiColors.Text.textPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = sheetState.title.getValue(),
                style = ZashiTypography.textXl,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sheetState.message.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            ZashiButton(
                state = sheetState.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            sheetState.secondary?.let { secondary ->
                Spacer(Modifier.height(12.dp))
                ZashiButton(
                    state = secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        LedgerErrorSheet(state = LedgerErrorSheetState.preview)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun TwoButtonsPreview() =
    ZcashTheme {
        LedgerErrorSheet(state = LedgerErrorSheetState.previewTwoButtons)
    }
