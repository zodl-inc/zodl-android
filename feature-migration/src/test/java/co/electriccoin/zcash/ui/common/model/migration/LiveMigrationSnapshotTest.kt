package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.MigrationTransferState
import cash.z.ecc.android.sdk.MigrationTransferStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * NU7 characterization pins (MOB-2287): [toSnapshot]'s height -> wall-clock projection at the
 * pre-NU7 75 s/block rate. Every call passes secondsPerBlock = 75 explicitly.
 */
class LiveMigrationSnapshotTest {
    private val now = 1_800_000_000L
    private val tip = 5_000_000L

    private fun transfer(
        id: Long,
        scheduled: Long,
        expiry: Long? = null,
        mined: Long? = null,
        isTransfer: Boolean = true,
    ) = MigrationTransferState(
        id = id,
        isTransfer = isTransfer,
        isSent = mined != null,
        isProved = true,
        scheduledHeight = scheduled,
        anchorBoundaryHeight = null,
        expiryHeight = expiry,
        minedHeight = mined,
    )

    private fun snapshot(vararg states: MigrationTransferState) =
        MigrationTransferStates(transfers = states.toList(), tipHeight = tip)
            .toSnapshot(estimatedTip = tip, secondsPerBlock = 75L, nowEpochSeconds = now)

    @Test
    fun `characterization pin of pre-NU7 75s - scheduledAt is now plus block delta times 75`() {
        // NU7: revisit - spans activation once helper is height-aware
        val t = snapshot(transfer(id = 1, scheduled = tip + 100)).transfers.single()
        assertEquals(now + 100L * 75L, t.scheduledAt.epochSeconds)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - expiryAt projects at 75s per block and null stays null`() {
        // NU7: revisit - spans activation once helper is height-aware
        val s = snapshot(transfer(id = 1, scheduled = tip + 10, expiry = tip + 40), transfer(id = 2, scheduled = tip + 20))
        assertEquals(now + 40L * 75L, s.transfers[0].expiryAt?.epochSeconds)
        assertNull(s.transfers[1].expiryAt)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - minedAt projects at 75s per block and null until mined`() {
        // NU7: revisit - spans activation once helper is height-aware
        val s = snapshot(transfer(id = 1, scheduled = tip - 30, mined = tip - 20), transfer(id = 2, scheduled = tip + 5))
        assertEquals(now - 20L * 75L, s.transfers[0].minedAt?.epochSeconds)
        assertNull(s.transfers[1].minedAt)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - past heights project before now with negative deltas`() {
        // NU7: revisit - spans activation once helper is height-aware
        val t = snapshot(transfer(id = 1, scheduled = tip - 96)).transfers.single()
        assertEquals(now - 96L * 75L, t.scheduledAt.epochSeconds)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - height equal to the estimated tip projects to now`() {
        val t = snapshot(transfer(id = 1, scheduled = tip)).transfers.single()
        assertEquals(now, t.scheduledAt.epochSeconds)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - preparations scheduledAt uses the same 75s line`() {
        // NU7: revisit - spans activation once helper is height-aware
        val p = snapshot(transfer(id = 1, scheduled = tip + 7, isTransfer = false)).preparations.single()
        assertEquals(now + 7L * 75L, p.scheduledAt.epochSeconds)
    }
}
