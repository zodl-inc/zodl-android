package co.electriccoin.zcash.ui.screen.connectledger.handshake

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheetState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssueState

data class LedgerHandshakeState(
    val title: StringResource,
    val message: StringResource,
    val isConnecting: Boolean,
    /**
     * What went wrong, kept on the page after the [errorSheet] is dismissed.
     */
    val inlineIssue: LedgerInlineIssueState?,
    /**
     * Absent while the handshake runs; afterwards it repeats the sheet's primary action.
     */
    val primaryButton: ButtonState?,
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
                        "Zodl is connecting to the Zcash app. When your Ledger asks to export your account, " +
                            "check the request and approve it."
                    ),
                isConnecting = true,
                inlineIssue = null,
                primaryButton = null,
                errorSheet = null,
                permissionRequestNonce = 0,
                enableBluetoothRequestNonce = 0,
                onBack = {},
            )

        val previewError =
            previewConnecting.copy(
                title = stringRes("Connect your Ledger"),
                isConnecting = false,
                inlineIssue =
                    LedgerInlineIssueState.preview.copy(
                        title = stringRes("Unlock Your Ledger"),
                        message =
                            stringRes("Unlock your Ledger and open the Zcash app on the device to continue."),
                    ),
                primaryButton = ButtonState(stringRes("Try again")),
                errorSheet = LedgerErrorSheetState.preview,
            )

        val previewAlreadyAdded =
            previewError.copy(
                inlineIssue =
                    LedgerInlineIssueState.preview.copy(
                        title = stringRes("Account Already Added"),
                        message = stringRes("This account is already connected to Zodl."),
                    ),
                primaryButton = ButtonState(stringRes("Go to Account")),
                errorSheet = LedgerErrorSheetState.previewTwoButtons,
            )
    }
}
