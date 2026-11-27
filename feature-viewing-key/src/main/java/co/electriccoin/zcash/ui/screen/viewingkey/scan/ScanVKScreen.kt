package co.electriccoin.zcash.ui.screen.viewingkey.scan

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.compose.SecureScreen
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.scan.ScanView
import co.electriccoin.zcash.ui.util.SettingsUtil
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun ScanVKScreen() {
    SecureScreen()
    val vm = koinViewModel<ScanVKVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    val invalidQrSheet by vm.invalidQrSheet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val openSettingsFailed = state.openSettingsFailed.getValue()
    BackHandler { state.onBack() }
    ScanView(
        snackbarHostState = snackbarHostState,
        onBack = state.onBack,
        onScan = { vm.onScanned(it) },
        onImageScan = { vm.onImageScanned(it) },
        onOpenSettings = {
            runCatching {
                context.startActivity(SettingsUtil.newSettingsIntent(context.packageName))
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar(message = openSettingsFailed)
                }
            }
        },
        onScanStateChange = {},
        validationResult = state.validation,
        invalidQrText = state.invalidQrText.getValue(),
        infoText = state.infoText.getValue(),
    )
    ZashiConfirmationBottomSheet(invalidQrSheet)
}

/** The camera scanner for a full viewing key QR code. */
@Serializable
internal data object ScanVKArgs
