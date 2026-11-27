package co.electriccoin.zcash.ui.screen.migration.torfailure

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState

data class MigrationTorFailureState(
    val onContinueWithoutTor: () -> Unit,
    val onTryAgain: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
