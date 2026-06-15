package co.electriccoin.zcash.ui.screen.migration.invalid

import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationTransferInvalidState(
    val completedCount: Int,
    val totalCount: Int,
    val remainingCount: Int,
    val invalidRange: StringResource,
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
)
