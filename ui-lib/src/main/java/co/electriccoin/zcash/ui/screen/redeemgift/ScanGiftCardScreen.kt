package co.electriccoin.zcash.ui.screen.redeemgift

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
import co.electriccoin.zcash.ui.screen.scan.ScanView
import co.electriccoin.zcash.ui.util.SettingsUtil
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ScanGiftCardScreen() {
    val vm = koinViewModel<ScanGiftCardVM>()
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
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.scan_settings_open_failed)
                    )
                }
            }
        },
        onScanStateChange = {},
        validationResult = state,
        onPaste = { vm.onPaste() },
        invalidQrText = stringResource(R.string.redeemGift_scan_invalid),
    )
}

@Serializable
data object ScanGiftCardArgs
