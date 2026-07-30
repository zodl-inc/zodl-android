package co.electriccoin.zcash.ui.screen.error

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.ServerCompatibilityError
import co.electriccoin.zcash.ui.common.model.toServerCompatibilityError
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.listitem.SimpleListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.chooseserver.ChooseServerArgs
import co.electriccoin.zcash.ui.screen.tor.settings.TorSettingsArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class SyncErrorVM(
    private val args: ErrorArgs.SyncError,
    private val navigationRouter: NavigationRouter,
    private val sendEmailUseCase: SendEmailUseCase,
    private val synchronizerProvider: SynchronizerProvider,
    getSelectedEndpointUseCase: GetSelectedEndpointUseCase,
    isTorEnabledUseCase: IsTorEnabledUseCase
) : ViewModel() {
    val state: StateFlow<SyncErrorState> =
        combine(
            isTorEnabledUseCase.observe().take(1),
            getSelectedEndpointUseCase.observe()
        ) { isTorEnabled, endpoint ->
            createState(isTorEnabled = isTorEnabled, endpoint = endpoint)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = createState(isTorEnabled = false, endpoint = null)
        )

    private fun onBack() = navigationRouter.back()

    private fun createState(
        isTorEnabled: Boolean,
        endpoint: LightWalletEndpoint?
    ) = SyncErrorState(
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
        onBack = ::onBack,
        diagnostics = createDiagnostics(endpoint)
    )

    /**
     * Diagnostics are shown only for the server-compatibility family, where the user has to make a
     * decision (update or switch server) and the specifics tell them which. Generic and transient
     * sync errors get no detail, because there retrying is the remedy and internals are only noise.
     */
    private fun createDiagnostics(endpoint: LightWalletEndpoint?): SyncErrorDiagnosticsState? {
        val error = args.synchronizerError.toServerCompatibilityError() ?: return null
        return SyncErrorDiagnosticsState(
            explanation = stringRes(explanationOf(error)),
            facts =
                buildList {
                    if (endpoint != null) {
                        add(
                            SimpleListItemState(
                                title = stringRes(R.string.sync_error_detail_server),
                                text = stringRes("${endpoint.host}:${endpoint.port}")
                            )
                        )
                    }
                    addAll(mismatchFacts(error))
                    add(
                        SimpleListItemState(
                            title = stringRes(R.string.sync_error_detail_error_type),
                            text = stringRes(error.type)
                        )
                    )
                }
        )
    }

    @StringRes
    private fun explanationOf(error: ServerCompatibilityError) =
        when (error) {
            is ServerCompatibilityError.ConsensusBranch -> R.string.sync_error_incompatible_consensus_message
            is ServerCompatibilityError.Network -> R.string.sync_error_incompatible_network_message
            is ServerCompatibilityError.SaplingActivationHeight -> R.string.sync_error_incompatible_sapling_message
        }

    private fun mismatchFacts(error: ServerCompatibilityError): List<SimpleListItemState> =
        when (error) {
            is ServerCompatibilityError.ConsensusBranch -> {
                listOf(
                    fact(R.string.sync_error_detail_expected_branch, "0x${error.clientBranchId}"),
                    fact(R.string.sync_error_detail_server_branch, "0x${error.serverBranchId}")
                )
            }

            is ServerCompatibilityError.Network -> {
                listOfNotNull(
                    error.clientNetwork?.let { fact(R.string.sync_error_detail_expected_network, it) },
                    error.serverNetwork?.let { fact(R.string.sync_error_detail_server_network, it) }
                )
            }

            is ServerCompatibilityError.SaplingActivationHeight -> {
                listOf(
                    fact(R.string.sync_error_detail_expected_sapling, error.clientHeight.toString()),
                    fact(R.string.sync_error_detail_server_sapling, error.serverHeight.toString())
                )
            }
        }

    private fun fact(
        @StringRes titleRes: Int,
        value: String
    ) = SimpleListItemState(title = stringRes(titleRes), text = stringRes(value))

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
