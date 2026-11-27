package co.electriccoin.zcash.work

import cash.z.ecc.android.sdk.MigrationTransferState
import cash.z.ecc.android.sdk.MigrationTransferStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The preparation fast-track (security split, 2026-07-30 tx9 investigation): in-pool note-split
 * preparations skip the crossing-grade privacy ceremony — quiet gap, active-sync defer and send
 * spacing — because they leak neither amount nor anchor-set membership. Crossings keep the full
 * ceremony.
 */
class PrepFastTrackTest {
    private fun state(
        id: Long,
        isTransfer: Boolean,
        isSent: Boolean = false,
        isProved: Boolean = true,
        scheduled: Long = 100L,
    ) = MigrationTransferState(
        id = id,
        isTransfer = isTransfer,
        isSent = isSent,
        isProved = isProved,
        scheduledHeight = scheduled,
        anchorBoundaryHeight = if (isTransfer) scheduled - 10 else null,
    )

    private fun states(vararg transfers: MigrationTransferState) =
        MigrationTransferStates(transfers = transfers.toList(), tipHeight = 100L)

    @Test
    fun dueProvedPreparationTriggersFastTrack() {
        val s = states(state(0, isTransfer = false, scheduled = 90))
        assertTrue(nextDueUnsentIsPreparation(s, estimatedTip = 100))
    }

    @Test
    fun transferNeverTriggersFastTrack() {
        val s = states(state(4, isTransfer = true, scheduled = 90))
        assertFalse(nextDueUnsentIsPreparation(s, estimatedTip = 100))
    }

    @Test
    fun unprovedPreparationDoesNotFastTrack() {
        val s = states(state(0, isTransfer = false, isProved = false, scheduled = 90))
        assertFalse(nextDueUnsentIsPreparation(s, estimatedTip = 100))
    }

    @Test
    fun notYetDuePreparationDoesNotFastTrack() {
        val s = states(state(0, isTransfer = false, scheduled = 150))
        assertFalse(nextDueUnsentIsPreparation(s, estimatedTip = 100))
    }

    @Test
    fun nextUnsentIsPickedByScheduleOrderNotId() {
        // A sent prep is skipped; the next UNSENT by schedule order is the transfer → no fast-track
        // even though an unsent prep exists later in the schedule.
        val s =
            states(
                state(0, isTransfer = false, isSent = true, scheduled = 80),
                state(4, isTransfer = true, scheduled = 90),
                state(2, isTransfer = false, scheduled = 95),
            )
        assertFalse(nextDueUnsentIsPreparation(s, estimatedTip = 100))
    }

    @Test
    fun nullStatesNeverFastTrack() {
        assertFalse(nextDueUnsentIsPreparation(null, estimatedTip = 100))
    }

    @Test
    fun fastTrackSkipsQuietGapAndSyncingDefers() {
        val action =
            decideBroadcastPreflight(
                synchronizerSyncing = true,
                nowEpochSeconds = 1_000,
                // quiet gap clearly unmet
                lastNetworkActivityEpochSeconds = 999,
                privacyBufferSeconds = 180,
                prepFastTrack = true,
            )
        assertEquals(BroadcastPreflight.BROADCAST, action)
    }

    @Test
    fun transfersKeepTheFullCeremony() {
        val action =
            decideBroadcastPreflight(
                synchronizerSyncing = false,
                nowEpochSeconds = 1_000,
                lastNetworkActivityEpochSeconds = 999,
                privacyBufferSeconds = 180,
                prepFastTrack = false,
            )
        assertEquals(BroadcastPreflight.DEFER, action)
    }
}
