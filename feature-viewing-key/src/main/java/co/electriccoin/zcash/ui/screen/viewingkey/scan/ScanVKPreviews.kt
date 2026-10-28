package co.electriccoin.zcash.ui.screen.viewingkey.scan

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.scan.ScanView

@Composable
private fun ScanPreviewContent(state: ScanVKState) =
    ScanView(
        snackbarHostState = SnackbarHostState(),
        onBack = state.onBack,
        onScan = {},
        onImageScan = {},
        onOpenSettings = {},
        onScanStateChange = {},
        validationResult = state.validation,
        invalidQrText = state.invalidQrText.getValue(),
        infoText = state.infoText.getValue(),
    )

@PreviewScreens
@Composable
private fun ScanPreview() =
    ZcashTheme {
        ScanPreviewContent(ScanVKState.preview)
    }

@PreviewScreens
@Composable
private fun InvalidQrPreview() =
    ZcashTheme {
        ScanPreviewContent(ScanVKState.preview)
        ZashiConfirmationBottomSheet(InvalidVKQrSheet.preview)
    }
