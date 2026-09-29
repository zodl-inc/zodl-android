package co.electriccoin.zcash.ui.screen.connectledger.openapp

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheetState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssueState

data class LedgerOpenAppState(
    /**
     * What went wrong, kept on the page after the [errorSheet] is dismissed.
     */
    val inlineIssue: LedgerInlineIssueState?,
    /**
     * Loading and disabled while the Ledger is being asked to open the Zcash app; otherwise it asks
     * again, or repeats the sheet's primary action after a failure.
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
        val preview =
            LedgerOpenAppState(
                inlineIssue = null,
                primaryButton = ButtonState(stringRes("Continue")),
                errorSheet = null,
                permissionRequestNonce = 0,
                enableBluetoothRequestNonce = 0,
                onBack = {},
            )

        val previewOpening =
            preview.copy(
                primaryButton =
                    ButtonState(
                        text = stringRes("Continue"),
                        isEnabled = false,
                        isLoading = true,
                    ),
            )

        private val declinedTitle = stringRes("Open the Zcash App")

        private val declinedMessage =
            stringRes("You declined opening the Zcash app on your Ledger. Try again and confirm it on the device.")

        val previewDeclined =
            preview.copy(
                inlineIssue =
                    LedgerInlineIssueState.preview.copy(
                        title = declinedTitle,
                        message = declinedMessage,
                    ),
                primaryButton = ButtonState(stringRes("Try again")),
                errorSheet =
                    LedgerErrorSheetState.preview.copy(
                        title = declinedTitle,
                        message = declinedMessage,
                    ),
            )
    }
}
