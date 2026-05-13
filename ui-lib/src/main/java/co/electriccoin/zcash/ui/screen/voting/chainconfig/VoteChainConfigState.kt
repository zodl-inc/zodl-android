package co.electriccoin.zcash.ui.screen.voting.chainconfig

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class VoteChainConfigState(
    val chains: List<VoteChainConfigItemState>,
    val editor: VoteChainConfigEditorState?,
    val errorSheet: ZashiConfirmationState?,
    val isValidating: Boolean,
    val saveChangesButton: ButtonState,
    val onBack: () -> Unit,
    val onAddCustom: () -> Unit,
)

@Immutable
data class VoteChainConfigItemState(
    val id: String,
    val radioButtonState: RadioButtonState,
    val fullUrl: StringResource,
    val isDefault: Boolean,
    val editButton: ButtonState?,
    val deleteButton: ButtonState?,
)

@Immutable
data class VoteChainConfigEditorState(
    val sheetTitle: StringResource,
    val title: StringResource,
    val description: StringResource,
    val name: TextFieldState,
    val url: TextFieldState,
    val showsUrlCopyButton: Boolean,
    val onUrlCopyClick: () -> Unit,
    val deleteButton: ButtonState?,
    val saveButton: ButtonState,
    val cancelButton: ButtonState,
)
