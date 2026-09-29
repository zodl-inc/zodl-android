package co.electriccoin.zcash.ui.screen.connectledger.handshake

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheetState

data class LedgerHandshakeState(
    /**
     * Whether the pairing is running; the page then shows the placeholder rows and the waiting
     * indicator, and otherwise only its header.
     */
    val isWaiting: Boolean,
    val cancelButton: ButtonState,
    /**
     * Absent while the pairing runs and after an issue that trying again cannot fix.
     */
    val retryButton: ButtonState?,
    val errorSheet: LedgerErrorSheetState?,
    /**
     * Bumped whenever the view model wants the screen to launch the runtime permission request
     * again; the screen keys its request effect on it.
     */
    val permissionRequestNonce: Int,
    /**
     * Bumped whenever the view model wants the screen to launch the system dialog that turns
     * Bluetooth on; the screen keys its launch effect on it.
     */
    val enableBluetoothRequestNonce: Int,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            LedgerHandshakeState(
                isWaiting = true,
                cancelButton = ButtonState(stringRes("Cancel"), style = ButtonStyle.DESTRUCTIVE1),
                retryButton = null,
                errorSheet = null,
                permissionRequestNonce = 0,
                enableBluetoothRequestNonce = 0,
                onBack = {},
            )

        val previewError =
            preview.copy(
                isWaiting = false,
                retryButton = ButtonState(stringRes("Retry")),
            )

        val previewErrorSheet =
            previewError.copy(
                errorSheet = LedgerErrorSheetState.preview,
            )
    }
}
