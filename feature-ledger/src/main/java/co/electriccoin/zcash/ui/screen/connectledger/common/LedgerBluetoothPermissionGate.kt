package co.electriccoin.zcash.ui.screen.connectledger.common

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * The Bluetooth runtime permissions and the enable-Bluetooth system dialog every Ledger screen
 * needs before it can talk to a device. It renders nothing and does nothing in previews.
 *
 * A permanently denied Bluetooth permission answers the request immediately and leaves both
 * `allPermissionsGranted` and `shouldShowRationale` false, so the result callback is the only
 * signal that the user was asked at all — hence `isRequestAnswered`, without which the first
 * resume would report a denial before the request had been answered. Those same flags separate a
 * soft denial, which can be asked again in-app, from a permanent one, which only Settings can
 * undo.
 *
 * The permission state is read back through a holder because the result callback is part of its
 * own construction and cannot refer to it directly.
 *
 * Turning Bluetooth on goes through the system dialog rather than Settings; its request needs
 * `BLUETOOTH_CONNECT` on API 31+, which is always granted by the time the SDK reports Bluetooth as
 * off. The last handled nonce is saved so a configuration change does not show the dialog again.
 *
 * @param permissionRequestNonce bumped by the caller to launch the runtime request again
 * @param enableBluetoothRequestNonce bumped by the caller to launch the enable-Bluetooth dialog
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun LedgerBluetoothPermissionGate(
    permissionRequestNonce: Int,
    enableBluetoothRequestNonce: Int,
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: (canRequestAgain: Boolean) -> Unit,
    onBluetoothEnabled: () -> Unit,
    onBluetoothEnableDeclined: () -> Unit,
) {
    if (LocalInspectionMode.current) return

    val currentOnPermissionsGranted by rememberUpdatedState(onPermissionsGranted)
    val currentOnPermissionsDenied by rememberUpdatedState(onPermissionsDenied)
    val currentOnBluetoothEnabled by rememberUpdatedState(onBluetoothEnabled)
    val currentOnBluetoothEnableDeclined by rememberUpdatedState(onBluetoothEnableDeclined)

    var isRequestAnswered by rememberSaveable { mutableStateOf(false) }
    val permissionsHolder = remember { mutableStateOf<MultiplePermissionsState?>(null) }

    val permissionsState =
        rememberMultiplePermissionsState(
            permissions = remember { bluetoothPermissions() },
            onPermissionsResult = { result ->
                isRequestAnswered = true
                if (result.values.all { it }) {
                    currentOnPermissionsGranted()
                } else {
                    currentOnPermissionsDenied(permissionsHolder.value?.canRequestAgain() == true)
                }
            },
        )
    SideEffect { permissionsHolder.value = permissionsState }

    LaunchedEffect(Unit) {
        if (permissionsState.allPermissionsGranted) {
            currentOnPermissionsGranted()
        } else {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(permissionRequestNonce) {
        if (permissionRequestNonce > 0 && !permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    var handledEnableBluetoothNonce by rememberSaveable { mutableIntStateOf(0) }
    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                currentOnBluetoothEnabled()
            } else {
                currentOnBluetoothEnableDeclined()
            }
        }

    LaunchedEffect(enableBluetoothRequestNonce) {
        val nonce = enableBluetoothRequestNonce
        if (nonce > 0 && nonce != handledEnableBluetoothNonce) {
            handledEnableBluetoothNonce = nonce
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (permissionsState.allPermissionsGranted) {
            currentOnPermissionsGranted()
        } else if (isRequestAnswered) {
            currentOnPermissionsDenied(permissionsState.canRequestAgain())
        }
    }
}

/**
 * Whether the system will still show the runtime dialog: Android reports a rationale exactly while
 * the user has denied without choosing "don't ask again".
 */
@OptIn(ExperimentalPermissionsApi::class)
private fun MultiplePermissionsState.canRequestAgain(): Boolean =
    revokedPermissions.any { it.status.shouldShowRationale }

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
