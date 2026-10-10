package co.electriccoin.zcash.ui.screen.texunsupported

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState

data class TEXUnsupportedState(
    val isLedger: Boolean = false,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
