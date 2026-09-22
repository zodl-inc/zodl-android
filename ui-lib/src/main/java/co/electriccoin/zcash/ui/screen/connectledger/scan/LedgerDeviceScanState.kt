package co.electriccoin.zcash.ui.screen.connectledger.scan

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource

data class LedgerDeviceScanState(
    val title: StringResource,
    val subtitle: StringResource,
    val isScanning: Boolean,
    val devices: List<LedgerDeviceItemState>,
    val primaryButton: ButtonState,
    val errorSheet: LedgerErrorSheetState?,
    val onPermissionsGranted: () -> Unit,
    val onPermissionsDenied: () -> Unit,
    val onBack: () -> Unit,
)

data class LedgerDeviceItemState(
    val identifier: String,
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
