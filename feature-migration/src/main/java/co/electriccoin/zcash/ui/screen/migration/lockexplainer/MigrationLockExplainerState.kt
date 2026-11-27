package co.electriccoin.zcash.ui.screen.migration.lockexplainer

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState

data class MigrationLockExplainerState(
    val onGotIt: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
