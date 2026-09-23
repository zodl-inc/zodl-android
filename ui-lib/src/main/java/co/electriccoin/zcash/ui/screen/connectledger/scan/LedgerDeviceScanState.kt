package co.electriccoin.zcash.ui.screen.connectledger.scan

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource

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
)

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
)

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
) : ModalBottomSheetState
