package co.electriccoin.zcash.ui.screen.migration.setup

import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationSetupState(
    val orchardBalance: StringResource,
    val fiatBalance: StringResource?,
    val isKeystone: Boolean,
    val mode: MigrationMode,
    val onModeChange: (MigrationMode) -> Unit,
    val onConfirm: () -> Unit,
    val onBack: () -> Unit,
)
