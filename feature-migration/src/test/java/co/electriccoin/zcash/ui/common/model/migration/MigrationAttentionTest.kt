package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.AttentionReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class MigrationAttentionTest {
    private fun transfer(
        index: Int,
        id: Long,
        isSent: Boolean = false,
        expiryAtEpochSeconds: Long? = 1_000L,
    ) = LiveMigrationTransfer(
        id = id,
        index = index,
        amountZatoshi = 100_000L,
        scheduledHeight = 1_000L + index,
        scheduledAt = Instant.fromEpochSeconds(0),
        isSent = isSent,
        isProved = true,
        action = null,
        blocker = null,
        expiryAt = expiryAtEpochSeconds?.let { Instant.fromEpochSeconds(it) },
        minedHeight = null,
    )

    private fun snapshot(transfers: List<LiveMigrationTransfer>) =
        LiveMigrationSnapshot(
            transfers = transfers,
            preparations = emptyList(),
            tipHeight = 1_000L,
        )

    private val now = Instant.fromEpochSeconds(1_000L)

    @Test
    fun toUiKindMapsInvalidTransferToPlanUpdate() {
        assertEquals(MigrationAttentionKind.PLAN_UPDATE, AttentionReason.InvalidTransfer(11L).toUiKind())
    }

    @Test
    fun toUiKindMapsTransferExpiredToTransferExpired() {
        assertEquals(MigrationAttentionKind.TRANSFER_EXPIRED, AttentionReason.TransferExpired.toUiKind())
    }

    @Test
    fun invalidTransferFindsExactlyTheNamedTransferByIdNotPosition() {
        // Ids deliberately out of index order (ZIP 318 shuffles funding-note order away from
        // broadcast-height order) — this must still find id 11 at index 2, not index 1.
        val snapshot =
            snapshot(
                listOf(
                    transfer(index = 0, id = 10L),
                    transfer(index = 1, id = 12L),
                    transfer(index = 2, id = 11L),
                )
            )
        val indices = AttentionReason.InvalidTransfer(11L).affectedTransferIndices(snapshot, now)
        assertEquals(listOf(2), indices)
    }

    @Test
    fun invalidTransferWithNoMatchingIdIsEmpty() {
        val snapshot = snapshot(listOf(transfer(index = 0, id = 10L)))
        val indices = AttentionReason.InvalidTransfer(99L).affectedTransferIndices(snapshot, now)
        assertEquals(emptyList(), indices)
    }

    @Test
    fun transferExpiredFindsEveryUnsentTransferPastItsOwnExpiry() {
        val snapshot =
            snapshot(
                listOf(
                    transfer(index = 0, id = 10L, isSent = true, expiryAtEpochSeconds = 100L),
                    transfer(index = 1, id = 11L, expiryAtEpochSeconds = 500L),
                    transfer(index = 2, id = 12L, expiryAtEpochSeconds = 2_000L),
                )
            )
        // now=1000: t0 is SENT (excluded regardless of expiry), t1 is unsent and past its expiry
        // (included), t2 is unsent but not yet expired (excluded) — NOT "everything after the
        // last completed transfer" (the old, wrong behavior would have included both t1 and t2).
        val indices = AttentionReason.TransferExpired.affectedTransferIndices(snapshot, now)
        assertEquals(listOf(1), indices)
    }

    @Test
    fun transferExpiredExcludesNeverExpiringTransfers() {
        // expiryAt == null means the engine reports "never expires" (ZIP 203 height 0).
        val snapshot =
            snapshot(
                listOf(
                    transfer(index = 0, id = 10L, expiryAtEpochSeconds = null),
                    transfer(index = 1, id = 11L, expiryAtEpochSeconds = 500L),
                )
            )
        val indices = AttentionReason.TransferExpired.affectedTransferIndices(snapshot, now)
        assertEquals(listOf(1), indices)
    }

    @Test
    fun syncRequiredBeforeNextHasNoAffectedTransfers() {
        val snapshot = snapshot(listOf(transfer(index = 0, id = 10L)))
        val indices = AttentionReason.SyncRequiredBeforeNext.affectedTransferIndices(snapshot, now)
        assertEquals(emptyList(), indices)
    }

    @Test
    fun rangeTextIsNullForEmptyIndices() {
        assertNull(emptyList<Int>().toMigrationRangeText())
    }

    @Test
    fun rangeTextIsASingleNumberForOneTransfer() {
        // affectedTransferIndices returns 0-based indices — the displayed "Transfer N" is 1-based.
        assertEquals("3", listOf(2).toMigrationRangeText())
    }

    @Test
    fun rangeTextIsAContiguousDashRangeForMultipleTransfers() {
        assertEquals("3–5", listOf(4, 2, 3).toMigrationRangeText())
    }
}
