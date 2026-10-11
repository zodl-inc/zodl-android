package co.electriccoin.zcash.ui.screen.connectledger.openapp

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerBluetoothPermissionGate
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LedgerOpenAppScreen(args: LedgerOpenAppArgs) {
    val vm = koinViewModel<LedgerOpenAppVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()

    LedgerBluetoothPermissionGate(
        permissionRequestNonce = state.permissionRequestNonce,
        enableBluetoothRequestNonce = state.enableBluetoothRequestNonce,
        onPermissionsGranted = vm::onPermissionsGranted,
        onPermissionsDenied = vm::onPermissionsDenied,
        onBluetoothEnabled = vm::onBluetoothEnabled,
        onBluetoothEnableDeclined = vm::onBluetoothEnableDeclined,
    )

    BackHandler { state.onBack() }

    LedgerOpenAppView(state)
}

/**
 * @param autoOpen whether the screen asks the Ledger to open the Zcash app as soon as it opens;
 *        false when the app already runs and the flow goes straight on to the handshake, so that
 *        backing out of it lands on an idle page instead of being sent forward again
 */
@Serializable
data class LedgerOpenAppArgs(
    val autoOpen: Boolean = true
)
