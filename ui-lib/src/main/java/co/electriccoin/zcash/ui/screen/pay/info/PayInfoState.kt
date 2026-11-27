package co.electriccoin.zcash.ui.screen.pay.info

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState

data class PayInfoState(
    override val onBack: () -> Unit,
    val onLearnMoreClick: () -> Unit
) : ModalBottomSheetState
