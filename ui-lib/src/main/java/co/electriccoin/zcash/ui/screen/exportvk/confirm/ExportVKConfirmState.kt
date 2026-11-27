package co.electriccoin.zcash.ui.screen.exportvk.confirm

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.stringRes

internal data class ExportVKConfirmState(
    val checkboxes: List<CheckboxState>,
    val cancelButton: ButtonState,
    val exportButton: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview = preview(checkedCount = 1)

        val previewAllChecked = preview(checkedCount = 3)

        private fun preview(checkedCount: Int) =
            ExportVKConfirmState(
                checkboxes =
                    listOf(
                        "I understand this reveals all my transactions, past and future",
                        "I trust the party I'm sharing this with",
                        "I understand this cannot be undone",
                    ).mapIndexed { index, title ->
                        CheckboxState(
                            title = stringRes(title),
                            isChecked = index < checkedCount,
                            onClick = {}
                        )
                    },
                cancelButton = ButtonState(text = stringRes("Cancel")),
                exportButton =
                    ButtonState(
                        text = stringRes("Export Key"),
                        isEnabled = checkedCount == 3
                    ),
                onBack = {}
            )
    }
}
