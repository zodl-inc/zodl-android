package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState

/**
 * The gift card scanner, drawn with the shared scan layout.
 *
 * @param infoText shown above the scanner; set when the scanner was opened for a gift card link from another app.
 */
@Immutable
data class ScanGiftCardState(
    val validation: ScanValidationState,
    val invalidQrText: StringResource,
    val infoText: StringResource?,
    val onBack: () -> Unit,
)
