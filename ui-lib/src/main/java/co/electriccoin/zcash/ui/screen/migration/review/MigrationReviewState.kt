package co.electriccoin.zcash.ui.screen.migration.review

import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationReviewState(
    val mode: MigrationMode,
    val totalAmount: StringResource,
    val transfers: List<MigrationReviewTransferState>,
    val isConfirming: Boolean = false,
    val onConfirm: () -> Unit,
    val onBack: () -> Unit,
)

data class MigrationReviewTransferState(
    val index: Int,
    val totalCount: Int,
    val amount: StringResource,
    val fiatAmount: StringResource?,
    val scheduledLabel: StringResource,
)
