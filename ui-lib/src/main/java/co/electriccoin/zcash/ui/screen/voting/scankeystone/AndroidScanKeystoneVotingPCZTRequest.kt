package co.electriccoin.zcash.ui.screen.voting.scankeystone

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.di.koinActivityViewModel
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.screen.scankeystone.view.ScanKeystoneView
import co.electriccoin.zcash.ui.screen.voting.scankeystone.viewmodel.ScanKeystoneVotingPCZTViewModel
import co.electriccoin.zcash.ui.util.SettingsUtil
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun WrapScanKeystoneVotingPCZTRequest(args: ScanKeystoneVotingPCZTRequest) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val walletViewModel = koinActivityViewModel<WalletViewModel>()
    val viewModel = koinViewModel<ScanKeystoneVotingPCZTViewModel> { parametersOf(args) }
    val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value
    val validationState by viewModel.validationState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler { viewModel.onBack() }

    if (synchronizer == null) {
        CircularScreenProgressIndicator()
    } else {
        ScanKeystoneView(
            snackbarHostState = snackbarHostState,
            onBack = viewModel::onBack,
            onScan = { viewModel.onScanned(it) },
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
            validationResult = validationState,
            state = state,
        )
    }
}
