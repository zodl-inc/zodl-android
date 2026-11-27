package co.electriccoin.zcash.ui.common.model.migration.sim

import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.TransferAttemptOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The overdue-vs-unproved distinction the home banner + Lane B rely on: a transfer whose scheduled
 * height is in the past is *past-scheduled*, but it is only genuinely "send now" once it is PROVED.
 * While it is still merely `signed` (unproved) — the funding note has not confirmed under its anchor
 * yet — the engine surface must report it as AwaitingProof, NOT as a broadcastable overdue transfer.
 *
 * This mirrors the live overdue-vs-unproved bug: the banner must not prompt "send now" (and Lane B
 * must not spin trying to broadcast) for a transfer that physically cannot broadcast yet because it
 * is unproved. Only after [FakeOrchardMigrationSdk.finalizeReadyTransfers] proves it does a
 * still-past-scheduled-unsent transfer become a genuine send-now.
 *
 * Layout — two transfers, both funded by preps that mine on time, both past-scheduled while unproved:
 *   - prep id1 funds transfer tx10 (anchor A, scheduled A+5)
 *   - prep id2 funds transfer tx11 (anchor A, scheduled A+15)
 * The tip is advanced well past both scheduled heights BEFORE any finalize pass runs.
 */
class MigrationOverdueScenarioTest {
    private companion object {
        const val ANCHOR: Long = 3_100_000L
        const val PREP_ID_A: Long = 1L
        const val PREP_ID_B: Long = 2L
        const val TRANSFER_A: Long = 10L
        const val TRANSFER_B: Long = 11L
        const val SCHED_A: Long = ANCHOR + 5L
        const val SCHED_B: Long = ANCHOR + 15L

        // Both preps mine ON TIME (≤ anchor) so, once proved, both transfers are broadcastable.
        const val PREP_MINE: Long = ANCHOR - 2L
    }

    private fun seededDriver(): MigrationSimDriver {
        val driver = MigrationSimDriver()
        driver.seedPlan(
            preparations =
                listOf(
                    MigrationSimDriver.SimPrep(id = PREP_ID_A, layer = 0, scheduledHeight = ANCHOR - 50L),
                    MigrationSimDriver.SimPrep(id = PREP_ID_B, layer = 0, scheduledHeight = ANCHOR - 50L),
                ),
            transfers =
                listOf(
                    MigrationSimDriver.SimTransfer(
                        id = TRANSFER_A,
                        scheduledHeight = SCHED_A,
                        anchorBoundary = ANCHOR,
                        dependsOn = listOf(PREP_ID_A),
                    ),
                    MigrationSimDriver.SimTransfer(
                        id = TRANSFER_B,
                        scheduledHeight = SCHED_B,
                        anchorBoundary = ANCHOR,
                        dependsOn = listOf(PREP_ID_B),
                    ),
                ),
            startTip = ANCHOR - 50L,
        )
        return driver
    }

    @Test
    fun `past-scheduled but unproved transfers are AwaitingProof, not send-now`() =
        runTest {
            val driver = seededDriver()
            val sdk = driver.sdk
            val opts = NetworkPrivacyOptions(useTor = false)

            // Mine both preps on time, then push the tip PAST both transfers' scheduled heights —
            // WITHOUT running a finalize pass. Both transfers are now past-scheduled but still unproved.
            driver.mine(id = PREP_ID_A, height = PREP_MINE)
            driver.mine(id = PREP_ID_B, height = PREP_MINE)
            driver.setTip(SCHED_B + 30L)

            assertTrue(sdk.txById(TRANSFER_A)!!.scheduledHeight < sdk.tip)
            assertTrue(sdk.txById(TRANSFER_B)!!.scheduledHeight < sdk.tip)
            assertFalse(sdk.txById(TRANSFER_A)!!.proved, "not finalized yet")
            assertFalse(sdk.txById(TRANSFER_B)!!.proved, "not finalized yet")

            // Lane B must NOT falsely broadcast either: with nothing proved, the earliest due transfer
            // comes back as AwaitingProof (upstream turns this into a sync/prove pass), NOT Executed.
            val outcome = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(
                outcome is TransferAttemptOutcome.AwaitingProof,
                "past-scheduled but unproved must be AwaitingProof, not a send-now broadcast.",
            )
            assertEquals(TRANSFER_A, outcome.transferId, "earliest past-scheduled transfer is surfaced.")
            assertFalse(sdk.txById(TRANSFER_A)!!.sent)
            assertFalse(sdk.txById(TRANSFER_B)!!.sent)

            // getMigrationTransferStates reflects the distinction the app filters on: both transfers are
            // past-scheduled (scheduledHeight < tip) yet isProved == false → the app treats these as
            // "awaiting proof", not "overdue send-now".
            val states = sdk.getMigrationTransferStates()!!
            val pastScheduledUnproved =
                states.transfers.filter {
                    it.isTransfer && !it.isSent && it.scheduledHeight < states.tipHeight && !it.isProved
                }
            assertEquals(
                setOf(TRANSFER_A, TRANSFER_B),
                pastScheduledUnproved.map { it.id }.toSet(),
                "both transfers are past-scheduled but unproved — not genuinely send-now.",
            )
        }

    @Test
    fun `once proved at its boundary a still-unsent past-scheduled transfer is genuinely overdue`() =
        runTest {
            val driver = seededDriver()
            val sdk = driver.sdk
            val opts = NetworkPrivacyOptions(useTor = false)

            driver.mine(id = PREP_ID_A, height = PREP_MINE)
            driver.mine(id = PREP_ID_B, height = PREP_MINE)
            driver.setTip(SCHED_B + 30L)

            // ── Lane A: finalize. With deps mined on time and the anchor in the past, BOTH transfers
            // prove (plus the two preps). This is the boundary at which past-scheduled becomes send-now.
            val proved = sdk.finalizeReadyTransfers()
            assertEquals(4, proved, "prep1, prep2, tx10, tx11 all prove.")
            assertTrue(sdk.txById(TRANSFER_A)!!.proved)
            assertTrue(sdk.txById(TRANSFER_B)!!.proved)

            // Now hasOverdueTransfers is genuinely true AND acting on it broadcasts (not AwaitingProof):
            assertTrue(sdk.hasOverdueTransfers(useEstimatedTip = true), "proved + past-scheduled = overdue.")

            val first = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(first is TransferAttemptOutcome.Executed, "proved+past-scheduled transfer broadcasts.")
            assertTrue(sdk.txById(TRANSFER_A)!!.sent)

            // getMigrationTransferStates: the remaining unsent transfer is now proved — a real send-now,
            // no longer awaiting proof.
            val states = sdk.getMigrationTransferStates()!!
            val genuinelyOverdue =
                states.transfers.filter {
                    it.isTransfer && !it.isSent && it.scheduledHeight < states.tipHeight && it.isProved
                }
            assertEquals(
                listOf(TRANSFER_B),
                genuinelyOverdue.map { it.id },
                "tx11 is proved + past-scheduled + unsent — genuinely overdue.",
            )

            // Drain it and the migration reaches Complete — the proved-gating never wedged the run.
            val second = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(second is TransferAttemptOutcome.Executed)
            assertFalse(sdk.hasOverdueTransfers(useEstimatedTip = true), "all sent — nothing overdue.")
            assertTrue(sdk.getMigrationState() is cash.z.ecc.android.sdk.MigrationState.Complete)
        }
}
