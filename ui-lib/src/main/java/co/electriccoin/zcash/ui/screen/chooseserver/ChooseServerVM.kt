package co.electriccoin.zcash.ui.screen.chooseserver

import android.app.Application
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.EndpointTextFieldInnerState
import co.electriccoin.zcash.ui.common.component.EndpointTextFieldState
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.usecase.GetAutomaticEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.IsServerAutomaticUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistEndpointException
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshFastestServersUseCase
import co.electriccoin.zcash.ui.design.component.AlertDialogState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions", "LongParameterList")
class ChooseServerVM(
    application: Application,
    observeFastestServers: ObserveFastestServersUseCase,
    getSelectedEndpoint: GetSelectedEndpointUseCase,
    isServerAutomatic: IsServerAutomaticUseCase,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val refreshFastestServersUseCase: RefreshFastestServersUseCase,
    private val persistServerSelection: PersistServerSelectionUseCase,
    private val navigationRouter: NavigationRouter,
    private val getAutomaticEndpoint: GetAutomaticEndpointUseCase
) : AndroidViewModel(application) {
    private val innerState =
        MutableStateFlow(InnerState(availableServers = lightWalletEndpointProvider.getEndpoints()))

    private val innerStateSafe = innerState.filter { it.local != null && it.persisted != null }

    val state =
        combine(observeFastestServers(), innerStateSafe) { fastestServers, inner ->
            val local = inner.requireLocal()
            val persisted = inner.requirePersisted()
            ChooseServerState(
                connectionMode = createConnectionMode(local, persisted, fastestServers),
                fastest = createFastest(local, persisted, fastestServers),
                other = createOther(local, persisted, fastestServers),
                saveButton = createSaveButton(local, persisted),
                dialogState = inner.dialogState,
                onBack = ::onBack
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun createConnectionMode(
        local: InnerState.Local,
        persisted: InnerState.Persisted,
        fastestServers: FastestServersState,
    ): ServerConnectionModeState {
        val fastestServer = fastestServers.servers?.firstOrNull()
        return ServerConnectionModeState(
            automatic =
                RadioButtonState(
                    text = stringRes(R.string.choose_server_automatic),
                    subtitle =
                        if (local.isAutomatic) {
                            val automaticEndpoint =
                                if (persisted.isAutomatic) {
                                    persisted.endpoint
                                } else {
                                    getAutomaticEndpoint(fastestServer, persisted.endpoint)
                                }
                            stringRes(
                                R.string.choose_server_full_server_name,
                                automaticEndpoint.host,
                                automaticEndpoint.port
                            )
                        } else {
                            null
                        },
                    isChecked = local.isAutomatic,
                    onClick = ::onAutomaticModeClicked,
                    hapticFeedbackType = if (local.isAutomatic) null else HapticFeedbackType.SegmentTick
                ),
            manual =
                RadioButtonState(
                    text = stringRes(R.string.choose_server_manual),
                    subtitle =
                        if (!local.isAutomatic && fastestServers.isLoading) {
                            stringRes(R.string.choose_server_loading_title)
                        } else {
                            null
                        },
                    isChecked = !local.isAutomatic,
                    onClick = ::onManualModeClicked,
                    hapticFeedbackType = if (!local.isAutomatic) null else HapticFeedbackType.SegmentTick
                ),
            automaticBadge =
                if (fastestServers.isLoading) stringRes(R.string.choose_server_testing) else null
        )
    }

    private fun createFastest(
        local: InnerState.Local,
        persisted: InnerState.Persisted,
        fastestServers: FastestServersState,
    ): ServerListState.Fastest =
        ServerListState.Fastest(
            title = stringRes(R.string.choose_server_fastest_servers),
            servers =
                fastestServers.servers
                    ?.map { endpoint ->
                        createDefaultServerState(
                            endpoint = endpoint,
                            local = local,
                            persistedEndpoint = persisted.endpoint,
                            isManualSelected = !local.isAutomatic
                        )
                    }.orEmpty(),
            isLoading = fastestServers.isLoading,
            retryButton =
                ButtonState(
                    text = stringRes(R.string.choose_server_refresh),
                    isEnabled = !local.isSaveInProgress,
                    onClick = ::onRefreshClicked
                )
        )

    private fun createOther(
        local: InnerState.Local,
        persisted: InnerState.Persisted,
        fastestServers: FastestServersState,
    ): ServerListState.Other {
        val persistedEndpoint = persisted.endpoint
        val customEndpointState = createCustomServerState(local = local, persisted = persisted)
        return ServerListState.Other(
            title =
                if (fastestServers.servers.isNullOrEmpty()) {
                    stringRes(R.string.choose_server_browse_servers)
                } else {
                    stringRes(R.string.choose_server_other_servers)
                },
            servers =
                innerState.value.availableServers
                    .filter { !fastestServers.servers.orEmpty().contains(it) }
                    .map<LightWalletEndpoint, ServerState> { endpoint ->
                        createDefaultServerState(
                            endpoint = endpoint,
                            local = local,
                            persistedEndpoint = persistedEndpoint,
                            isManualSelected = !local.isAutomatic
                        )
                    }.toMutableList()
                    .apply {
                        val index = 1.coerceIn(0, size.coerceAtLeast(0))
                        add(index, customEndpointState)
                    }.toList()
        )
    }

    private fun createSaveButton(local: InnerState.Local, persisted: InnerState.Persisted): ButtonState {
        val isSaveInProgress = local.isSaveInProgress
        return ButtonState(
            text =
                if (isSaveInProgress) {
                    stringRes(R.string.choose_server_saving)
                } else {
                    stringRes(R.string.choose_server_save)
                },
            isEnabled = !isSaveInProgress && hasUnsavedChanges(local, persisted),
            isLoading = isSaveInProgress,
            onClick = ::onSaveButtonClicked,
            hapticFeedbackType = HapticFeedbackType.Confirm
        )
    }

    init {
        combine(
            isServerAutomatic.observe(),
            getSelectedEndpoint.observe().filterNotNull()
        ) { isAutomatic, endpoint -> isAutomatic to endpoint }
            .onEach { (isAutomatic, endpoint) ->
                innerState.update { inner ->
                    val persisted =
                        InnerState.Persisted(
                            endpoint = endpoint,
                            isAutomatic = isAutomatic,
                            isCustomEndpoint = endpoint !in inner.availableServers,
                        )
                    inner.copy(
                        persisted = persisted,
                        local = inner.local ?: createInitialLocalState(persisted)
                    )
                }
            }.launchIn(viewModelScope)
    }

    private fun createInitialLocalState(persisted: InnerState.Persisted): InnerState.Local {
        val endpoint = persisted.endpoint
        val isCustom = persisted.isCustomEndpoint
        return InnerState.Local(
            isAutomatic = persisted.isAutomatic,
            selectedEndpoint = endpoint.takeIf { !isCustom },
            isCustomSelected = !persisted.isAutomatic && isCustom,
            isSaveInProgress = false,
            customEndpoint =
                if (isCustom) EndpointTextFieldInnerState.fromEndpoint(endpoint) else EndpointTextFieldInnerState()
        )
    }

    private fun hasUnsavedChanges(local: InnerState.Local, persisted: InnerState.Persisted): Boolean =
        when {
            local.isAutomatic != persisted.isAutomatic -> {
                true
            }

            local.isAutomatic -> {
                false
            }

            else -> {
                val effectiveEndpoint =
                    if (local.isCustomSelected) local.customEndpoint.endpoint else local.selectedEndpoint
                effectiveEndpoint != null && effectiveEndpoint != persisted.endpoint
            }
        }

    private fun onBack() {
        if (canUpdateSelection()) {
            navigationRouter.back()
        }
    }

    private fun createCustomServerState(local: InnerState.Local, persisted: InnerState.Persisted): ServerState.Custom {
        val isPersistedEndpointCustom = persisted.isCustomEndpoint
        val isEnabled = !local.isSaveInProgress
        val isChecked = !local.isAutomatic && local.isCustomSelected
        val isExpanded = isChecked || (!local.isAutomatic && isPersistedEndpointCustom)
        return ServerState.Custom(
            radioButtonState =
                RadioButtonState(
                    text =
                        if (isPersistedEndpointCustom) {
                            stringRes(
                                R.string.choose_server_full_server_name,
                                persisted.endpoint.host,
                                persisted.endpoint.port,
                            )
                        } else {
                            stringRes(R.string.choose_server_custom)
                        },
                    isChecked = isChecked,
                    onClick = ::onCustomEndpointClicked,
                    hapticFeedbackType = if (isChecked) null else HapticFeedbackType.SegmentTick,
                ),
            newServerTextFieldState =
                EndpointTextFieldState(
                    innerState = local.customEndpoint,
                    isEnabled = isEnabled,
                    onValueChange = ::onCustomEndpointInnerStateChanged,
                ),
            badge = if (isPersistedEndpointCustom) stringRes(R.string.choose_server_active) else null,
            isExpanded = isExpanded,
            key = "custom",
        )
    }

    private fun createDefaultServerState(
        endpoint: LightWalletEndpoint,
        local: InnerState.Local,
        persistedEndpoint: LightWalletEndpoint,
        isManualSelected: Boolean,
    ): ServerState.Default {
        val defaultEndpoint = lightWalletEndpointProvider.getDefaultEndpoint()
        val isEndpointChecked =
            isManualSelected && !local.isCustomSelected && local.selectedEndpoint == endpoint
        return ServerState.Default(
            key = "default_${endpoint.host}_${endpoint.port}",
            radioButtonState =
                RadioButtonState(
                    text = stringRes(R.string.choose_server_full_server_name, endpoint.host, endpoint.port),
                    isChecked = isEndpointChecked,
                    onClick = { onEndpointClicked(endpoint) },
                    subtitle =
                        if (endpoint == defaultEndpoint) {
                            stringRes(R.string.choose_server_save_default)
                        } else {
                            null
                        },
                    hapticFeedbackType = if (isEndpointChecked) null else HapticFeedbackType.SegmentTick,
                ),
            badge = if (endpoint == persistedEndpoint) stringRes(R.string.choose_server_active) else null,
        )
    }

    private fun onRefreshClicked() {
        if (!canUpdateSelection()) return
        refreshFastestServersUseCase()
    }

    private fun onCustomEndpointInnerStateChanged(new: EndpointTextFieldInnerState) {
        if (!canUpdateSelection()) return
        innerState.update { inner ->
            inner.copy(
                local =
                    inner.requireLocal().copy(
                        customEndpoint = new,
                        isAutomatic = false,
                        isCustomSelected = true,
                        selectedEndpoint = null,
                    )
            )
        }
    }

    private fun onAutomaticModeClicked() {
        if (!canUpdateSelection()) return
        innerState.update { inner ->
            inner.copy(local = inner.requireLocal().copy(isAutomatic = true))
        }
    }

    private fun onManualModeClicked() {
        if (!canUpdateSelection()) return
        innerState.update { inner ->
            val persisted = inner.requirePersisted()
            val pickCustom = persisted.isCustomEndpoint
            inner.copy(
                local =
                    inner.requireLocal().copy(
                        isAutomatic = false,
                        selectedEndpoint = if (!pickCustom) persisted.endpoint else null,
                        isCustomSelected = pickCustom
                    )
            )
        }
    }

    private fun onEndpointClicked(endpoint: LightWalletEndpoint) {
        if (!canUpdateSelection()) return
        innerState.update { inner ->
            inner.copy(
                local =
                    inner.requireLocal().copy(
                        isAutomatic = false,
                        selectedEndpoint = endpoint,
                        isCustomSelected = false,
                    )
            )
        }
    }

    private fun onCustomEndpointClicked() {
        if (!canUpdateSelection()) return
        innerState.update { inner ->
            inner.copy(
                local =
                    inner.requireLocal().copy(
                        isAutomatic = false,
                        isCustomSelected = true,
                        selectedEndpoint = null,
                    )
            )
        }
    }

    private fun canUpdateSelection(): Boolean {
        val local = innerState.value.local ?: return false
        return !local.isSaveInProgress
    }

    private fun onSaveButtonClicked() =
        viewModelScope.launch {
            val local = innerState.value.requireLocal()
            if (local.isSaveInProgress) return@launch
            innerState.update { it.copy(local = it.requireLocal().copy(isSaveInProgress = true)) }
            try {
                if (local.isAutomatic) {
                    persistServerSelection.persistAutomatic()
                } else {
                    val ep = if (local.isCustomSelected) local.customEndpoint.endpoint else local.selectedEndpoint
                    if (ep == null) {
                        showValidationErrorDialog()
                        return@launch
                    }
                    persistServerSelection.persistManual(ep)
                }
            } catch (e: PersistEndpointException) {
                showValidationErrorDialog(e.message)
            } finally {
                innerState.update { it.copy(local = it.requireLocal().copy(isSaveInProgress = false)) }
            }
        }

    private fun onConfirmDialogButtonClicked() = innerState.update { it.copy(dialogState = null) }

    private fun showValidationErrorDialog(reason: String? = null) {
        innerState.update {
            it.copy(
                dialogState =
                    ServerDialogState.Validation(
                        AlertDialogState(
                            title = stringRes(R.string.choose_server_validation_dialog_error_title),
                            text = stringRes(R.string.choose_server_validation_dialog_error_text),
                            confirmButtonState =
                                ButtonState(
                                    text = stringRes(R.string.choose_server_save_success_dialog_btn),
                                    onClick = ::onConfirmDialogButtonClicked
                                ),
                        ),
                        reason = reason?.let { stringRes(it) }
                    )
            )
        }
    }
}

private data class InnerState(
    val availableServers: List<LightWalletEndpoint>,
    val local: Local? = null,
    val persisted: Persisted? = null,
    val dialogState: ServerDialogState? = null,
) {
    fun requireLocal(): Local = requireNotNull(local) { "Local state has not been initialized" }

    fun requirePersisted(): Persisted = requireNotNull(persisted) { "Persisted state has not been initialized" }

    data class Local(
        val isAutomatic: Boolean,
        val selectedEndpoint: LightWalletEndpoint?,
        val isCustomSelected: Boolean,
        val isSaveInProgress: Boolean,
        val customEndpoint: EndpointTextFieldInnerState,
    )

    data class Persisted(
        val endpoint: LightWalletEndpoint,
        val isAutomatic: Boolean,
        val isCustomEndpoint: Boolean,
    )
}
