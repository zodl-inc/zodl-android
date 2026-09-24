package co.electriccoin.zcash.ui.screen.signledgertransaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue

/**
 * Non-dismissable: neither a drag, a tap outside nor system back hides it; only Cancel Transaction
 * or the end of the session leaves it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerSignSheet(state: LedgerSignSheetState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetGesturesEnabled = false,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = rememberScreenModalBottomSheetState(confirmValueChange = { it != SheetValue.Hidden }),
        dragHandle = null,
    ) { sheetState, contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(LedgerSignTag.SHEET)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = 24.dp,
                        bottom = contentPadding.calculateBottomPadding()
                    )
        ) {
            Text(
                text = sheetState.phase.getValue(),
                style = ZashiTypography.textXl,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            sheetState.issueTitle?.let {
                Spacer(12.dp)
                Text(
                    text = it.getValue(),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                )
            }
            sheetState.issueMessage?.let {
                Spacer(8.dp)
                Text(
                    text = it.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
            }
            sheetState.devices.forEachIndexed { index, device ->
                Spacer(8.dp)
                ZashiButton(
                    state =
                        ButtonState(
                            text = device.name,
                            style = if (device.isSelected) ButtonStyle.PRIMARY else ButtonStyle.TERTIARY,
                            isEnabled = device.isEnabled,
                            onClick = device.onClick,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(LedgerSignTag.DEVICE_ROW_PREFIX + index),
                )
            }
            Spacer(24.dp)
            sheetState.primaryButton?.let {
                ZashiButton(
                    state = it,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(LedgerSignTag.PRIMARY_BTN),
                )
                Spacer(8.dp)
            }
            ZashiButton(
                state = sheetState.cancelButton,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(LedgerSignTag.CANCEL_BTN),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun ScanningPreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewScanning)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun DevicesPreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewDevices)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun IssuePreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewIssue)
    }
