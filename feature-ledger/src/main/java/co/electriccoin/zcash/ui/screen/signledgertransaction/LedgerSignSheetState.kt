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
 * The sheet a Ledger signing session runs under. Its title is the same in every phase, and
 * [cancelButton] is there in every phase; the sheet draws it in the destructive colours.
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

        private val previewBodyBeforeReview = stringRes("Keep your Ledger unlocked and nearby.")

        private val previewBodyReview = stringRes("Confirm the transaction on your Ledger device.")

        val previewScanning =
            LedgerSignSheetState(
                content =
                    LedgerSignContent.Progress(
                        body = previewBodyBeforeReview,
                        status = stringRes("Looking for your Ledger"),
                    ),
                cancelButton = previewCancel,
                permissionRequestNonce = 0,
                enableBluetoothRequestNonce = 0,
            )

        val previewOpeningZcashApp =
            previewScanning.copy(
                content =
                    LedgerSignContent.Progress(
                        body = previewBodyBeforeReview,
                        status = stringRes("Open the Zcash app on your Ledger"),
                    ),
            )

        val previewAwaitingReview =
            previewScanning.copy(
                content =
                    LedgerSignContent.Progress(
                        body = previewBodyReview,
                        status = stringRes("Check and approve on your Ledger"),
                    ),
            )

        val previewDevices =
            previewScanning.copy(
                content =
                    LedgerSignContent.Devices(
                        body = previewBodyBeforeReview,
                        title = stringRes("Choose your Ledger"),
                        devices =
                            listOf(
                                LedgerDeviceItemState.preview.copy(name = stringRes("Harry Ledger")),
                                LedgerDeviceItemState.preview.copy(name = stringRes("Office Ledger")),
                            ),
                    ),
            )

        val previewIssue =
            previewScanning.copy(
                content =
                    LedgerSignContent.Issue(
                        icon = R.drawable.ic_ledger_alert_circle,
                        isBadge = true,
                        title = stringRes("Zcash App Not Opened"),
                        message =
                            stringRes(
                                "You declined opening the Zcash app on your Ledger. Try again and confirm " +
                                    "“Open Zcash” on your device."
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
     * A phase of the session the user only waits through, or acts on at the device: [body] under
     * the sheet's title, and [status] next to the spinner.
     */
    data class Progress(
        val body: StringResource,
        val status: StringResource,
    ) : LedgerSignContent

    /**
     * More than one Ledger is in range, or the last session met the wrong one: [title] heads the
     * [devices], and a tap on one connects to it.
     */
    data class Devices(
        val body: StringResource,
        val title: StringResource,
        val devices: List<LedgerDeviceItemState>,
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
