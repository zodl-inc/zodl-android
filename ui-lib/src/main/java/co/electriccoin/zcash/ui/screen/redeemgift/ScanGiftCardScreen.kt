package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.scan.ScanView
import co.electriccoin.zcash.ui.util.SettingsUtil
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ScanGiftCardScreen(args: ScanGiftCardArgs) {
    val vm = koinViewModel<ScanGiftCardVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.scan_settings_open_failed)
                    )
                }
            }
        },
        onScanStateChange = {},
        validationResult = state.validation,
        onPaste = { vm.onPaste() },
        invalidQrText = state.invalidQrText.getValue(),
        infoText = state.infoText?.getValue(),
    )
}

/**
 * @param isFromExternalLink whether the scanner was opened because a gift card link arrived from outside the app. That
 * link is never redeemed: the scanner asks the user to scan the card with the app instead.
 */
@Serializable
data class ScanGiftCardArgs(
    val isFromExternalLink: Boolean = false
)
