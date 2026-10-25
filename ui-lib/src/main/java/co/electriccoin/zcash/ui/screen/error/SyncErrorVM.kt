package co.electriccoin.zcash.ui.screen.error

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.chooseserver.ChooseServerArgs
import co.electriccoin.zcash.ui.screen.tor.settings.TorSettingsArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class SyncErrorVM(
    private val args: ErrorArgs.SyncError,
    private val navigationRouter: NavigationRouter,
    private val sendEmailUseCase: SendEmailUseCase,
    private val synchronizerProvider: SynchronizerProvider,
    isTorEnabledUseCase: IsTorEnabledUseCase
) : ViewModel() {
    val state: StateFlow<SyncErrorState> =
        isTorEnabledUseCase
            .observe()
            .take(1)
            .map { isTorEnabled ->
                createState(isTorEnabled)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(false)
            )

    private fun onBack() = navigationRouter.back()

    private fun createState(isTorEnabled: Boolean) =
        SyncErrorState(
            tryAgain =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.disconnectHWWallet_tryAgain),
                    icon = R.drawable.ic_sync_error_try_again,
                    trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                    onClick = ::onTryAgainClick
                ),
            switchServer =
                ButtonState(
                    text = stringRes(R.string.sync_error_switch_server),
                    icon = R.drawable.ic_sync_error_switch_server,
                    trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                    onClick = ::onSwitchServerClick
                ),
            disableTor =
                if (isTorEnabled) {
                    ButtonState(
                        text = stringRes(R.string.sync_error_disable_tor),
                        icon = R.drawable.ic_sync_error_disable_tor,
                        trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                        onClick = ::onDisableTorClick
                    )
                } else {
                    null
                },
            support =
                ButtonState(
                    text = stringRes(R.string.sync_error_contact_support),
                    onClick = ::sendReportClick
                ),
            onBack = ::onBack
        )

    private fun onTryAgainClick() {
        synchronizerProvider.resetSynchronizer()
        navigationRouter.back()
    }

    private fun onSwitchServerClick() = navigationRouter.forward(ChooseServerArgs)

    private fun onDisableTorClick() = navigationRouter.forward(TorSettingsArgs)

    private fun sendReportClick() =
        viewModelScope.launch {
            navigationRouter.back()
            sendEmailUseCase(args.synchronizerError)
        }
}
