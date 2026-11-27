package co.electriccoin.zcash.ui.screen.migration.restart

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationRestartState(
    val onBack: () -> Unit,
    val body: StringResource,
    val migratedLabel: StringResource,
    val migratedValue: StringResource,
    val remainingLabel: StringResource,
    val remainingValue: StringResource,
    val warning: StringResource,
    val support: StringResource,
    val nextButton: ButtonState,
    val confirmationDialog: ZashiConfirmationState?,
)
