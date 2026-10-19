package co.electriccoin.zcash.ui.screen.ohttpunavailable

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState

data class OhttpUnavailableState(
    val isTorEnabled: Boolean,
    val onTorToggle: (Boolean) -> Unit,
    val onKeepWaiting: () -> Unit,
    val onSwitchToPublic: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
