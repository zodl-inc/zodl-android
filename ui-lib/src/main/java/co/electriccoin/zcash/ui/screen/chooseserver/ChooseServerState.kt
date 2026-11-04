package co.electriccoin.zcash.ui.screen.chooseserver

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.EndpointTextFieldState
import co.electriccoin.zcash.ui.design.component.AlertDialogState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.util.Itemizable
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

data class ChooseServerState(
    val connectionMode: ServerConnectionModeState,
    val fastest: ServerListState.Fastest,
    val other: ServerListState.Other,
    val saveButton: ButtonState,
    val dialogState: ServerDialogState?,
    val onBack: () -> Unit
)

data class ServerConnectionModeState(
    val private: RadioButtonState,
    val public: RadioButtonState,
    val manual: RadioButtonState,
    val privateBadge: ModeBadgeState? = null,
    val publicBadge: ModeBadgeState? = null,
    val manualBadge: ModeBadgeState? = null,
    val onPrivateInfoClick: () -> Unit = {},
    val privateInfoLabel: StringResource = stringRes(R.string.serverSetup_privateInfo),
) {
    val isManualSelected = manual.isChecked
}

enum class ModeBadgeStyle { SUCCESS, WARNING, ERROR }

/** [showsProgress]: the row is being tested, so its radio is replaced by a progress indicator. */
data class ModeBadgeState(
    val text: StringResource,
    val style: ModeBadgeStyle,
    val showsProgress: Boolean = false,
)

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
        val newServerTextFieldState: EndpointTextFieldState,
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
