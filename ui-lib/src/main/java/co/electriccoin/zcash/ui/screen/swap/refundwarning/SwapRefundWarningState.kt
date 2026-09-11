package co.electriccoin.zcash.ui.screen.swap.refundwarning

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource

data class SwapRefundWarningState(
    val title: StringResource,
    val message: StringResource,
    val checkbox: CheckboxState,
    val cancelButton: ButtonState,
    val continueButton: ButtonState,
    override val onBack: () -> Unit
) : ModalBottomSheetState
