package co.electriccoin.zcash.ui.screen.advancedsettings.debug

import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState

data class DebugState(
    val items: List<ListItemState>,
    val onBack: () -> Unit,
    val confirmationDialog: ZashiConfirmationState? = null,
)
