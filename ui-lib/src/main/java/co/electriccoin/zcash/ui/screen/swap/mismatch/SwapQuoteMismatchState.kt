package co.electriccoin.zcash.ui.screen.swap.mismatch

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource

data class SwapQuoteMismatchState(
    val title: StringResource,
    val paragraphs: List<StringResource>,
    val goBackButton: ButtonState,
    val reportButton: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
