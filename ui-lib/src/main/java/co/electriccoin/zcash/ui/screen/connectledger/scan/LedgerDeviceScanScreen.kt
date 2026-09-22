package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.Manifest
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

/**
 * A permanently denied Bluetooth permission answers the request immediately and leaves both
 * `allPermissionsGranted` and `shouldShowRationale` false, so the result callback is the only
 * signal that the user was asked at all — hence `isRequestAnswered`, without which the first
 * resume would report a denial before the request had been answered.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LedgerDeviceScanScreen() {
    val vm = koinViewModel<LedgerDeviceScanVM>()
    val state by vm.state.collectAsStateWithLifecycle()

    if (LocalInspectionMode.current) {
        LedgerDeviceScanView(state)
        return
    }

    var isRequestAnswered by rememberSaveable { mutableStateOf(false) }

    val permissionsState =
        rememberMultiplePermissionsState(
            permissions = remember { bluetoothPermissions() },
            onPermissionsResult = { result ->
                isRequestAnswered = true
                if (result.values.all { it }) {
                    state.onPermissionsGranted()
                } else {
                    state.onPermissionsDenied()
                }
            },
        )

    LaunchedEffect(Unit) {
        if (permissionsState.allPermissionsGranted) {
            state.onPermissionsGranted()
        } else {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (permissionsState.allPermissionsGranted) {
            state.onPermissionsGranted()
        } else if (isRequestAnswered) {
            state.onPermissionsDenied()
        }
    }

    LedgerDeviceScanView(state)
}

/**
 * API 31 replaced the location-derived Bluetooth permissions with the scan/connect pair; below it
 * the BLE scan still needs fine location. minSdk is 27, so both branches ship.
 */
private fun bluetoothPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Serializable
data object LedgerDeviceScanArgs
