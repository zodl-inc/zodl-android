package co.electriccoin.zcash.ui.screen.signledgertransaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerBluetoothPermissionGate
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun LedgerSignScreen() {
    val vm = koinViewModel<LedgerSignVM>()
    val state by vm.state.collectAsStateWithLifecycle()

    LedgerBluetoothPermissionGate(
        permissionRequestNonce = state?.permissionRequestNonce ?: 0,
        enableBluetoothRequestNonce = state?.enableBluetoothRequestNonce ?: 0,
        onPermissionsGranted = vm::onPermissionsGranted,
        onPermissionsDenied = vm::onPermissionsDenied,
        onBluetoothEnabled = vm::onBluetoothEnabled,
        onBluetoothEnableDeclined = vm::onBluetoothEnableDeclined,
    )

    LedgerSignSheet(state)
}

@Serializable
data object LedgerSignArgs
