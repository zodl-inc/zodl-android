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
 * State-level companion to MigrationSendingVMTest's Tor routing: drives the SDK broadcast surface
 * Lane B calls and asserts the outcome the app relies on when a broadcast fails specifically because
 * of Tor circuit bootstrap.
 *
 * Contract exercised:
 *  - the failing attempt returns [TransferResult.NetworkError] with `isTorFailure = true`,
 *  - the transfer stays UNSENT (so a later window retries it), and
 *  - once "Tor recovers" (the injected failure clears), the very same transfer broadcasts.
 *
 * The app-side Tor routing (MigrationSendingVM.init → sendOnce → MigrationTorFailureArgs) is covered
 * by MigrationSendingVMTest; this test intentionally does NOT drive the CoroutineWorker — it pins
 * the SDK-surface behaviour that routing is built on.
 */
class MigrationTorFailureScenarioTest {
    private companion object {
        const val ANCHOR: Long = 2_500_000L
        const val PREP_ID: Long = 1L
        const val TRANSFER_ID: Long = 9L
        const val SCHED: Long = ANCHOR + 8L
    }

    private fun seededDriver(): MigrationSimDriver {
        val driver = MigrationSimDriver()
        driver.seedPlan(
            preparations =
                listOf(
                    MigrationSimDriver.SimPrep(id = PREP_ID, layer = 0, scheduledHeight = ANCHOR - 30L),
                ),
            transfers =
                listOf(
                    MigrationSimDriver.SimTransfer(
                        id = TRANSFER_ID,
                        scheduledHeight = SCHED,
                        anchorBoundary = ANCHOR,
                        dependsOn = listOf(PREP_ID),
                    ),
                ),
            startTip = ANCHOR - 30L,
        )
        // Bring the single transfer to proved + due so the ONLY thing that can stop it is the
        // injected broadcast failure — isolating the Tor-failure behaviour.
        driver.mine(id = PREP_ID, height = ANCHOR - 2L)
        driver.setTip(SCHED + 5L)
        return driver
    }

    @Test
    fun `a Tor broadcast failure leaves the transfer unsent, then retries successfully once Tor recovers`() =
        runTest {
            val driver = seededDriver()
            val sdk = driver.sdk
            val opts = NetworkPrivacyOptions(useTor = true)

            // Lane A proves the transfer (prep + transfer).
            assertEquals(2, sdk.finalizeReadyTransfers())
            assertTrue(sdk.txById(TRANSFER_ID)!!.proved)

            // Arm the next broadcast to fail with a Tor circuit-bootstrap failure.
            driver.failNextBroadcastWithTorFailure()

            val failed = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(failed is TransferAttemptOutcome.Executed, "the attempt reached the broadcast stage.")
            val failResult = failed.result
            assertTrue(failResult is TransferResult.NetworkError, "broadcast failed with a network error.")
            assertTrue(failResult.isTorFailure, "the failure is specifically a Tor failure.")
            assertTrue(failResult.retryable, "a Tor failure is retryable.")

            // The transfer must NOT be marked sent — the next window (or user retry) must find it pending.
            assertFalse(sdk.txById(TRANSFER_ID)!!.sent, "a failed Tor broadcast leaves the transfer unsent.")
            assertTrue(sdk.hasOverdueTransfers(useEstimatedTip = true), "still due — banner keeps prompting.")
            assertFalse(sdk.getMigrationState() is cash.z.ecc.android.sdk.MigrationState.Complete)

            // "Tor recovers" — the injection is one-shot, so it is already cleared; re-running broadcasts.
            val recovered = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(recovered is TransferAttemptOutcome.Executed)
            assertTrue(recovered.result is TransferResult.Success, "same transfer broadcasts once Tor recovers.")
            assertTrue(sdk.txById(TRANSFER_ID)!!.sent)
            assertFalse(sdk.hasOverdueTransfers(useEstimatedTip = true))
            assertTrue(sdk.getMigrationState() is cash.z.ecc.android.sdk.MigrationState.Complete)
        }

    @Test
    fun `explicitly clearing the failure also lets the transfer broadcast`() =
        runTest {
            // Guards the driver's explicit-clear path (clearBroadcastFailure), separate from the one-shot
            // auto-consume — a caller that arms a failure but recovers before attempting must not be stuck.
            val driver = seededDriver()
            val sdk = driver.sdk
            val opts = NetworkPrivacyOptions(useTor = true)

            assertEquals(2, sdk.finalizeReadyTransfers())

            driver.failNextBroadcast(TransferResult.NetworkError(retryable = true, isTorFailure = true))
            driver.clearBroadcastFailure()

            val outcome = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(outcome is TransferAttemptOutcome.Executed)
            assertTrue(outcome.result is TransferResult.Success)
            assertTrue(sdk.txById(TRANSFER_ID)!!.sent)
        }
}
