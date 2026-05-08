package co.electriccoin.zcash.ui.screen.chooseserver

import co.electriccoin.zcash.ui.design.component.AlertDialogState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.Itemizable
import co.electriccoin.zcash.ui.design.util.StringResource

data class ChooseServerState(
    val connectionMode: ServerConnectionModeState,
    val fastest: ServerListState.Fastest,
    val other: ServerListState.Other,
    val saveButton: ButtonState,
    val dialogState: ServerDialogState?,
    val onBack: () -> Unit
)

data class ServerConnectionModeState(
    val automatic: RadioButtonState,
    val manual: RadioButtonState,
    val automaticBadge: StringResource? = null
) {
    val isManualSelected = manual.isChecked
}

sealed interface ServerListState {
    val title: StringResource
    val servers: List<ServerState>

    data class Other(
        override val title: StringResource,
        override val servers: List<ServerState>,
    ) : ServerListState

    data class Fastest(
        override val title: StringResource,
        override val servers: List<ServerState.Default>,
        val retryButton: ButtonState,
        val isLoading: Boolean
    ) : ServerListState
}

sealed interface ServerState : Itemizable {
    data class Default(
        override val key: Any,
        val radioButtonState: RadioButtonState,
        val badge: StringResource?,
    ) : ServerState {
        override val contentType: Any = "Default"
    }

    data class Custom(
        override val key: Any,
        val radioButtonState: RadioButtonState,
        val newServerTextFieldState: TextFieldState,
        val badge: StringResource?,
        val isExpanded: Boolean,
    ) : ServerState {
        override val contentType: Any = "Custom"
    }
}

sealed interface ServerDialogState {
    val state: AlertDialogState

    data class Validation(
        override val state: AlertDialogState,
        val reason: StringResource?
    ) : ServerDialogState
}
