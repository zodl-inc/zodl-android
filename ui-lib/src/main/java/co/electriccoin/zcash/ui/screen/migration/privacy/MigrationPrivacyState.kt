package co.electriccoin.zcash.ui.screen.migration.privacy

import co.electriccoin.zcash.ui.common.model.migration.MigrationMode

data class MigrationPrivacyState(
    val mode: MigrationMode,
    val useTor: Boolean,
    val onTorToggle: (Boolean) -> Unit,
    val onConfirm: () -> Unit,
    val onSkip: () -> Unit,
    val onBack: () -> Unit,
)
