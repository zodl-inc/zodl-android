package co.electriccoin.zcash.ui.screen.ohttpunavailable

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

data class OhttpUnavailableState(
    val title: StringResource,
    val body: StringResource,
    val keepWaitingText: StringResource,
    val switchToPublicText: StringResource,
    val torTitle: StringResource,
    val torBody: StringResource,
    val isTorEnabled: Boolean,
    val onTorToggle: (Boolean) -> Unit,
    val onKeepWaiting: () -> Unit,
    val onSwitchToPublic: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview =
            OhttpUnavailableState(
                title = stringRes(R.string.ohttpUnavailableSheet_title),
                body = stringRes(R.string.ohttpUnavailableSheet_body),
                keepWaitingText = stringRes(R.string.ohttpUnavailableSheet_keepWaiting),
                switchToPublicText = stringRes(R.string.ohttpUnavailableSheet_switchToPublic),
                torTitle = stringRes(R.string.ohttpUnavailableSheet_torTitle),
                torBody = stringRes(R.string.ohttpUnavailableSheet_torBody),
                isTorEnabled = false,
                onTorToggle = {},
                onKeepWaiting = {},
                onSwitchToPublic = {},
                onBack = {},
            )
    }
}
