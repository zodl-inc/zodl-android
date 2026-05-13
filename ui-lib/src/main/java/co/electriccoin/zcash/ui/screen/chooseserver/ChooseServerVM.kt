package co.electriccoin.zcash.ui.screen.chooseserver

import android.app.Application
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.ConnectionMode
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
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions", "LongParameterList")
class ChooseServerVM(
    application: Application,
    observeFastestServers: ObserveFastestServersUseCase,
    private val getSelectedEndpoint: GetSelectedEndpointUseCase,
    private val getServerSelection: GetServerSelectionUseCase,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val refreshFastestServersUseCase: RefreshFastestServersUseCase,
    private val persistServerSelection: PersistServerSelectionUseCase,
    private val validateEndpoint: ValidateEndpointUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val userCustomEndpointText = MutableStateFlow<String?>(null)

    private val userEndpointSelection = MutableStateFlow<Selection?>(null)

    private val userModeSelection = MutableStateFlow<ConnectionMode?>(null)

    private val isSaveInProgress = MutableStateFlow(false)

    private val dialogState = MutableStateFlow<ServerDialogState?>(null)

    private val isCustomEndpointExpanded = MutableStateFlow(false)

    private val availableServers by lazy(LazyThreadSafetyMode.NONE) { lightWalletEndpointProvider.getEndpoints() }

    private val selectedMode =
        combine(
            getServerSelection.observe(),
            userModeSelection
        ) { persistedServerSelection, userModeSelection ->
            userModeSelection ?: persistedServerSelection.mode
        }

    private val endpointUiSelection =
        combine(
            userCustomEndpointText,
            userEndpointSelection,
            isCustomEndpointExpanded
        ) { customEndpointText, endpointSelection, isCustomEndpointExpanded ->
            EndpointUiSelection(
                customEndpointText = customEndpointText,
                endpointSelection = endpointSelection,
                isCustomEndpointExpanded = isCustomEndpointExpanded
            )
        }

    private val saveButtonInput =
        combine(
            userEndpointSelection,
            userModeSelection,
            isSaveInProgress,
            userCustomEndpointText
        ) { endpointSelection, modeSelection, isSaveInProgress, customEndpointText ->
            SaveButtonInput(
                endpointSelection = endpointSelection,
                modeSelection = modeSelection,
                isSaveInProgress = isSaveInProgress,
                customEndpointText = customEndpointText
            )
        }

    private val connectionMode =
        combine(
            selectedMode,
            getSelectedEndpoint.observe(),
            observeFastestServers()
        ) { selectedMode, selectedEndpoint, fastestServers ->
            val isAutomatic = selectedMode == ConnectionMode.AUTOMATIC
            ServerConnectionModeState(
                automatic =
                    RadioButtonState(
                        text = stringRes(R.string.choose_server_automatic),
                        subtitle =
                            if (isAutomatic && selectedEndpoint != null) {
                                stringRes(
                                    R.string.choose_server_full_server_name,
                                    selectedEndpoint.host,
                                    selectedEndpoint.port
                                )
                            } else {
                                null
                            },
                        isChecked = isAutomatic,
                        onClick = ::onAutomaticModeClicked,
                        hapticFeedbackType =
                            if (isAutomatic) {
                                null
                            } else {
                                HapticFeedbackType.SegmentTick
                            }
                    ),
                manual =
                    RadioButtonState(
                        text = stringRes(R.string.choose_server_manual),
                        isChecked = selectedMode == ConnectionMode.MANUAL,
                        onClick = ::onManualModeClicked,
                        hapticFeedbackType =
                            if (selectedMode == ConnectionMode.MANUAL) {
                                null
                            } else {
                                HapticFeedbackType.SegmentTick
                            }
                    ),
                automaticBadge =
                    if (isAutomatic && fastestServers.isLoading) {
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
            userEndpointSelection,
            selectedMode,
        ) { selectedEndpoint, fastestServers, userEndpointSelection, selectedMode ->
            ServerListState.Fastest(
                title = stringRes(R.string.choose_server_fastest_servers),
                servers =
                    fastestServers.servers
                        ?.map { endpoint ->
                            createDefaultServerState(
                                endpoint = endpoint,
                                userEndpointSelection = userEndpointSelection,
                                selectedEndpoint = selectedEndpoint,
                                selectedMode = selectedMode
                            )
                        }.orEmpty(),
                isLoading = fastestServers.isLoading,
                retryButton =
                    ButtonState(
                        text = stringRes(R.string.choose_server_refresh),
                        onClick = ::onRefreshClicked
                    )
            )
        }

    private val other =
        combine(
            getSelectedEndpoint.observe(),
            observeFastestServers(),
            getServerSelection.observe(),
            endpointUiSelection,
            selectedMode
        ) { selectedEndpoint, fastest, persistedServerSelection, endpointUiSelection, selectedMode ->
            if (selectedEndpoint == null) return@combine null

            val isSelectedEndpointCustom = isCustomEndpoint(selectedEndpoint, persistedServerSelection)

            val customEndpointState =
                createCustomServerState(
                    userEndpointSelection = endpointUiSelection.endpointSelection,
                    isSelectedEndpointCustom = isSelectedEndpointCustom,
                    userCustomEndpointText = endpointUiSelection.customEndpointText,
                    selectedEndpoint = selectedEndpoint,
                    isCustomEndpointExpanded =
                        endpointUiSelection.isCustomEndpointExpanded ||
                            (selectedMode == ConnectionMode.MANUAL && isSelectedEndpointCustom),
                    selectedMode = selectedMode
                )

            ServerListState.Other(
                title = stringRes(R.string.choose_server_other_servers),
                servers =
                    availableServers
                        .filter {
                            !fastest.servers.orEmpty().contains(it)
                        }.map<LightWalletEndpoint, ServerState> { endpoint ->
                            createDefaultServerState(
                                endpoint = endpoint,
                                userEndpointSelection = endpointUiSelection.endpointSelection,
                                selectedEndpoint = selectedEndpoint,
                                selectedMode = selectedMode
                            )
                        }.toMutableList()
                        .apply {
                            val index = 1.coerceIn(0, size.coerceAtLeast(0))
                            add(index, customEndpointState)
                        }.toList()
            )
        }

    private val buttonState =
        combine(
            getSelectedEndpoint.observe(),
            getServerSelection.observe(),
            saveButtonInput
        ) { selectedEndpoint, persistedServerSelection, saveButtonInput ->
            val selectedMode = saveButtonInput.modeSelection ?: persistedServerSelection.mode
            val userSelectedEndpoint =
                when (saveButtonInput.endpointSelection) {
                    Selection.Custom -> {
                        val isSelectedEndpointCustom = isCustomEndpoint(selectedEndpoint, persistedServerSelection)
                        if (isSelectedEndpointCustom) selectedEndpoint else null
                    }

                    is Selection.Endpoint -> {
                        saveButtonInput.endpointSelection.endpoint
                    }

                    null -> {
                        if (selectedMode == ConnectionMode.MANUAL) {
                            selectedEndpoint
                        } else {
                            null
                        }
                    }
                }

            val isCustomEndpointSelectedAndUpdated =
                when (saveButtonInput.endpointSelection) {
                    Selection.Custom -> {
                        val isSelectedEndpointCustom = isCustomEndpoint(selectedEndpoint, persistedServerSelection)
                        when {
                            isSelectedEndpointCustom && saveButtonInput.customEndpointText == null -> false

                            isSelectedEndpointCustom &&
                                selectedEndpoint?.generateUserString() !=
                                saveButtonInput.customEndpointText -> true

                            else -> false
                        }
                    }

                    is Selection.Endpoint -> {
                        false
                    }

                    null -> {
                        false
                    }
                }

            val hasUnsavedSelection =
                selectedMode != persistedServerSelection.mode ||
                    (
                        selectedMode == ConnectionMode.MANUAL &&
                            saveButtonInput.endpointSelection != null &&
                            selectedEndpoint != userSelectedEndpoint
                    ) ||
                    isCustomEndpointSelectedAndUpdated

            ButtonState(
                text =
                    if (saveButtonInput.isSaveInProgress) {
                        stringRes(R.string.choose_server_saving)
                    } else {
                        stringRes(R.string.choose_server_save)
                    },
                isEnabled =
                    !saveButtonInput.isSaveInProgress &&
                        hasUnsavedSelection,
                isLoading = saveButtonInput.isSaveInProgress,
                onClick = ::onSaveButtonClicked,
                hapticFeedbackType = HapticFeedbackType.Confirm
            )
        }

    val state =
        combine(connectionMode, fastest, other, buttonState, dialogState) {
            connectionMode,
            fastest,
            other,
            buttonState,
            dialogState
            ->
            if (other == null) { // not loaded yet
                return@combine null
            }

            ChooseServerState(
                connectionMode = connectionMode,
                fastest = fastest,
                other = other,
                saveButton = buttonState,
                dialogState = dialogState,
                onBack = ::onBack
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT), null)

    private fun onBack() {
        val canGoBack = isSaveInProgress.value.not()
        if (canGoBack) {
            navigationRouter.back()
        }
    }

    private fun createCustomServerState(
        userEndpointSelection: Selection?,
        isSelectedEndpointCustom: Boolean,
        userCustomEndpointText: String?,
        selectedEndpoint: LightWalletEndpoint,
        isCustomEndpointExpanded: Boolean,
        selectedMode: ConnectionMode,
    ): ServerState.Custom {
        val isChecked =
            selectedMode == ConnectionMode.MANUAL &&
                (
                    userEndpointSelection is Selection.Custom ||
                        (userEndpointSelection == null && isSelectedEndpointCustom)
                )
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
                    onValueChange = ::onCustomEndpointTextChanged,
                ),
            badge = if (isSelectedEndpointCustom) stringRes(R.string.choose_server_active) else null,
            isExpanded = isCustomEndpointExpanded,
            key = "custom",
        )
    }

    private fun createDefaultServerState(
        endpoint: LightWalletEndpoint,
        userEndpointSelection: Selection?,
        selectedEndpoint: LightWalletEndpoint?,
        selectedMode: ConnectionMode,
    ): ServerState.Default {
        val defaultEndpoint = lightWalletEndpointProvider.getDefaultEndpoint()
        val isEndpointChecked =
            selectedMode == ConnectionMode.MANUAL &&
                (
                    (userEndpointSelection is Selection.Endpoint && userEndpointSelection.endpoint == endpoint) ||
                        (userEndpointSelection == null && selectedEndpoint == endpoint)
                )

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
            badge = if (endpoint == selectedEndpoint) stringRes(R.string.choose_server_active) else null,
        )
    }

    private fun onRefreshClicked() {
        refreshFastestServersUseCase()
    }

    private fun onCustomEndpointTextChanged(new: String) {
        this.userCustomEndpointText.update { new }
    }

    private fun onAutomaticModeClicked() {
        isCustomEndpointExpanded.update { false }
        userEndpointSelection.update { null }
        userModeSelection.update { ConnectionMode.AUTOMATIC }
    }

    private fun onManualModeClicked() {
        userModeSelection.update { ConnectionMode.MANUAL }
    }

    private fun onEndpointClicked(endpoint: LightWalletEndpoint) {
        isCustomEndpointExpanded.update { false }
        userModeSelection.update { ConnectionMode.MANUAL }
        userEndpointSelection.update { Selection.Endpoint(endpoint) }
    }

    private fun onCustomEndpointClicked() {
        isCustomEndpointExpanded.update { true }
        userModeSelection.update { ConnectionMode.MANUAL }
        userEndpointSelection.update { Selection.Custom }
    }

    private fun onSaveButtonClicked() =
        viewModelScope.launch {
            try {
                if (isSaveInProgress.value) return@launch
                isSaveInProgress.update { true }
                val selection = getUserServerSelectionOrShowError() ?: return@launch
                persistServerSelection(selection)
                isCustomEndpointExpanded.update { false }
                userEndpointSelection.update { null }
                userModeSelection.update { null }
            } catch (e: PersistEndpointException) {
                showValidationErrorDialog(e.message)
            } finally {
                isSaveInProgress.update { false }
            }
        }

    private fun onConfirmDialogButtonClicked() {
        dialogState.update { null }
    }

    /**
     * @return the server selection requested by the user, or null if the selected custom endpoint is invalid.
     */
    private suspend fun getUserServerSelectionOrShowError(): ServerSelection? {
        val persistedSelection = getServerSelection()
        return when (userModeSelection.value ?: persistedSelection.mode) {
            ConnectionMode.AUTOMATIC -> {
                ServerSelection.automatic()
            }

            ConnectionMode.MANUAL -> {
                val selectedEndpoint = userEndpointSelection.value
                val endpoint =
                    when (selectedEndpoint) {
                        Selection.Custom -> getUserEndpointSelectionOrShowError() ?: return null
                        is Selection.Endpoint -> getUserEndpointSelectionOrShowError()
                        null -> getSelectedEndpoint()
                    } ?: run {
                        showValidationErrorDialog(null)
                        return null
                    }

                ServerSelection.manual(
                    endpoint = endpoint,
                    isCustom =
                        when (selectedEndpoint) {
                            Selection.Custom,
                            is Selection.Endpoint -> {
                                !availableServers.contains(endpoint)
                            }

                            null -> {
                                if (persistedSelection.endpoint == endpoint) {
                                    persistedSelection.isCustom || !availableServers.contains(endpoint)
                                } else {
                                    !availableServers.contains(endpoint)
                                }
                            }
                        }
                )
            }
        }
    }

    private fun isCustomEndpoint(
        endpoint: LightWalletEndpoint?,
        persistedSelection: ServerSelection
    ) = endpoint != null &&
        (
            !availableServers.contains(endpoint) ||
                (
                    persistedSelection.mode == ConnectionMode.MANUAL &&
                        persistedSelection.endpoint == endpoint &&
                        persistedSelection.isCustom
                )
        )

    private fun getUserEndpointSelectionOrShowError(): LightWalletEndpoint? =
        when (val selection = userEndpointSelection.value) {
            is Selection.Custom -> {
                val endpoint = userCustomEndpointText.value
                val validated = validateEndpoint(endpoint.orEmpty())
                if (validated == null) {
                    showValidationErrorDialog(null)
                }
                validated
            }

            is Selection.Endpoint -> {
                selection.endpoint
            }

            null -> {
                null
            }
        }

    private fun showValidationErrorDialog(reason: String?) {
        dialogState.update {
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
        }
    }

    private fun LightWalletEndpoint.generateUserString(): String =
        stringRes(resource = R.string.choose_server_full_server_name_text_field, host, port)
            .getString(getApplication())
}

private sealed interface Selection {
    data object Custom : Selection

    data class Endpoint(
        val endpoint: LightWalletEndpoint
    ) : Selection
}

private data class EndpointUiSelection(
    val customEndpointText: String?,
    val endpointSelection: Selection?,
    val isCustomEndpointExpanded: Boolean,
)

private data class SaveButtonInput(
    val endpointSelection: Selection?,
    val modeSelection: ConnectionMode?,
    val isSaveInProgress: Boolean,
    val customEndpointText: String?,
)
