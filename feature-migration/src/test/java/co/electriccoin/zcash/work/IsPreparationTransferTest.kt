package co.electriccoin.zcash.work

import cash.z.ecc.android.sdk.MigrationTransferState
import cash.z.ecc.android.sdk.MigrationTransferStates
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [isPreparationTransfer] is the id-keyed §2.1 same-beat check (core sync call 2026-08-05 §2.1):
 * whether the specific transaction the engine just named via [MigrationAdvanceStep.Broadcast] is a
 * preparation, so [MigrationDriveOnce.syncRun] can broadcast it in the same beat instead of waiting
 * out the privacy buffer. Unlike [nextDueUnsentIsPreparation] (schedule-order guess, covered in
 * PrepFastTrackTest.kt), this looks up the named id directly.
 */
class IsPreparationTransferTest {
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
    fun namedIdMatchingAPreparationIsTrue() {
        val s = states(state(1, isTransfer = false))
        assertTrue(isPreparationTransfer(s, transferId = 1))
    }

    @Test
    fun namedIdMatchingATransferIsFalse() {
        val s = states(state(1, isTransfer = true))
        assertFalse(isPreparationTransfer(s, transferId = 1))
    }

    @Test
    fun namedIdNotPresentInStatesIsFalse() {
        val s = states(state(1, isTransfer = false), state(2, isTransfer = true))
        assertFalse(isPreparationTransfer(s, transferId = 99))
    }

    @Test
    fun nullStatesIsFalse() {
        assertFalse(isPreparationTransfer(null, transferId = 1))
    }
}
