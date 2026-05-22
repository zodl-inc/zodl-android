package co.electriccoin.zcash.ui.screen.chooseserver

import android.app.Application
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.GetServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistEndpointException
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateEndpointUseCase
import co.electriccoin.zcash.ui.design.component.AlertDialogState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions", "LongParameterList")
class ChooseServerVM(
    application: Application,
    observeFastestServers: ObserveFastestServersUseCase,
    getServerSelection: GetServerSelectionUseCase,
    private val getSelectedEndpoint: GetSelectedEndpointUseCase,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val refreshFastestServersUseCase: RefreshFastestServersUseCase,
    private val persistServerSelection: PersistServerSelectionUseCase,
    private val validateEndpoint: ValidateEndpointUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val innerState = MutableStateFlow(InnerState())

    private val availableServers = lightWalletEndpointProvider.getEndpoints()

    private val connectionMode =
        combine(
            getSelectedEndpoint.observe(),
            observeFastestServers(),
            innerState
        ) { persistedEndpoint, fastestServers, inner ->
            val effective = inner.effectiveSelection
            val isAutomatic = effective is Selection.Automatic
            val isManual = effective is Selection.Manual
            val isSaveInProgress = inner.isSaveInProgress
            val fastestServer = fastestServers.servers?.firstOrNull()
            ServerConnectionModeState(
                automatic =
                    RadioButtonState(
                        text = stringRes(R.string.choose_server_automatic),
                        subtitle =
                            if (isAutomatic && persistedEndpoint != null) {
                                val automaticEndpoint =
                                    if (inner.persistedSelection is Selection.Automatic) {
                                        persistedEndpoint
                                    } else {
                                        fastestServer
                                            ?: persistedEndpoint.takeIf {
                                                lightWalletEndpointProvider.getEndpoints().contains(it)
                                            }
                                            ?: lightWalletEndpointProvider.getDefaultEndpoint()
                                    }

                                stringRes(
                                    R.string.choose_server_full_server_name,
                                    automaticEndpoint.host,
                                    automaticEndpoint.port
                                )
                            } else {
                                null
                            },
                        isChecked = isAutomatic,
                        isEnabled = !isSaveInProgress,
                        onClick = ::onAutomaticModeClicked,
                        hapticFeedbackType = if (isAutomatic) null else HapticFeedbackType.SegmentTick
                    ),
                manual =
                    RadioButtonState(
                        text = stringRes(R.string.choose_server_manual),
                        subtitle =
                            if (isManual && fastestServers.isLoading) {
                                stringRes(R.string.choose_server_loading_title)
                            } else {
                                null
                            },
                        isChecked = isManual,
                        isEnabled = !isSaveInProgress,
                        onClick = ::onManualModeClicked,
                        hapticFeedbackType = if (isManual) null else HapticFeedbackType.SegmentTick
                    ),
                automaticBadge =
                    if (fastestServers.isLoading) {
                        stringRes(R.string.choose_server_testing)
                    } else {
                        null
                    }
            )
        }

    private val fastest =
        combine(
            getSelectedEndpoint.observe(),
            observeFastestServers(),
            innerState,
        ) { selectedEndpoint, fastestServers, inner ->
            val isManual = inner.effectiveSelection is Selection.Manual
            val isSaveInProgress = inner.isSaveInProgress
            ServerListState.Fastest(
                title = stringRes(R.string.choose_server_fastest_servers),
                servers =
                    fastestServers.servers
                        ?.map { endpoint ->
                            createDefaultServerState(
                                endpoint = endpoint,
                                userSelection = inner.userSelection,
                                selectedEndpoint = selectedEndpoint,
                                isManualSelected = isManual,
                                isEnabled = !isSaveInProgress
                            )
                        }.orEmpty(),
                isLoading = fastestServers.isLoading,
                retryButton =
                    ButtonState(
                        text = stringRes(R.string.choose_server_refresh),
                        isEnabled = !isSaveInProgress,
                        onClick = ::onRefreshClicked
                    )
            )
        }

    private val other =
        combine(
            getSelectedEndpoint.observe(),
            observeFastestServers(),
            innerState
        ) { selectedEndpoint, fastest, inner ->
            if (selectedEndpoint == null) return@combine null

            val isSelectedEndpointCustom = isCustomEndpoint(selectedEndpoint)
            val isManual = inner.effectiveSelection is Selection.Manual
            val isEnabled = !inner.isSaveInProgress

            val customEndpointState =
                createCustomServerState(
                    userSelection = inner.userSelection,
                    isSelectedEndpointCustom = isSelectedEndpointCustom,
                    userCustomEndpointText = inner.customEndpointText,
                    selectedEndpoint = selectedEndpoint,
                    isCustomEndpointExpanded = inner.isCustomEndpointExpanded || (isManual && isSelectedEndpointCustom),
                    isManualSelected = isManual,
                    isEnabled = isEnabled
                )

            ServerListState.Other(
                title =
                    if (fastest.servers.isNullOrEmpty()) {
                        stringRes(R.string.choose_server_browse_servers)
                    } else {
                        stringRes(R.string.choose_server_other_servers)
                    },
                servers =
                    availableServers
                        .filter {
                            !fastest.servers.orEmpty().contains(it)
                        }.map<LightWalletEndpoint, ServerState> { endpoint ->
                            createDefaultServerState(
                                endpoint = endpoint,
                                userSelection = inner.userSelection,
                                selectedEndpoint = selectedEndpoint,
                                isManualSelected = isManual,
                                isEnabled = isEnabled
                            )
                        }.toMutableList()
                        .apply {
                            val index = 1.coerceIn(0, size.coerceAtLeast(0))
                            add(index, customEndpointState)
                        }.toList()
            )
        }

    private val buttonState =
        combine(getSelectedEndpoint.observe(), innerState) { selectedEndpoint, inner ->
            val user = inner.userSelection
            val persistedSelection = inner.persistedSelection
            val effective = inner.effectiveSelection
            val isManual = effective is Selection.Manual

            val userSelectedEndpoint =
                when (user) {
                    is Selection.Manual.Endpoint -> user.endpoint
                    Selection.Manual.Custom -> if (isCustomEndpoint(selectedEndpoint)) selectedEndpoint else null
                    Selection.Automatic, null -> null
                }

            val isCustomEndpointSelectedAndUpdated =
                if (user is Selection.Manual.Custom && isCustomEndpoint(selectedEndpoint)) {
                    val typedEndpoint = inner.customEndpointText?.let { validateEndpoint(it) }
                    typedEndpoint != null && typedEndpoint != selectedEndpoint
                } else {
                    false
                }

            val modeChanged = (effective is Selection.Automatic) != (persistedSelection is Selection.Automatic)
            val endpointChanged = isManual && user != null && selectedEndpoint != userSelectedEndpoint
            val hasUnsavedSelection = modeChanged || endpointChanged || isCustomEndpointSelectedAndUpdated

            ButtonState(
                text =
                    if (inner.isSaveInProgress) {
                        stringRes(R.string.choose_server_saving)
                    } else {
                        stringRes(R.string.choose_server_save)
                    },
                isEnabled = !inner.isSaveInProgress && hasUnsavedSelection,
                isLoading = inner.isSaveInProgress,
                onClick = ::onSaveButtonClicked,
                hapticFeedbackType = HapticFeedbackType.Confirm
            )
        }

    val state =
        combine(connectionMode, fastest, other, buttonState, innerState) {
            connectionMode,
            fastest,
            other,
            buttonState,
            inner
            ->
            if (other == null || inner.persistedSelection == null) return@combine null

            ChooseServerState(
                connectionMode = connectionMode,
                fastest = fastest,
                other = other,
                saveButton = buttonState,
                dialogState = inner.dialogState,
                onBack = ::onBack
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT), null)

    init {
        viewModelScope.launch {
            getServerSelection.observe().collect { persisted ->
                innerState.update { it.copy(persistedSelection = persisted.toSelection()) }
            }
        }
    }

    private fun onBack() {
        if (canUpdateSelection()) {
            navigationRouter.back()
        }
    }

    private fun createCustomServerState(
        userSelection: Selection?,
        isSelectedEndpointCustom: Boolean,
        userCustomEndpointText: String?,
        selectedEndpoint: LightWalletEndpoint,
        isCustomEndpointExpanded: Boolean,
        isManualSelected: Boolean,
        isEnabled: Boolean,
    ): ServerState.Custom {
        val isChecked =
            isManualSelected &&
                (userSelection is Selection.Manual.Custom || (userSelection == null && isSelectedEndpointCustom))
        return ServerState.Custom(
            radioButtonState =
                RadioButtonState(
                    text =
                        if (isSelectedEndpointCustom) {
                            stringRes(
                                R.string.choose_server_full_server_name,
                                selectedEndpoint.host,
                                selectedEndpoint.port,
                            )
                        } else {
                            stringRes(R.string.choose_server_custom)
                        },
                    isChecked = isChecked,
                    isEnabled = isEnabled,
                    onClick = ::onCustomEndpointClicked,
                    hapticFeedbackType = if (isChecked) null else HapticFeedbackType.SegmentTick,
                ),
            newServerTextFieldState =
                TextFieldState(
                    value =
                        userCustomEndpointText?.let { stringRes(it) } ?: if (isSelectedEndpointCustom) {
                            stringRes(
                                resource = R.string.choose_server_full_server_name_text_field,
                                selectedEndpoint.host,
                                selectedEndpoint.port
                            )
                        } else {
                            stringRes("")
                        },
                    isEnabled = isEnabled,
                    onValueChange = ::onCustomEndpointTextChanged,
                ),
            badge = if (isSelectedEndpointCustom) stringRes(R.string.choose_server_active) else null,
            isExpanded = isCustomEndpointExpanded,
            key = "custom",
        )
    }

    private fun createDefaultServerState(
        endpoint: LightWalletEndpoint,
        userSelection: Selection?,
        selectedEndpoint: LightWalletEndpoint?,
        isManualSelected: Boolean,
        isEnabled: Boolean,
    ): ServerState.Default {
        val defaultEndpoint = lightWalletEndpointProvider.getDefaultEndpoint()
        val isEndpointChecked =
            isManualSelected &&
                (
                    (userSelection is Selection.Manual.Endpoint && userSelection.endpoint == endpoint) ||
                        (userSelection == null && selectedEndpoint == endpoint)
                )

        return ServerState.Default(
            key = "default_${endpoint.host}_${endpoint.port}",
            radioButtonState =
                RadioButtonState(
                    text = stringRes(R.string.choose_server_full_server_name, endpoint.host, endpoint.port),
                    isChecked = isEndpointChecked,
                    isEnabled = isEnabled,
                    onClick = { onEndpointClicked(endpoint) },
                    subtitle =
                        if (endpoint == defaultEndpoint) {
                            stringRes(R.string.choose_server_save_default)
                        } else {
                            null
                        },
                    hapticFeedbackType = if (isEndpointChecked) null else HapticFeedbackType.SegmentTick,
                ),
            badge = if (endpoint == selectedEndpoint) stringRes(R.string.choose_server_active) else null,
        )
    }

    private fun onRefreshClicked() {
        if (!canUpdateSelection()) return
        refreshFastestServersUseCase()
    }

    private fun onCustomEndpointTextChanged(new: String) {
        if (!canUpdateSelection()) return
        innerState.update { it.copy(customEndpointText = new) }
    }

    private fun onAutomaticModeClicked() {
        if (!canUpdateSelection()) return
        innerState.update {
            it.copy(
                isCustomEndpointExpanded = false,
                userSelection = Selection.Automatic
            )
        }
    }

    private fun onManualModeClicked() {
        if (!canUpdateSelection()) return
        viewModelScope.launch {
            val current = getSelectedEndpoint() ?: lightWalletEndpointProvider.getDefaultEndpoint()
            val pick =
                if (availableServers.contains(current)) {
                    Selection.Manual.Endpoint(current)
                } else {
                    Selection.Manual.Custom
                }
            innerState.update { it.copy(userSelection = pick) }
        }
    }

    private fun onEndpointClicked(endpoint: LightWalletEndpoint) {
        if (!canUpdateSelection()) return
        innerState.update {
            it.copy(
                isCustomEndpointExpanded = false,
                userSelection = Selection.Manual.Endpoint(endpoint)
            )
        }
    }

    private fun onCustomEndpointClicked() {
        if (!canUpdateSelection()) return
        innerState.update {
            it.copy(
                isCustomEndpointExpanded = true,
                userSelection = Selection.Manual.Custom
            )
        }
    }

    private fun canUpdateSelection() = !innerState.value.isSaveInProgress

    private fun onSaveButtonClicked() =
        viewModelScope.launch {
            try {
                if (innerState.value.isSaveInProgress) return@launch
                val selection = getUserServerSelectionOrShowError() ?: return@launch
                if (selection !is ServerSelection.Automatic) innerState.update { it.copy(isSaveInProgress = true) }
                persistServerSelection(selection)
                innerState.update {
                    it.copy(
                        isCustomEndpointExpanded = false,
                        userSelection = null,
                        persistedSelection = selection.toSelection()
                    )
                }
            } catch (e: PersistEndpointException) {
                showValidationErrorDialog(e.message)
            } finally {
                innerState.update { it.copy(isSaveInProgress = false) }
            }
        }

    private fun onConfirmDialogButtonClicked() = innerState.update { it.copy(dialogState = null) }

    private fun getUserServerSelectionOrShowError(): ServerSelection? {
        fun validateCustomEndpointOrShowError(): LightWalletEndpoint? {
            val typed = innerState.value.customEndpointText
            val validated = validateEndpoint(typed.orEmpty())
            if (validated == null) showValidationErrorDialog()
            return validated
        }

        return when (val user = innerState.value.userSelection) {
            Selection.Automatic -> ServerSelection.Automatic
            Selection.Manual.Custom -> validateCustomEndpointOrShowError()?.let { ServerSelection.Manual(it) }
            is Selection.Manual.Endpoint -> ServerSelection.Manual(user.endpoint)
            null -> null
        }
    }

    private fun isCustomEndpoint(selected: LightWalletEndpoint?): Boolean =
        selected != null && !availableServers.contains(selected)

    private fun ServerSelection.toSelection(): Selection =
        when (this) {
            ServerSelection.Automatic -> {
                Selection.Automatic
            }

            is ServerSelection.Manual -> {
                if (availableServers.contains(endpoint)) {
                    Selection.Manual.Endpoint(endpoint)
                } else {
                    Selection.Manual.Custom
                }
            }
        }

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

private sealed interface Selection {
    data object Automatic : Selection

    sealed interface Manual : Selection {
        data object Custom : Manual

        data class Endpoint(
            val endpoint: LightWalletEndpoint
        ) : Manual
    }
}

private data class InnerState(
    val customEndpointText: String? = null,
    val userSelection: Selection? = null,
    val persistedSelection: Selection? = null,
    val isCustomEndpointExpanded: Boolean = false,
    val isSaveInProgress: Boolean = false,
    val dialogState: ServerDialogState? = null,
) {
    val effectiveSelection: Selection? = userSelection ?: persistedSelection
}
