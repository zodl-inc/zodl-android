package co.electriccoin.zcash.ui.screen.voting.chainconfig

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

@Immutable
data class VoteChainConfigState(
    val chains: List<VoteChainConfigItemState>,
    val editor: VoteChainConfigEditorState?,
    val errorSheet: ZashiConfirmationState?,
    val isValidating: Boolean,
    val saveChangesButton: ButtonState,
    val onBack: () -> Unit,
    val onAddCustom: () -> Unit,
) {
    companion object {
        val preview =
            VoteChainConfigState(
                chains = listOf(VoteChainConfigItemState.preview),
                editor = null,
                errorSheet = null,
                isValidating = false,
                saveChangesButton = ButtonState.preview,
                onBack = {},
                onAddCustom = {},
            )
    }
}

@Immutable
data class VoteChainConfigItemState(
    val id: String,
    val radioButtonState: RadioButtonState,
    val fullUrl: StringResource,
    val isDefault: Boolean,
    val editButton: ButtonState?,
    val deleteButton: ButtonState?,
) {
    companion object {
        val preview =
            VoteChainConfigItemState(
                id = "default",
                radioButtonState =
                    RadioButtonState(
                        text = stringRes("Default"),
                        isChecked = true,
                        onClick = {},
                    ),
                fullUrl = stringRes("https://vote.zfnd.org"),
                isDefault = true,
                editButton = null,
                deleteButton = null,
            )
    }
}

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
) {
    companion object {
        val preview =
            VoteChainConfigEditorState(
                sheetTitle = stringRes("Add custom source"),
                title = stringRes("Custom voting source"),
                description = stringRes("Enter the URL of a trusted voting configuration source."),
                name = TextFieldState(value = stringRes("My Source"), onValueChange = {}),
                url = TextFieldState(value = stringRes("https://example.com/voting.json"), onValueChange = {}),
                showsUrlCopyButton = false,
                onUrlCopyClick = {},
                deleteButton = null,
                saveButton = ButtonState.preview,
                cancelButton = ButtonState.preview,
            )
    }
}
