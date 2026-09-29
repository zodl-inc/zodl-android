package co.electriccoin.zcash.ui.screen.signledgertransaction

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceRow
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorContent

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
                    .animateContentSize()
                    .padding(
                        top = 24.dp,
                        bottom = contentPadding.calculateBottomPadding()
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val content = sheetState.content) {
                is LedgerSignContent.Progress -> {
                    ProgressContent(content)
                }

                is LedgerSignContent.Devices -> {
                    DevicesContent(content)
                }

                is LedgerSignContent.Issue -> {
                    LedgerErrorContent(
                        state = content,
                        primaryModifier = Modifier.testTag(LedgerSignTag.PRIMARY_BTN),
                    )
                }
            }
            if (sheetState.content is LedgerSignContent.Progress) {
                Spacer(12.dp)
            }
            ZashiButton(
                state = sheetState.cancelButton,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag(LedgerSignTag.CANCEL_BTN),
                defaultPrimaryColors = ZashiButtonDefaults.destructive2Colors(),
            )
        }
    }
}

@Composable
private fun ProgressContent(content: LedgerSignContent.Progress) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(title = content.title, message = content.message)
        if (content.isSpinning) {
            Spacer(16.dp)
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = ZashiColors.Text.textPrimary,
                strokeWidth = 2.dp,
            )
        }
        Spacer(12.dp)
    }
}

@Composable
private fun ColumnScope.DevicesContent(content: LedgerSignContent.Devices) {
    Column(
        modifier =
            Modifier
                .weight(1f, false)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(title = content.title, message = content.message)
        Spacer(24.dp)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            content.devices.forEachIndexed { index, device ->
                if (index != 0) {
                    Spacer(12.dp)
                }
                LedgerDeviceRow(
                    state = device,
                    testTag = LedgerSignTag.DEVICE_ROW_PREFIX + index,
                )
            }
        }
        Spacer(24.dp)
        PrimaryButton(content.connectButton)
    }
}

@Composable
private fun ColumnScope.Header(
    title: StringResource,
    message: StringResource,
) {
    Image(
        modifier = Modifier.size(44.dp),
        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_item_ledger),
        contentDescription = null,
    )
    Spacer(12.dp)
    Text(
        text = title.getValue(),
        style = ZashiTypography.textXl,
        fontWeight = FontWeight.SemiBold,
        color = ZashiColors.Text.textPrimary,
        textAlign = TextAlign.Center,
    )
    Spacer(4.dp)
    Text(
        text = message.getValue(),
        style = ZashiTypography.textSm,
        color = ZashiColors.Text.textTertiary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PrimaryButton(state: ButtonState) {
    ZashiButton(
        state = state,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(LedgerSignTag.PRIMARY_BTN),
    )
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
private fun AwaitingReviewPreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewAwaitingReview)
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
