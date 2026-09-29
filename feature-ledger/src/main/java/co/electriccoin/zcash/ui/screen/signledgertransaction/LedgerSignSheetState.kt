package co.electriccoin.zcash.ui.screen.signledgertransaction

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceItemState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorContentState

/**
 * The sheet a Ledger signing session runs under. [cancelButton] is there in every phase; the sheet
 * draws it in the destructive colours.
 */
data class LedgerSignSheetState(
    val content: LedgerSignContent,
    val cancelButton: ButtonState,
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
    override val onBack: () -> Unit = {},
) : ModalBottomSheetState {
    companion object {
        private val previewCancel = ButtonState(stringRes("Cancel Transaction"))

        val previewScanning =
            LedgerSignSheetState(
                content =
                    LedgerSignContent.Progress(
                        title = stringRes("Confirm Transaction"),
                        message = stringRes("Looking for your Ledger…"),
                        isSpinning = true,
                    ),
                cancelButton = previewCancel,
                permissionRequestNonce = 0,
                enableBluetoothRequestNonce = 0,
            )

        val previewAwaitingReview =
            previewScanning.copy(
                content =
                    LedgerSignContent.Progress(
                        title = stringRes("Confirm Transaction"),
                        message =
                            stringRes(
                                "Confirm the transaction by pressing the two buttons on your Ledger device."
                            ),
                        isSpinning = false,
                    ),
            )

        val previewDevices =
            previewScanning.copy(
                content =
                    LedgerSignContent.Devices(
                        title = stringRes("Select Your Device"),
                        message = stringRes("Select the Ledger device to sign with."),
                        devices = listOf(LedgerDeviceItemState.previewSelected, LedgerDeviceItemState.preview),
                        connectButton = ButtonState(stringRes("Connect")),
                    ),
            )

        val previewIssue =
            previewScanning.copy(
                content =
                    LedgerSignContent.Issue(
                        icon = R.drawable.ic_ledger_alert_circle,
                        isBadge = true,
                        title = stringRes("Transaction Rejected"),
                        message =
                            stringRes(
                                "You rejected the transaction on your Ledger. Try again to review it on your device."
                            ),
                        primary = ButtonState(stringRes("Try again")),
                    ),
            )
    }
}

/**
 * What the sheet shows above Cancel Transaction.
 */
sealed interface LedgerSignContent {
    /**
     * A phase of the session the user only waits through, or acts on at the device.
     */
    data class Progress(
        val title: StringResource,
        val message: StringResource,
        val isSpinning: Boolean,
    ) : LedgerSignContent

    /**
     * More than one Ledger is in range; [connectButton] is enabled once a row is selected.
     */
    data class Devices(
        val title: StringResource,
        val message: StringResource,
        val devices: List<LedgerDeviceItemState>,
        val connectButton: ButtonState,
    ) : LedgerSignContent

    /**
     * A failed session. [primary] is absent where nothing in the app can fix the issue and Cancel
     * Transaction is the only way on.
     */
    data class Issue(
        @get:DrawableRes
        override val icon: Int,
        override val isBadge: Boolean,
        override val title: StringResource,
        override val message: StringResource,
        override val primary: ButtonState?,
    ) : LedgerSignContent,
        LedgerErrorContentState {
        override val secondary: ButtonState? = null
    }
}
