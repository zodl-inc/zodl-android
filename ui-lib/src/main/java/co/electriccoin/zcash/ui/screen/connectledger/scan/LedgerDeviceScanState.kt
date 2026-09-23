package co.electriccoin.zcash.ui.screen.connectledger.scan

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

data class LedgerDeviceScanState(
    val title: StringResource,
    val subtitle: StringResource,
    val isScanning: Boolean,
    /**
     * Whether the placeholder rows stand in for devices still being looked for. False once the
     * scan has stopped, so an idle screen does not pretend to be searching.
     */
    val showDeviceSkeletons: Boolean,
    val devices: List<LedgerDeviceItemState>,
    val primaryButton: ButtonState,
    val errorSheet: LedgerErrorSheetState?,
    /**
     * Bumped whenever the view model wants the screen to launch the runtime permission request
     * again; the screen keys its request effect on it.
     */
    val permissionRequestNonce: Int,
    val onPermissionsGranted: () -> Unit,
    val onPermissionsDenied: (canRequestAgain: Boolean) -> Unit,
    val onBack: () -> Unit,
) {
    companion object {
        val previewSearching =
            LedgerDeviceScanState(
                title = stringRes("Searching for Devices…"),
                subtitle = stringRes("Make sure your Ledger is unlocked and Bluetooth is enabled."),
                isScanning = true,
                showDeviceSkeletons = true,
                devices = emptyList(),
                primaryButton = ButtonState(stringRes("Searching"), isEnabled = false, isLoading = true),
                errorSheet = null,
                permissionRequestNonce = 0,
                onPermissionsGranted = {},
                onPermissionsDenied = {},
                onBack = {},
            )

        val previewSelect =
            previewSearching.copy(
                title = stringRes("Select Your Device"),
                subtitle = stringRes("Select the Ledger device you'd like to connect."),
                isScanning = false,
                showDeviceSkeletons = false,
                devices =
                    listOf(
                        LedgerDeviceItemState.previewSelected,
                        LedgerDeviceItemState.preview,
                    ),
                primaryButton = ButtonState(stringRes("Connect")),
            )

        val previewPairing =
            previewSelect.copy(
                devices = previewSelect.devices.map { it.copy(isEnabled = false) },
                primaryButton = ButtonState(stringRes("Connect"), isEnabled = false, isLoading = true),
            )

        val previewError = previewSearching.copy(errorSheet = LedgerErrorSheetState.preview)
    }
}

/**
 * A device row. It deliberately carries no identifier: the only one a scan has is the device's
 * Bluetooth address, a stable hardware identifier that must not reach the semantics tree. The view
 * keys and tags rows by position; the selection itself is tracked inside the view model.
 */
data class LedgerDeviceItemState(
    val name: StringResource,
    val isSelected: Boolean,
    val isEnabled: Boolean,
    val onClick: () -> Unit,
) {
    companion object {
        val preview =
            LedgerDeviceItemState(
                name = stringRes("Ledger Device 2"),
                isSelected = false,
                isEnabled = true,
                onClick = {},
            )

        val previewSelected = preview.copy(name = stringRes("Ledger Device 1"), isSelected = true)
    }
}

/**
 * The one error sheet the scan screen owns; only the copy and the buttons differ between the
 * seven cases in the Figma "Error States and Edge Cases" section.
 */
data class LedgerErrorSheetState(
    val title: StringResource,
    val message: StringResource,
    val primary: ButtonState,
    val secondary: ButtonState?,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview =
            LedgerErrorSheetState(
                title = stringRes("No Devices Found"),
                message =
                    stringRes(
                        "We couldn't find any Ledger devices nearby. Make sure your Ledger " +
                            "hardware is unlocked and Bluetooth is turned on."
                    ),
                primary = ButtonState(stringRes("Try again")),
                secondary = null,
                onBack = {},
            )

        val previewTwoButtons =
            LedgerErrorSheetState(
                title = stringRes("Account Already Added"),
                message = stringRes("This account is already connected to Zodl."),
                primary = ButtonState(stringRes("Go to Account")),
                secondary = ButtonState(stringRes("Cancel"), style = ButtonStyle.SECONDARY),
                onBack = {},
            )
    }
}
