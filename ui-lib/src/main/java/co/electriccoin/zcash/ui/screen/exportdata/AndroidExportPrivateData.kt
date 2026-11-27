package co.electriccoin.zcash.ui.screen.exportdata

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.sdk.type.fromResources
import co.electriccoin.zcash.di.koinActivityViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.compose.LocalActivity
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.screen.exportdata.view.ExportPrivateData
import co.electriccoin.zcash.ui.util.FileShareUtil
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun WrapExportPrivateData() {
    val walletViewModel = koinActivityViewModel<WalletViewModel>()
    val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value
    val navigationRouter = koinInject<NavigationRouter>()
    WrapExportPrivateData(
        goBack = { navigationRouter.back() },
        onShare = { navigationRouter.back() },
        synchronizer = synchronizer,
    )
}

@Composable
internal fun WrapExportPrivateData(
    goBack: () -> Unit,
    onShare: () -> Unit,
    synchronizer: Synchronizer?,
) {
    val activity = LocalActivity.current

    BackHandler {
        goBack()
    }

    if (synchronizer == null) {
        // TODO [#1146]: Consider moving CircularScreenProgressIndicator from Android layer to View layer
        // TODO [#1146]: Improve this by allowing screen composition and updating it after the data is available
        // TODO [#1146]: https://github.com/Electric-Coin-Company/zashi-android/issues/1146
        CircularScreenProgressIndicator()
    } else {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        ExportPrivateData(
            snackbarHostState = snackbarHostState,
            onBack = goBack,
            onAgree = {
                // Needed for UI testing only
            },
            onConfirm = {
                scope.launch {
                    shareData(
                        context = activity.applicationContext,
                        synchronizer = synchronizer,
                        snackbarHostState = snackbarHostState,
                    ).collect { shareResult ->
                        if (shareResult) {
                            onShare()
                        }
                    }
                }
            },
        )
    }
}

fun shareData(
    context: Context,
    synchronizer: Synchronizer,
    snackbarHostState: SnackbarHostState,
): Flow<Boolean> =
    callbackFlow {
        val shareIntent =
            FileShareUtil.newShareContentIntent(
                context = context,
                // Example of the expected db file absolute path:
                // /data/user/0/co.electriccoin.zcash/no_backup/co.electricoin.zcash/zcash_sdk_mainnet_data.sqlite3
                dataFilePath =
                    synchronizer.getExistingDataDbFilePath(
                        context = context,
                        network = ZcashNetwork.fromResources(context)
                    ),
                fileType = FileShareUtil.ZASHI_INTERNAL_DATA_MIME_TYPE,
                sharePickerText = context.getString(R.string.export_data_export_data_chooser_title),
                versionInfo = VersionInfo.new(context.applicationContext)
            )
        runCatching {
            context.startActivity(shareIntent)
            trySend(true)
        }.onFailure {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.export_data_unable_to_share)
            )
            trySend(false)
        }
        awaitClose {
            // No resources to release
        }
    }
