package co.electriccoin.zcash.ui.screen.scan

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.util.SettingsUtil
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

@Composable
fun ScanGenericAddressScreen(args: ScanGenericAddressArgs) {
    val vm = koinViewModel<ScanGenericAddressVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    BackHandler { vm.onBack() }
    ScanView(
        snackbarHostState = snackbarHostState,
        onBack = { vm.onBack() },
        onScan = { vm.onScanned(it) },
        onImageScan = { vm.onImageScanned(it) },
        onOpenSettings = {
            runCatching {
                context.startActivity(SettingsUtil.newSettingsIntent(context.packageName))
            }.onFailure {
                // This case should not really happen, as the Settings app should be available on every
                // Android device, but rather handle it.
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.scan_settings_open_failed)
                    )
                }
            }
        },
        onScanStateChange = {},
        validationResult = state,
        invalidQrText = stringResource(R.string.scan_invalidAddressQr),
    )
}

@Serializable
data class ScanGenericAddressArgs(
    val requestId: String = UUID.randomUUID().toString(),
)
