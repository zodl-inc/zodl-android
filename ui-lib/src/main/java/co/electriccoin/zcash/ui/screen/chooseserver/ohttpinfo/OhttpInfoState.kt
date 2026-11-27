package co.electriccoin.zcash.ui.screen.chooseserver.ohttpinfo

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

data class OhttpInfoState(
    val title: StringResource,
    val body: StringResource,
    val gotItText: StringResource,
    val onGotIt: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview =
            OhttpInfoState(
                title = stringRes(R.string.ohttpInfo_title),
                body = stringRes(R.string.ohttpInfo_body),
                gotItText = stringRes(R.string.ohttpInfo_gotIt),
                onGotIt = {},
                onBack = {},
            )
    }
}
