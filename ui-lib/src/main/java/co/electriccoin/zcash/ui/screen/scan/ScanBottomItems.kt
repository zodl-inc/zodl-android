package co.electriccoin.zcash.ui.screen.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

@Composable
fun ScanBottomItems(
    validationResult: ScanValidationState,
    scanState: ScanScreenState,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPaste: (() -> Unit)? = null,
    invalidQrText: String? = null,
    infoText: String? = null,
) {
    Column(modifier) {
        ScanInfoRow(
            failureText = scanFailureText(validationResult, scanState, invalidQrText),
            infoText = infoText
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (onPaste != null) {
            ZashiButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(ScanTag.PASTE_BUTTON),
                onClick = onPaste,
                text = stringResource(id = R.string.scan_pasteLink),
                colors = ZashiButtonDefaults.secondaryColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (scanState) {
            ScanScreenState.Scanning, ScanScreenState.Failed -> {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack,
                    text = stringResource(id = co.electriccoin.zcash.ui.design.R.string.general_cancel)
                )
            }

            ScanScreenState.Permission -> {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSettings,
                    text = stringResource(id = R.string.scan_openSettings)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}

@Composable
private fun scanFailureText(
    validationResult: ScanValidationState,
    scanState: ScanScreenState,
    invalidQrText: String?,
): String? {
    val validationFailureText =
        when (validationResult) {
            ScanValidationState.INVALID -> invalidQrText ?: stringResource(id = R.string.scan_invalidQR)
            ScanValidationState.INVALID_IMAGE -> stringResource(id = R.string.scan_invalidImage)
            ScanValidationState.SEVERAL_CODES_FOUND -> stringResource(id = R.string.scan_severalCodesFound)
            else -> null
        }

    // Check permission request result, if any
    return when (scanState) {
        ScanScreenState.Permission -> {
            stringResource(
                id = R.string.scan_cameraSettings,
                stringResource(id = R.string.app_name)
            )
        }

        ScanScreenState.Failed -> {
            stringResource(id = R.string.scan_state_failed)
        }

        ScanScreenState.Scanning -> {
            validationFailureText
        }
    }
}

@Composable
private fun ScanInfoRow(
    failureText: String?,
    infoText: String?,
) {
    // A failure takes the place of the info text while it is shown.
    val messageText = failureText ?: infoText ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = painterResource(R.drawable.ic_scan_info), contentDescription = messageText)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = messageText,
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(if (failureText != null) ScanTag.FAILED_TEXT_STATE else ScanTag.INFO_TEXT)
        )
    }
}
