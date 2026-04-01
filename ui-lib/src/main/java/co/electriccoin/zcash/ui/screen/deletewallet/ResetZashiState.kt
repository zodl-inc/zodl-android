package co.electriccoin.zcash.ui.screen.deletewallet

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState

data class ResetZashiState(
    val onBack: () -> Unit,
    val checkboxState: CheckboxState,
    val buttonState: ButtonState,
    val confirmationDialog: ZashiConfirmationState?,
)
