package co.electriccoin.zcash.ui.common.model.migration

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class MigrationTransfer(
    val index: Int,
    val amountZatoshi: Long,
    val scheduledAtEpochSeconds: Long,
    val status: MigrationTransferStatus = MigrationTransferStatus.PENDING
) {
    val scheduledAt: Instant get() = Instant.fromEpochSeconds(scheduledAtEpochSeconds)
}
