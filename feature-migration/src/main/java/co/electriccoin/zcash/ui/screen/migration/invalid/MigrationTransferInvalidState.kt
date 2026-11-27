package co.electriccoin.zcash.ui.screen.migration.invalid

import co.electriccoin.zcash.ui.common.model.migration.MigrationAttentionKind
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationTransferInvalidState(
    // Selects between spec §6.2 (PLAN_UPDATE) and §6.3 (TRANSFER_EXPIRED) copy — same layout,
    // different title/body (see MigrationTransferInvalidScreen). Never null in practice (this
    // screen is only ever reached while MigrationState is RequiresAttention), but defaults to the
    // most common cause if the reason couldn't be read for some reason (e.g. a transient SDK read
    // failure that still let the LCE load succeed).
    val kind: MigrationAttentionKind,
    val completedCount: Int,
    val totalCount: Int,
    val remainingCount: Int,
    val invalidRange: StringResource,
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
)
