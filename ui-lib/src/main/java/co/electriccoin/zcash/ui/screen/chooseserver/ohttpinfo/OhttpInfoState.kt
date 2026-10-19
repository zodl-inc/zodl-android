package co.electriccoin.zcash.ui.screen.chooseserver.ohttpinfo

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState

data class OhttpInfoState(
    val onGotIt: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
