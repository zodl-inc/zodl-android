package co.electriccoin.zcash.ui.screen.swap.slippage

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource

@Immutable
data class SwapSlippageState(
    val picker: SlippagePickerState,
    val info: SwapSlippageInfoState?,
    val warning: StyledStringResource?,
    val primary: ButtonState,
    override val onBack: () -> Unit
) : ModalBottomSheetState

@Immutable
data class SwapSlippageInfoState(
    val title: StringResource,
    val additional: StringResource?,
    val mode: Mode,
) {
    enum class Mode { LOW, MEDIUM, HIGH }
}
