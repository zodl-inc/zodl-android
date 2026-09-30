package co.electriccoin.zcash.ui.screen.connectledger.scan

import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceItemState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheetState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssueState

data class LedgerDeviceScanState(
    val title: StringResource,
    val subtitle: StringResource,
    val isScanning: Boolean,
    /**
     * Whether the placeholder rows stand in for devices still being looked for. False once the
     * scan has stopped, so an idle screen does not pretend to be searching — unless an
     * [inlineIssue] sits over them, in which case they stay, static.
     */
    val showDeviceSkeletons: Boolean,
    val devices: List<LedgerDeviceItemState>,
    /**
     * The issue shown over the placeholder rows while no device is listed; it outlives a dismissed
     * [errorSheet].
     */
    val inlineIssue: LedgerInlineIssueState?,
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
        val previewSearching =
            LedgerDeviceScanState(
                title = stringRes("Searching for Devices..."),
                subtitle = stringRes("Make sure your Ledger is unlocked and Bluetooth is enabled."),
                isScanning = true,
                showDeviceSkeletons = true,
                devices = emptyList(),
                inlineIssue = null,
                primaryButton =
                    ButtonState(
                        text = stringRes("Searching"),
                        icon = R.drawable.ic_ledger_loading,
                        isEnabled = false,
                        isIconRotating = true,
                    ),
                errorSheet = null,
                permissionRequestNonce = 0,
                enableBluetoothRequestNonce = 0,
                onBack = {},
            )

        val previewSelect =
            previewSearching.copy(
                title = stringRes("Select Your Ledger"),
                subtitle = stringRes(SELECT_SUBTITLE),
                isScanning = false,
                showDeviceSkeletons = false,
                devices =
                    listOf(
                        LedgerDeviceItemState.previewSelected,
                        LedgerDeviceItemState.preview,
                    ),
                primaryButton = ButtonState(stringRes("Connect")),
            )

        val previewSelectNone =
            previewSelect.copy(
                devices = listOf(LedgerDeviceItemState.preview),
                primaryButton = ButtonState(stringRes("Connect"), isEnabled = false),
            )

        val previewConnecting =
            previewSelect.copy(
                devices = previewSelect.devices.map { it.copy(isEnabled = false) },
                primaryButton = ButtonState(stringRes("Connect"), isEnabled = false, isLoading = true),
            )

        val previewBluetoothOff =
            previewSearching.copy(
                title = stringRes("Select Your Ledger"),
                subtitle = stringRes(SELECT_SUBTITLE),
                isScanning = false,
                inlineIssue = LedgerInlineIssueState.preview,
                primaryButton = ButtonState(stringRes("Try again")),
                errorSheet = LedgerErrorSheetState.previewBluetoothOff,
            )

        val previewAccessRequired =
            previewBluetoothOff.copy(
                inlineIssue = LedgerInlineIssueState.previewAccessRequired,
                errorSheet = null,
            )

        val previewNoDevices =
            previewSearching.copy(
                subtitle = stringRes(SELECT_SUBTITLE),
                isScanning = false,
                inlineIssue = LedgerInlineIssueState.previewNoDevices,
                primaryButton = ButtonState(stringRes("Try again")),
                errorSheet = LedgerErrorSheetState.preview,
            )

        val previewSomethingWentWrong =
            previewSearching.copy(
                isScanning = false,
                inlineIssue = LedgerInlineIssueState.previewSomethingWentWrong,
                primaryButton = ButtonState(stringRes("Try again")),
                errorSheet = null,
            )

        val previewUnlock =
            previewSelect.copy(
                devices = listOf(LedgerDeviceItemState.previewSelected),
                errorSheet = LedgerErrorSheetState.previewUnlock,
            )
    }
}

private const val SELECT_SUBTITLE =
    "After you tap Connect, check that the code on your phone matches the one on your Ledger."
