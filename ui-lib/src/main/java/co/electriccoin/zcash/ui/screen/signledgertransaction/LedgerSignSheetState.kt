package co.electriccoin.zcash.ui.screen.signledgertransaction

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceItemState

/**
 * The sheet the Ledger signing session runs under. [primaryButton] is the device-selection Connect
 * or the retry of a failed session, whichever the phase offers; [cancelButton] is always there.
 */
data class LedgerSignSheetState(
    val phase: StringResource,
    val devices: List<LedgerDeviceItemState>,
    val issueTitle: StringResource?,
    val issueMessage: StringResource?,
    val primaryButton: ButtonState?,
    val cancelButton: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        private val previewCancel =
            ButtonState(stringRes("Cancel Transaction"), style = ButtonStyle.DESTRUCTIVE2)

        val previewScanning =
            LedgerSignSheetState(
                phase = stringRes("Looking for your Ledger…"),
                devices = emptyList(),
                issueTitle = null,
                issueMessage = null,
                primaryButton = null,
                cancelButton = previewCancel,
                onBack = {},
            )

        val previewDevices =
            previewScanning.copy(
                phase = stringRes("Select your device"),
                devices = listOf(LedgerDeviceItemState.previewSelected, LedgerDeviceItemState.preview),
                primaryButton = ButtonState(stringRes("Connect")),
            )

        val previewIssue =
            previewScanning.copy(
                phase = stringRes("Failed"),
                issueTitle = stringRes("Transaction Rejected"),
                issueMessage = stringRes("You declined the transaction on your Ledger."),
                primaryButton = ButtonState(stringRes("Try again")),
            )
    }
}
