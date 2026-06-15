package co.electriccoin.zcash.ui.screen.migration.progress

import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationProgressState(
    val title: StringResource,
    val subtitle: StringResource,
    val transfers: List<MigrationProgressTransferState>,
    val completedCount: Int,
    val totalCount: Int,
    val isComplete: Boolean,
    val hasOverdue: Boolean,
    val onBack: () -> Unit,
    val progressSummary: StringResource?,
    val onSendNow: (() -> Unit)? = null,
    val onReschedule: (() -> Unit)? = null,
    val onSimulateTransfer: (() -> Unit)? = null,
    val onResetMigration: (() -> Unit)? = null,
    val onDone: (() -> Unit)? = null,
)

data class MigrationProgressTransferState(
    val index: Int,
    val totalCount: Int,
    val amount: StringResource,
    val statusLabel: StringResource,
    val isOverdue: Boolean,
    val isSent: Boolean,
    val fiatAmount: StringResource? = null,
)
