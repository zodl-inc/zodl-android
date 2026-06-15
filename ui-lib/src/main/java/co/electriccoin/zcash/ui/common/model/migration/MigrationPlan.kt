package co.electriccoin.zcash.ui.common.model.migration

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class MigrationPlan(
    val id: String,
    val createdAtEpochSeconds: Long,
    val transfers: List<MigrationTransfer>,
    val mode: MigrationMode = MigrationMode.AUTOMATIC,
) {
    val createdAt: Instant get() = Instant.fromEpochSeconds(createdAtEpochSeconds)
    val nextPending: MigrationTransfer? get() = transfers.firstOrNull { it.status == MigrationTransferStatus.PENDING }
    val isComplete: Boolean get() = transfers.all { it.status == MigrationTransferStatus.SENT }
    val completedCount: Int get() = transfers.count { it.status == MigrationTransferStatus.SENT }
    val totalCount: Int get() = transfers.size
}
