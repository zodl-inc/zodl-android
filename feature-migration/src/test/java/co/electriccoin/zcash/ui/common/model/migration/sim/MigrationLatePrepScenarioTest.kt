package co.electriccoin.zcash.ui.common.model.migration.sim

import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.TransferAttemptOutcome
import cash.z.ecc.android.sdk.TransferResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * State-level companion to the Rust `late_dependency_anchor_tests`: drives the real Lane A
 * (finalize/prove) and Lane B (broadcast) semantics through the [MigrationSimDriver] to reproduce
 * today's live bug — a preparation (funding note) that mines LATE, at a height past the anchor its
 * dependent transfer committed to, must never let that transfer prove or broadcast, and must not
 * crash or loop the migration.
 *
 * Layout (ids deliberately out of scheduled order — ZIP 318 shuffles funding-note order away from
 * broadcast order):
 *   - prep id1 (layer 0, no dep) funds transfer tx8, which committed to anchor A = 4_220_724.
 *   - prep id2 (layer 0, no dep) funds transfer tx7, which committed to the same anchor A.
 *   - id2 mines ON TIME (≤ A); id1 mines LATE (> A).
 * Expectation: tx7 proves and broadcasts; tx8 stays awaiting proof forever; migration keeps running.
 */
class MigrationLatePrepScenarioTest {
    private companion object {
        const val ANCHOR: Long = 4_220_724L
        const val LATE_MINE: Long = 4_220_802L // > ANCHOR by 78 blocks: too late to witness under A
        const val ON_TIME_MINE: Long = ANCHOR - 4L // ≤ ANCHOR: witnessable

        const val PREP_ID_LATE: Long = 1L
        const val PREP_ID_ON_TIME: Long = 2L
        const val TRANSFER_ID_LATE_DEP: Long = 8L
        const val TRANSFER_ID_ON_TIME_DEP: Long = 7L
    }

    private fun seededDriver(): MigrationSimDriver {
        val driver = MigrationSimDriver()
        driver.seedPlan(
            preparations =
                listOf(
                    MigrationSimDriver.SimPrep(id = PREP_ID_LATE, layer = 0, scheduledHeight = ANCHOR - 100L),
                    MigrationSimDriver.SimPrep(id = PREP_ID_ON_TIME, layer = 0, scheduledHeight = ANCHOR - 100L),
                ),
            transfers =
                listOf(
                    MigrationSimDriver.SimTransfer(
                        id = TRANSFER_ID_LATE_DEP,
                        scheduledHeight = ANCHOR + 10L,
                        anchorBoundary = ANCHOR,
                        dependsOn = listOf(PREP_ID_LATE),
                    ),
                    MigrationSimDriver.SimTransfer(
                        id = TRANSFER_ID_ON_TIME_DEP,
                        scheduledHeight = ANCHOR + 20L,
                        anchorBoundary = ANCHOR,
                        dependsOn = listOf(PREP_ID_ON_TIME),
                    ),
                ),
            startTip = ANCHOR - 100L,
        )
        return driver
    }

    @Test
    fun `late prep leaves its transfer unprovable while the on-time transfer proves and broadcasts`() =
        runTest {
            val driver = seededDriver()
            val sdk = driver.sdk

            // Time passes; the on-time prep confirms under the anchor, the other confirms late.
            // The on-time prep mines first (≤ ANCHOR), then the late prep mines at LATE_MINE > ANCHOR.
            // tip must remain monotonic, so we do NOT call setTip() afterward: mineTx() already
            // advanced the tip to LATE_MINE (4_220_802), which is well past both transfers'
            // scheduled heights (ANCHOR+10 and ANCHOR+20) and past the anchor itself.
            driver.advanceTip(blocks = 50L)
            driver.mine(id = PREP_ID_ON_TIME, height = ON_TIME_MINE)
            driver.mine(id = PREP_ID_LATE, height = LATE_MINE)
            // Tip is already at LATE_MINE (4_220_802) — past both ANCHOR (4_220_724) and both
            // transfers' scheduled heights. No backward setTip call needed.

            // ── Lane A: finalize/prove ──────────────────────────────────────────
            val provedCount = sdk.finalizeReadyTransfers()

            // Only the on-time-dep transfer proves. The late-dep transfer does NOT.
            assertFalse(
                sdk.txById(TRANSFER_ID_LATE_DEP)!!.proved,
                "tx8's dependency mined at $LATE_MINE > anchor $ANCHOR — it must never prove.",
            )
            assertTrue(
                sdk.txById(TRANSFER_ID_ON_TIME_DEP)!!.proved,
                "tx7's dependency mined at $ON_TIME_MINE <= anchor $ANCHOR — it must prove.",
            )
            // Exactly the transfers whose deps mined in time (the on-time transfer + both preps, whose
            // natural anchors are in the past) prove; the late-dep transfer is excluded.
            assertEquals(3, provedCount, "prep1, prep2, and tx7 prove; tx8 does not.")

            // Idempotent: a second Lane-A pass proves nothing new and does not throw or loop.
            assertEquals(0, sdk.finalizeReadyTransfers())

            // ── Lane B: broadcast ───────────────────────────────────────────────
            val opts = NetworkPrivacyOptions(useTor = false)

            // First broadcast picks up the earliest proved-due tx: the on-time transfer tx7.
            val first = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(first is TransferAttemptOutcome.Executed, "tx7 is proved+due — it must broadcast.")
            assertTrue(first.result is TransferResult.Success)
            assertTrue(sdk.txById(TRANSFER_ID_ON_TIME_DEP)!!.sent)

            // Next broadcast: the only remaining due tx is tx8, which is proved=false. Lane B must get
            // AwaitingProof (which it turns into a sync/prove pass upstream), NOT a crash or a spin.
            val second = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(
                second is TransferAttemptOutcome.AwaitingProof,
                "tx8 is due but never proved — Lane B must see AwaitingProof, not execute or crash.",
            )
            assertEquals(TRANSFER_ID_LATE_DEP, second.transferId)
            assertFalse(sdk.txById(TRANSFER_ID_LATE_DEP)!!.sent, "tx8 must never be broadcast.")

            // ── Banner / migration liveness ─────────────────────────────────────
            // The stuck tx8 is still overdue (banner keeps prompting), the migration is NOT Complete,
            // and re-running the whole Lane-A → Lane-B sweep stays a stable no-op — no crash, no loop.
            assertTrue(sdk.hasOverdueTransfers(useEstimatedTip = true), "tx8 remains overdue.")
            assertEquals(0, sdk.finalizeReadyTransfers())
            assertEquals(
                TRANSFER_ID_LATE_DEP,
                (sdk.executeNextPendingTransfer(opts, useEstimatedTip = true) as TransferAttemptOutcome.AwaitingProof)
                    .transferId,
            )
            assertFalse(
                sdk.getMigrationState() is cash.z.ecc.android.sdk.MigrationState.Complete,
                "One transfer is permanently stuck — migration must not report Complete.",
            )
        }
}
