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
import co.electriccoin.zcash.ui.common.datasource.resolveIsEndpointCustom
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.model.ServerConnectionMode
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveServerConnectionModeUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistEndpointException
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshFastestServersUseCase
import co.electriccoin.zcash.ui.design.component.AlertDialogState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.chooseserver.ohttpinfo.OhttpInfoArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions", "LongParameterList")
class ChooseServerVM(
    application: Application,
    observeFastestServers: ObserveFastestServersUseCase,
    getSelectedEndpoint: GetSelectedEndpointUseCase,
    observeServerConnectionMode: ObserveServerConnectionModeUseCase,
    automaticServerRepository: AutomaticServerRepository,
    private val preselectFastestClearnet: Boolean,
    private val autoStartProbing: Boolean,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val refreshFastestServersUseCase: RefreshFastestServersUseCase,
    private val persistServerSelection: PersistServerSelectionUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val innerState =
        MutableStateFlow(InnerState(availableServers = lightWalletEndpointProvider.getEndpoints()))

    private val innerStateSafe = innerState.filter { it.local != null && it.persisted != null }

    val state =
        combine(
            observeFastestServers(),
            innerStateSafe,
            automaticServerRepository.ohttpUnavailable
        ) { fastestServers, inner, isOhttpUnavailable ->
            val local = inner.requireLocal()
            val persisted = inner.requirePersisted()
            ChooseServerState(
                connectionMode = createConnectionMode(local, persisted, fastestServers, isOhttpUnavailable),
                fastest = createFastest(local, persisted, fastestServers),
                other = createOther(local, persisted, fastestServers),
                saveButton = createSaveButton(local, persisted, fastestServers),
                dialogState = inner.dialogState,
                explanatoryMessage =
                    if (preselectFastestClearnet) {
                        stringRes(R.string.choose_server_clearnet_preselection_message)
                    } else {
                        null
                    },
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
        isOhttpUnavailable: Boolean,
    ): ServerConnectionModeState {
        val isTesting = fastestServers.isLoading
        val testingBadge =
            ModeBadgeState(
                text = stringRes(R.string.serverSetup_testing),
                style = ModeBadgeStyle.WARNING,
                showsProgress = true
            )
        return ServerConnectionModeState(
            private =
                RadioButtonState(
                    text = stringRes(R.string.serverSetup_private),
                    subtitle = stringRes(R.string.serverSetup_privateSubtitle),
                    isChecked = local.mode == ServerConnectionMode.PRIVATE,
                    onClick = ::onPrivateModeClicked,
                    hapticFeedbackType =
                        if (local.mode == ServerConnectionMode.PRIVATE) null else HapticFeedbackType.SegmentTick
                ),
            public =
                RadioButtonState(
                    text = stringRes(R.string.serverSetup_public),
                    subtitle =
                        if (local.mode == ServerConnectionMode.PUBLIC) {
                            endpointLabel(
                                if (persisted.mode == ServerConnectionMode.PUBLIC) {
                                    persisted.endpoint
                                } else {
                                    lightWalletEndpointProvider.getFallbackEndpoints().firstOrNull()
                                        ?: persisted.endpoint
                                }
                            )
                        } else {
                            null
                        },
                    isChecked = local.mode == ServerConnectionMode.PUBLIC,
                    onClick = ::onPublicModeClicked,
                    hapticFeedbackType =
                        if (local.mode == ServerConnectionMode.PUBLIC) null else HapticFeedbackType.SegmentTick
                ),
            manual =
                RadioButtonState(
                    text = stringRes(R.string.serverSetup_manual),
                    subtitle =
                        if (local.mode == ServerConnectionMode.MANUAL && isTesting) {
                            stringRes(R.string.serverSetup_performingTest)
                        } else {
                            null
                        },
                    isChecked = local.mode == ServerConnectionMode.MANUAL,
                    onClick = ::onManualModeClicked,
                    hapticFeedbackType =
                        if (local.mode == ServerConnectionMode.MANUAL) null else HapticFeedbackType.SegmentTick
                ),
            privateBadge =
                when {
                    persisted.mode != ServerConnectionMode.PRIVATE -> {
                        null
                    }

                    isOhttpUnavailable -> {
                        ModeBadgeState(stringRes(R.string.serverSetup_unavailable), ModeBadgeStyle.ERROR)
                    }

                    else -> {
                        ModeBadgeState(stringRes(R.string.serverSetup_active), ModeBadgeStyle.SUCCESS)
                    }
                },
            publicBadge =
                when {
                    local.mode == ServerConnectionMode.PUBLIC && isTesting -> {
                        testingBadge
                    }

                    persisted.mode == ServerConnectionMode.PUBLIC -> {
                        ModeBadgeState(stringRes(R.string.serverSetup_active), ModeBadgeStyle.SUCCESS)
                    }

                    else -> {
                        null
                    }
                },
            manualBadge = if (local.mode == ServerConnectionMode.MANUAL && isTesting) testingBadge else null,
            onPrivateInfoClick = ::onPrivateInfoClicked,
        )
    }

    private fun createFastest(
        local: InnerState.Local,
        persisted: InnerState.Persisted,
        fastestServers: FastestServersState,
    ): ServerListState.Fastest =
        ServerListState.Fastest(
            title = stringRes(R.string.serverSetup_fastestServers),
            servers =
                fastestServers.servers
                    ?.filterNot { isGateway(it) }
                    ?.map { endpoint ->
                        createDefaultServerState(
                            endpoint = endpoint,
                            local = local,
                            persistedEndpoint = persisted.endpoint,
                            isManualSelected = local.mode == ServerConnectionMode.MANUAL
                        )
                    }.orEmpty(),
            isLoading = fastestServers.isLoading,
            retryButton =
                ButtonState(
                    text =
                        if (preselectFastestClearnet && !autoStartProbing && fastestServers.servers == null) {
                            stringRes(R.string.serverSetup_measure_servers)
                        } else {
                            stringRes(R.string.serverSetup_refresh)
                        },
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
                if (fastestServers.servers?.filterNot { isGateway(it) }.isNullOrEmpty()) {
                    stringRes(R.string.serverSetup_allServers)
                } else {
                    stringRes(R.string.serverSetup_otherServers)
                },
            servers =
                innerState.value.availableServers
                    // The Zodl OHTTP gateway is chosen with the Private radio, not from the Manual list.
                    .filterNot { isGateway(it) }
                    .filter { !fastestServers.servers.orEmpty().contains(it) }
                    .map<LightWalletEndpoint, ServerState> { endpoint ->
                        createDefaultServerState(
                            endpoint = endpoint,
                            local = local,
                            persistedEndpoint = persistedEndpoint,
                            isManualSelected = local.mode == ServerConnectionMode.MANUAL
                        )
                    }.toMutableList()
                    .apply {
                        val index = 1.coerceIn(0, size.coerceAtLeast(0))
                        add(index, customEndpointState)
                    }.toList()
        )
    }

    private fun createSaveButton(
        local: InnerState.Local,
        persisted: InnerState.Persisted,
        fastestServers: FastestServersState,
    ): ButtonState {
        val isSaveInProgress = local.isSaveInProgress
        return ButtonState(
            text =
                if (isSaveInProgress) {
                    stringRes(R.string.choose_server_saving)
                } else {
                    stringRes(R.string.serverSetup_save)
                },
            // Public is still being tested: Save waits for the test result (Figma "Public (Testing)").
            isEnabled =
                !isSaveInProgress &&
                    !(local.mode == ServerConnectionMode.PUBLIC && fastestServers.isLoading) &&
                    hasUnsavedChanges(local, persisted),
            isLoading = isSaveInProgress,
            onClick = ::onSaveButtonClicked,
            hapticFeedbackType = HapticFeedbackType.Confirm
        )
    }

    init {
        viewModelScope.launch {
            val relayIndependence =
                LightWalletEndpointProvider.GATEWAY_HOSTS.associateWith {
                    lightWalletEndpointProvider.usesIndependentRelay(it)
                }
            innerState.update { it.copy(gatewayRelayIndependence = relayIndependence) }
        }

        combine(
            observeServerConnectionMode(),
            getSelectedEndpoint.observe().filterNotNull()
        ) { mode, endpoint -> mode to endpoint }
            .onEach { (mode, endpoint) ->
                innerState.update { inner ->
                    val persisted =
                        InnerState.Persisted(
                            endpoint = endpoint,
                            mode = mode,
                            isCustomEndpoint = resolveIsEndpointCustom(endpoint, inner.availableServers),
                        )
                    inner.copy(
                        persisted = persisted,
                        local = inner.local ?: createInitialLocalState(persisted)
                    )
                }
            }.launchIn(viewModelScope)

        if (preselectFastestClearnet) {
            if (autoStartProbing) {
                refreshFastestServersUseCase()
            }
            // Preselect once per ViewModel: the first finished result with a clearnet candidate. Later
            // emissions (e.g. a refresh) and any selection the user has already made are never overwritten.
            viewModelScope.launch {
                val clearnetEndpoints = lightWalletEndpointProvider.getFallbackEndpoints()
                val fastestClearnet =
                    observeFastestServers()
                        .mapNotNull { fastestServers ->
                            fastestServers.servers
                                ?.takeIf { !fastestServers.isLoading }
                                ?.firstOrNull { it in clearnetEndpoints }
                        }.first()
                innerState.first { it.local != null }
                innerState.update { inner ->
                    inner.local?.let { local ->
                        if (!local.hasUserSelection && local.selectedEndpoint == inner.persisted?.endpoint) {
                            inner.copy(local = local.copy(mode = ServerConnectionMode.MANUAL, selectedEndpoint = fastestClearnet))
                        } else {
                            inner
                        }
                    } ?: inner
                }
            }
        }
    }

    private fun createInitialLocalState(persisted: InnerState.Persisted): InnerState.Local {
        val endpoint = persisted.endpoint
        val isCustom = persisted.isCustomEndpoint
        return InnerState.Local(
            mode = persisted.mode,
            selectedEndpoint = endpoint.takeIf { !isCustom },
            isCustomSelected = persisted.mode == ServerConnectionMode.MANUAL && isCustom,
            isSaveInProgress = false,
            customEndpoint =
                if (isCustom) EndpointTextFieldInnerState.fromEndpoint(endpoint) else EndpointTextFieldInnerState()
        )
    }

    private fun hasUnsavedChanges(local: InnerState.Local, persisted: InnerState.Persisted): Boolean =
        when {
            local.mode != persisted.mode -> {
                true
            }

            local.mode != ServerConnectionMode.MANUAL -> {
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
        val isChecked = local.mode == ServerConnectionMode.MANUAL && local.isCustomSelected
        val isExpanded = isChecked || (local.mode == ServerConnectionMode.MANUAL && isPersistedEndpointCustom)
        return ServerState.Custom(
            radioButtonState =
                RadioButtonState(
                    text =
                        if (isPersistedEndpointCustom) {
                            endpointLabel(persisted.endpoint)
                        } else {
                            stringRes(R.string.serverSetup_custom)
                        },
                    isChecked = isChecked,
                    onClick = ::onCustomEndpointClicked,
                    subtitle =
                        if (isPersistedEndpointCustom && isGateway(persisted.endpoint)) {
                            ohttpSubtitle(persisted.endpoint)
                        } else {
                            stringRes(R.string.serverSetup_customNotPrivate)
                        },
                    hapticFeedbackType = if (isChecked) null else HapticFeedbackType.SegmentTick,
                ),
            newServerTextFieldState =
                EndpointTextFieldState(
                    innerState = local.customEndpoint,
                    isEnabled = isEnabled,
                    onValueChange = ::onCustomEndpointInnerStateChanged,
                ),
            badge = if (isPersistedEndpointCustom) stringRes(R.string.serverSetup_active) else null,
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
                    text = endpointLabel(endpoint),
                    isChecked = isEndpointChecked,
                    onClick = { onEndpointClicked(endpoint) },
                    // Clearnet lightwalletd rows (the automatic fallback tier) are flagged as not private.
                    subtitle =
                        when {
                            endpoint == defaultEndpoint -> ohttpSubtitle(endpoint)
                            else -> stringRes(R.string.serverSetup_clearnetNotPrivate)
                        },
                    hapticFeedbackType = if (isEndpointChecked) null else HapticFeedbackType.SegmentTick,
                ),
            badge = if (endpoint == persistedEndpoint) stringRes(R.string.serverSetup_active) else null,
        )
    }

    /**
     * A gateway reached through a relay its own operator runs (the relay simulator) gives no unlinkability,
     * so it is not labelled private (ZIP #10).
     */
    private fun isGateway(endpoint: LightWalletEndpoint) = lightWalletEndpointProvider.isOhttpGateway(endpoint)

    private fun ohttpSubtitle(endpoint: LightWalletEndpoint) =
        when (innerState.value.gatewayRelayIndependence[endpoint.host]) {
            true -> stringRes(R.string.serverSetup_ohttpPrivate)
            false -> stringRes(R.string.serverSetup_ohttpTestRelayNotPrivate)
            null -> null
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
                        mode = ServerConnectionMode.MANUAL,
                        isCustomSelected = true,
                        selectedEndpoint = null,
                        hasUserSelection = true,
                    )
            )
        }
    }

    private fun onPrivateInfoClicked() = navigationRouter.forward(OhttpInfoArgs)

    private fun onPrivateModeClicked() = onAutomaticModeClicked(ServerConnectionMode.PRIVATE)

    private fun onPublicModeClicked() = onAutomaticModeClicked(ServerConnectionMode.PUBLIC)

    private fun onAutomaticModeClicked(mode: ServerConnectionMode) {
        if (!canUpdateSelection()) return
        innerState.update { inner ->
            inner.copy(local = inner.requireLocal().copy(mode = mode, hasUserSelection = true))
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
                        mode = ServerConnectionMode.MANUAL,
                        selectedEndpoint = if (!pickCustom) persisted.endpoint else null,
                        isCustomSelected = pickCustom,
                        hasUserSelection = true
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
                        mode = ServerConnectionMode.MANUAL,
                        selectedEndpoint = endpoint,
                        isCustomSelected = false,
                        hasUserSelection = true,
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
                        mode = ServerConnectionMode.MANUAL,
                        isCustomSelected = true,
                        selectedEndpoint = null,
                        hasUserSelection = true,
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
                if (local.mode == ServerConnectionMode.PRIVATE) {
                    persistServerSelection.persistPrivate()
                } else if (local.mode == ServerConnectionMode.PUBLIC) {
                    persistServerSelection.persistPublic()
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
                                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                                    onClick = ::onConfirmDialogButtonClicked
                                ),
                        ),
                        reason = reason?.let { stringRes(it) }
                    )
            )
        }
    }
}

