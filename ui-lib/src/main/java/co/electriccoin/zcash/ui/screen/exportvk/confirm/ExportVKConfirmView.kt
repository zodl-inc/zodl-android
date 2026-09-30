package co.electriccoin.zcash.ui.screen.exportvk.confirm

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiCard
import co.electriccoin.zcash.ui.design.component.ZashiCheckboxCard
import co.electriccoin.zcash.ui.design.component.ZashiCheckboxCardDefaults
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.screen.common.ZodlInfoBottomSheetDefaults
import co.electriccoin.zcash.ui.screen.common.ZodlInfoBottomSheetView

private val ALERT_ICON_SIZE = 20.dp

private val CONTENT_HORIZONTAL_PADDING = 24.dp

/** Half of the 12 dp gap the design puts between the acknowledgements, so the gap belongs to the tap targets. */
private val CHECKBOX_VERTICAL_PADDING = 6.dp

private val CHECKBOX_LIST_SPACING = 24.dp - CHECKBOX_VERTICAL_PADDING

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportVKConfirmView(
    state: ExportVKConfirmState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZodlInfoBottomSheetView(
        state = state,
        primaryButton = state?.exportButton,
        secondaryButton = state?.cancelButton,
        sheetState = sheetState,
        contentPadding = PaddingValues(0.dp),
    ) { innerState ->
        Text(
            modifier = Modifier.padding(ZodlInfoBottomSheetDefaults.contentPadding),
            text = stringResource(R.string.exportViewingKey_confirm_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(16.dp)
        AlertCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ZodlInfoBottomSheetDefaults.contentPadding)
        )
        Spacer(CHECKBOX_LIST_SPACING)
        innerState.checkboxes.forEach { checkbox ->
            ZashiCheckboxCard(
                state = checkbox,
                colors =
                    ZashiCheckboxCardDefaults.colors(
                        container = Color.Transparent,
                        border = Color.Unspecified
                    ),
                contentPadding =
                    PaddingValues(
                        horizontal = CONTENT_HORIZONTAL_PADDING,
                        vertical = CHECKBOX_VERTICAL_PADDING
                    ),
                textStyles =
                    ZashiCheckboxCardDefaults.textStyles(
                        title =
                            ZashiTypography.textSm.copy(
                                fontWeight = FontWeight.Medium,
                                color = ZashiColors.Text.textPrimary
                            )
                    )
            )
        }
    }
}

@Composable
private fun AlertCard(modifier: Modifier = Modifier) {
    ZashiCard(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = ZashiColors.Surfaces.bgPrimary,
                contentColor = ZashiColors.Text.textPrimary
            ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_alert_circle),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Utility.ErrorRed.utilityError600),
                modifier = Modifier.size(ALERT_ICON_SIZE)
            )
            Spacer(12.dp)
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.exportViewingKey_confirm_alert),
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        ExportVKConfirmView(state = ExportVKConfirmState.preview)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun DarkPreview() =
    ZcashTheme(appearanceMode = AppearanceMode.DARK) {
        ExportVKConfirmView(state = ExportVKConfirmState.previewAllChecked)
    }
