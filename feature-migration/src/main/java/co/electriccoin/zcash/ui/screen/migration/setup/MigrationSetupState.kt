package co.electriccoin.zcash.ui.screen.migration.setup

import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationSetupState(
    val orchardBalance: StringResource,
    val fiatBalance: StringResource?,
    val isKeystone: Boolean,
    val mode: MigrationMode,
    val onModeChange: (MigrationMode) -> Unit,
    val onFindOutMore: () -> Unit,
    val onConfirm: () -> Unit,
    val onBack: () -> Unit,
    val gate: MigrationSetupGate = MigrationSetupGate.Open,
)

/**
 * What the NU7 gate allows on the setup screen. The scheduled lane waits for NU7 because its
 * transactions are signed for the consensus branch before it; the single-transaction lane is a
 * normal send and stays open until the last blocks before NU7.
 */
sealed interface MigrationSetupGate {
    /** Both options are offered. */
    data object Open : MigrationSetupGate

    /** Only the immediate option is offered; the scheduled one is shown paused with [automaticSubtitle]. */
    data class ScheduledPaused(
        val automaticSubtitle: StringResource,
    ) : MigrationSetupGate

    /** Neither option is offered: the last blocks before NU7. [notice] explains why. */
    data class Closed(
        val notice: StringResource,
    ) : MigrationSetupGate
}
