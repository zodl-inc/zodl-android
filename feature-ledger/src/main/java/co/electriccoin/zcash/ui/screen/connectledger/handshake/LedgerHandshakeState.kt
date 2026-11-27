package co.electriccoin.zcash.ui.screen.connectledger.handshake

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheetState

data class LedgerHandshakeState(
    val title: StringResource,
    val message: StringResource,
    /**
     * Whether the Ledger is being asked for the account; the page then shows the placeholder rows
     * with the waiting indicator over them.
     */
    val isConnecting: Boolean,
    /**
     * A disabled Connect while the handshake runs; afterwards it retries, or repeats the sheet's
     * primary action.
     */
    val primaryButton: ButtonState,
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
        val previewConnecting =
            LedgerHandshakeState(
                title = stringRes("Approve on Your Ledger"),
                message =
                    stringRes(
                        "Your Ledger will ask to share your Zcash account with Zodl. " +
                            "Check the request and approve it."
                    ),
                isConnecting = true,
                primaryButton = ButtonState(stringRes("Connect"), isEnabled = false),
                errorSheet = null,
                permissionRequestNonce = 0,
                enableBluetoothRequestNonce = 0,
                onBack = {},
            )

        val previewError =
            previewConnecting.copy(
                isConnecting = false,
                primaryButton = ButtonState(stringRes("Retry")),
                errorSheet = LedgerErrorSheetState.preview,
            )

        val previewErrorDismissed = previewError.copy(errorSheet = null)

        val previewAlreadyAdded =
            previewError.copy(
                primaryButton = ButtonState(stringRes("Go to Account")),
                errorSheet = LedgerErrorSheetState.previewTwoButtons,
            )
    }
}