/**
 * The name shown for [endpoint] in the server picker. The Zodl OHTTP gateways get a product name: their
 * host is a logical identifier the SDK routes over OHTTP, not an address the user connects to.
 */
private fun endpointLabel(endpoint: LightWalletEndpoint) =
    when {
        LightWalletEndpointProvider.isOhttpGatewayHost(endpoint.host) -> {
            if (endpoint.host.lowercase().contains("testnet")) {
                stringRes(R.string.choose_server_ohttp_gateway_testnet)
            } else {
                stringRes(R.string.choose_server_ohttp_gateway_mainnet)
            }
        }

        else -> {
            stringRes(R.string.choose_server_full_server_name, endpoint.host, endpoint.port)
        }
    }

private data class InnerState(
    val availableServers: List<LightWalletEndpoint>,
    val local: Local? = null,
    val persisted: Persisted? = null,
    val dialogState: ServerDialogState? = null,
    /**
     * Per gateway host, whether this build reaches it through an independent relay (false: a relay the
     * gateway operator runs, e.g. the relay simulator). Absent until known: no privacy label meanwhile.
     */
    val gatewayRelayIndependence: Map<String, Boolean> = emptyMap(),
) {
    fun requireLocal(): Local = requireNotNull(local) { "Local state has not been initialized" }

    fun requirePersisted(): Persisted = requireNotNull(persisted) { "Persisted state has not been initialized" }

    data class Local(
        val mode: ServerConnectionMode,
        val selectedEndpoint: LightWalletEndpoint?,
        val isCustomSelected: Boolean,
        val isSaveInProgress: Boolean,
        val customEndpoint: EndpointTextFieldInnerState,
        val hasUserSelection: Boolean = false,
    )

    data class Persisted(
        val endpoint: LightWalletEndpoint,
        val mode: ServerConnectionMode,
        val isCustomEndpoint: Boolean,
    )
}
