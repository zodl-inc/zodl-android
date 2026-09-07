package co.electriccoin.zcash.ui.screen.theme.darklook

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState

internal data class ThemeDarkLookState(
    val options: List<DarkLookOptionState>,
    val saveButton: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState

internal data class DarkLookOptionState(
    val isOledEnabled: Boolean,
    val isChecked: Boolean,
    val onClick: () -> Unit,
)
