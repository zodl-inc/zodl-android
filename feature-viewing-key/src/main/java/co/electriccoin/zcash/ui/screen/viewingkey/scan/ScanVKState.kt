package co.electriccoin.zcash.ui.screen.viewingkey.scan

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import co.electriccoin.zcash.viewingkey.R
import co.electriccoin.zcash.ui.R as UiR

/**
 * The viewing key scanner, drawn with the shared scan layout.
 *
 * @param infoText shown above the scanner's buttons.
 * @param openSettingsFailed shown when the system settings for the camera permission cannot open.
 */
@Immutable
internal data class ScanVKState(
    val validation: ScanValidationState,
    val invalidQrText: StringResource,
    val infoText: StringResource,
    val openSettingsFailed: StringResource,
    val onBack: () -> Unit,
) {
    companion object {
        val preview: ScanVKState = create(validation = ScanValidationState.NONE, onBack = {})

        fun create(
            validation: ScanValidationState,
            onBack: () -> Unit,
        ) = ScanVKState(
            validation = validation,
            invalidQrText = stringRes(R.string.view_only_invalid_qr_title),
            infoText = stringRes(R.string.view_only_scan_info),
            openSettingsFailed = stringRes(UiR.string.scan_settings_open_failed),
            onBack = onBack,
        )
    }
}

/**
 * The sheet over the scanner when a scanned code is not an importable viewing key.
 */
internal object InvalidVKQrSheet {
    val preview: ZashiConfirmationState = create(onPasteClick = {}, onScanAgainClick = {})

    fun create(
        onPasteClick: () -> Unit,
        onScanAgainClick: () -> Unit,
    ) = ZashiConfirmationState(
        icon = R.drawable.view_only_ic_alert,
        title = stringRes(R.string.view_only_invalid_qr_title),
        message = stringRes(R.string.view_only_invalid_qr_message),
        primaryAction =
            ButtonState(
                text = stringRes(R.string.view_only_invalid_qr_scan_again),
                style = ButtonStyle.PRIMARY,
                onClick = onScanAgainClick,
            ),
        secondaryAction =
            ButtonState(
                text = stringRes(R.string.view_only_invalid_qr_paste),
                style = ButtonStyle.SECONDARY,
                onClick = onPasteClick,
            ),
        onBack = onScanAgainClick,
        style = ZashiConfirmationStyle.UNVERIFIED_POLL_WARNING,
    )
}
