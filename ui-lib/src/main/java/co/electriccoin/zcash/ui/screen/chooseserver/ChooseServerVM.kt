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
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
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
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.chooseserver.ohttpinfo.OhttpInfoArgs
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
    isOhttpFallbackActive: IsOhttpFallbackActiveProvider,
    getSelectedEndpoint: GetSelectedEndpointUseCase,
    observeServerConnectionMode: ObserveServerConnectionModeUseCase,
    automaticServerRepository: AutomaticServerRepository,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val refreshFastestServersUseCase: RefreshFastestServersUseCase,
    private val persistServerSelection: PersistServerSelectionUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val fastestServersFlow = observeFastestServers()

    private val innerState =
        MutableStateFlow(InnerState(availableServers = lightWalletEndpointProvider.getEndpoints()))

    private val innerStateSafe = innerState.filter { it.local != null && it.persisted != null }

    val state =
        combine(
            fastestServersFlow,
            innerStateSafe,
            automaticServerRepository.ohttpUnavailable
        ) { fastest, inner, isOhttpUnavailable ->
            val local = inner.requireLocal()
            val persisted = inner.requirePersisted()
            ChooseServerState(
                connectionMode =
                    createConnectionMode(
                        local = local,
                        persisted = persisted,
                        fastestServers = fastest,
                        isOhttpUnavailable = isOhttpUnavailable,
                        gatewayRelayIndependence = inner.gatewayRelayIndependence,
                    ),
                fastest = createFastest(local, persisted, fastest),
                other = createOther(local, persisted, fastest),
                saveButton = createSaveButton(local, persisted, fastest),
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
        isOhttpUnavailable: Boolean,
        gatewayRelayIndependence: Map<String, Boolean>,
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
                    subtitle = privateSubtitle(gatewayRelayIndependence),
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
                                    fastestPublicServer(fastestServers)
                                        ?: lightWalletEndpointProvider.getFallbackEndpoints().firstOrNull()
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
            privateBadge = createPrivateBadge(persisted, isOhttpUnavailable, gatewayRelayIndependence),
            publicBadge = createPublicBadge(local, persisted, testingBadge, isTesting),
            manualBadge = if (local.mode == ServerConnectionMode.MANUAL && isTesting) testingBadge else null,
            onPrivateInfoClick = ::onPrivateInfoClicked,
        )
    }

    /**
     * The Private mode's badge. A gateway reached through a relay its own operator runs (the relay simulator)
     * is not private, so it never reads "Active", only "Not private", as its subtitle says ([privateSubtitle]).
     */
    private fun createPrivateBadge(
        persisted: InnerState.Persisted,
        isOhttpUnavailable: Boolean,
        gatewayRelayIndependence: Map<String, Boolean>,
    ): ModeBadgeState? =
        when {
            persisted.mode != ServerConnectionMode.PRIVATE -> {
                null
            }

            isOhttpUnavailable -> {
                ModeBadgeState(stringRes(R.string.serverSetup_unavailable), ModeBadgeStyle.ERROR)
            }

            !isRelayIndependent(gatewayRelayIndependence) -> {
                ModeBadgeState(stringRes(R.string.serverSetup_notPrivate), ModeBadgeStyle.WARNING)
            }

            else -> {
                ModeBadgeState(stringRes(R.string.serverSetup_active), ModeBadgeStyle.SUCCESS)
            }
        }

    private fun createPublicBadge(
        local: InnerState.Local,
        persisted: InnerState.Persisted,
        testingBadge: ModeBadgeState,
        isTesting: Boolean,
    ): ModeBadgeState? =
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
                    text = stringRes(R.string.serverSetup_refresh),
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

    /**
     * Save button visibility and state.
     *
     * While Public mode is being tested (speed test still running), save is disabled. The user can only
     * confirm after the test completes or manually pick a server in Manual mode.
     */
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
            // The gateway of the current network is one of the compiled hosts; naming it keeps the answer for it
            // independent of how the SDK was built.
            val currentGatewayHost = lightWalletEndpointProvider.getOhttpEndpoint()?.host
            val relayIndependence =
                (LightWalletEndpointProvider.gatewayHosts + listOfNotNull(currentGatewayHost))
                    .distinct()
                    .associateWith { lightWalletEndpointProvider.usesIndependentRelay(it) }
            innerState.update { it.copy(gatewayRelayIndependence = relayIndependence) }
        }

        combine(
            observeServerConnectionMode(),
            getSelectedEndpoint.observe().filterNotNull(),
            isOhttpFallbackActive.observe(),
        ) { mode, endpoint, isFallback -> Triple(mode, endpoint, isFallback) }
            .onEach { (mode, endpoint, isFallback) ->
                innerState.update { inner ->
                    val persisted =
                        InnerState.Persisted(
                            endpoint = endpoint,
                            mode = mode,
                            isOutageSwitch = mode == ServerConnectionMode.PUBLIC && isFallback,
                            isCustomEndpoint = resolveIsEndpointCustom(endpoint, inner.availableServers),
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
            mode = persisted.mode,
            selectedEndpoint = endpoint.takeIf { !isCustom },
            isCustomSelected = persisted.mode == ServerConnectionMode.MANUAL && isCustom,
            isSaveInProgress = false,
            customEndpoint =
                if (isCustom) EndpointTextFieldInnerState.fromEndpoint(endpoint) else EndpointTextFieldInnerState()
        )
    }

    /**
     * Determines whether the current local state differs from the persisted state.
     *
     * For Manual mode, a server must be selected (either from the list or custom). Immediately after
     * switching to Manual from Private, nothing is selected yet.
     */
    private fun hasUnsavedChanges(local: InnerState.Local, persisted: InnerState.Persisted): Boolean {
        val manualEndpoint = if (local.isCustomSelected) local.customEndpoint.endpoint else local.selectedEndpoint
        return when {
            local.mode == ServerConnectionMode.MANUAL -> {
                manualEndpoint != null &&
                    (persisted.mode != ServerConnectionMode.MANUAL || manualEndpoint != persisted.endpoint)
            }

            local.mode != persisted.mode -> {
                true
            }

            else -> {
                local.mode == ServerConnectionMode.PUBLIC && persisted.isOutageSwitch
            }
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
                    subtitle = stringRes(R.string.serverSetup_customNotPrivate),
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
        val isEndpointChecked =
            isManualSelected && !local.isCustomSelected && local.selectedEndpoint == endpoint
        return ServerState.Default(
            key = "default_${endpoint.host}_${endpoint.port}",
            radioButtonState =
                RadioButtonState(
                    text = endpointLabel(endpoint),
                    isChecked = isEndpointChecked,
                    onClick = { onEndpointClicked(endpoint) },
                    subtitle = stringRes(R.string.serverSetup_clearnetNotPrivate),
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

    /**
     * "ZODL servers", unless this build reaches the gateway through a relay the gateway operator also runs (the
     * relay simulator), which gives no unlinkability, so it must not read as private.
     */
    private fun privateSubtitle(gatewayRelayIndependence: Map<String, Boolean>): StringResource =
        if (isRelayIndependent(gatewayRelayIndependence)) {
            stringRes(R.string.serverSetup_privateSubtitle)
        } else {
            stringRes(R.string.serverSetup_ohttpTestRelayNotPrivate)
        }

    /** False only when this build's relay for the default gateway is known to be run by the gateway operator. */
    private fun isRelayIndependent(gatewayRelayIndependence: Map<String, Boolean>): Boolean {
        // The gateway host comes from the compiled SDK; a build without a gateway has no relay to judge.
        val gatewayHost = lightWalletEndpointProvider.getOhttpEndpoint()?.host
        return gatewayHost?.let { gatewayRelayIndependence[it] } != false
    }

    /** The public server a finished speed test ranked first, if any. */
    private fun fastestPublicServer(state: FastestServersState): LightWalletEndpoint? =
        state.servers
            ?.takeIf { !state.isLoading }
            ?.firstOrNull { !isGateway(it) }

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
            inner.copy(local = inner.requireLocal().copy(mode = mode))
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
                        selectedEndpoint = persisted.endpoint.takeIf { !pickCustom && !isGateway(it) },
                        isCustomSelected = pickCustom,
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
                    )
            )
        }
    }

    private fun canUpdateSelection(): Boolean {
        val local = innerState.value.local ?: return false
        return !local.isSaveInProgress
    }

    /**
     * Saves the selected mode. Confirming Public as a deliberate choice (not an outage fallback) keeps the current
     * endpoint rather than switching to the fastest one: changing servers rebuilds the Synchronizer, which is
     * unnecessary here.
     */
    private fun onSaveButtonClicked() =
        viewModelScope.launch {
            val local = innerState.value.requireLocal()
            if (local.isSaveInProgress) return@launch
            innerState.update { it.copy(local = it.requireLocal().copy(isSaveInProgress = true)) }
            try {
                if (local.mode == ServerConnectionMode.PRIVATE) {
                    persistServerSelection.persistPrivate()
                } else if (local.mode == ServerConnectionMode.PUBLIC) {
                    val persisted = innerState.value.requirePersisted()
                    persistServerSelection.persistPublic(
                        fastest =
                            persisted.endpoint.takeIf { persisted.mode == ServerConnectionMode.PUBLIC }
                                ?: fastestPublicServer(fastestServersFlow.value)
                    )
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
            if (LightWalletEndpointProvider.isTestnetGatewayHost(endpoint.host)) {
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
    )

    data class Persisted(
        val endpoint: LightWalletEndpoint,
        val mode: ServerConnectionMode,
        /** Public entered from the "private connection unavailable" prompt, so it is still a temporary switch. */
        val isOutageSwitch: Boolean,
        val isCustomEndpoint: Boolean,
    )
}
