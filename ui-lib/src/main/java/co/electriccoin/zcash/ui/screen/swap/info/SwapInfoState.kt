package co.electriccoin.zcash.ui.screen.swap.info

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState

data class SwapInfoState(
    override val onBack: () -> Unit,
    val onLearnMoreClick: () -> Unit
) : ModalBottomSheetState
