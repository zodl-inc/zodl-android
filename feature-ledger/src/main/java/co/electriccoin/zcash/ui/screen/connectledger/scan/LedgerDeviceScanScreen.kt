package co.electriccoin.zcash.ui.screen.connectledger.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerBluetoothPermissionGate
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun LedgerDeviceScanScreen() {
    val vm = koinViewModel<LedgerDeviceScanVM>()
    val state by vm.state.collectAsStateWithLifecycle()

    LedgerBluetoothPermissionGate(
        permissionRequestNonce = state.permissionRequestNonce,
        enableBluetoothRequestNonce = state.enableBluetoothRequestNonce,
        onPermissionsGranted = vm::onPermissionsGranted,
        onPermissionsDenied = vm::onPermissionsDenied,
        onBluetoothEnabled = vm::onBluetoothEnabled,
        onBluetoothEnableDeclined = vm::onBluetoothEnableDeclined,
    )

    LedgerDeviceScanView(state)
}

@Serializable
data object LedgerDeviceScanArgs
