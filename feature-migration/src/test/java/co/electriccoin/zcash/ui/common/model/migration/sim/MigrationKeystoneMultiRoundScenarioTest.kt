package co.electriccoin.zcash.ui.common.model.migration.sim

import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Models a multi-round Keystone campaign at the STATE level: the engine's per-run note cap means a
 * large Orchard balance can't clear in one run, so after round N completes a residual remains and a
 * round N+1 is expected. The app decides "another round needed?" from the DECISION INPUTS the SDK
 * exposes — [FakeOrchardMigrationSdk.estimateMigrationRunCount] (a stateless preview off the
 * remaining migratable balance) and [FakeOrchardMigrationSdk.migrationDustThresholdZatoshi] (what
 * counts as an ignorable residual vs. a migratable one) — not from Keystone QR plumbing.
 *
 * This test drives that decision loop directly: round 1 completes, the residual is read, and because
 * it still exceeds BOTH the per-run cap's single-round reach AND the dust threshold, another round is
 * indicated. After round 2 the residual falls to dust, so no round 3 is indicated.
 *
 * Scope note: the actual Keystone batch-signing surface (buildKeystoneSignBatchQrParts /
 * decodeKeystoneSignBatchPart / applyKeystoneBatchSignatures) and the propose/commit of a fresh
 * round's schedule are still `NotImplementedError` in the fake — a fuller scenario that walks each
 * round's QR sign-and-commit would need those plus a per-round schedule seeded into the chain. Here
 * the residual is advanced explicitly (as a real completed round would leave it) so the
 * continuation DECISION is exercised end to end; the signing transport is out of scope for a
 * state-level harness.
 */
class MigrationKeystoneMultiRoundScenarioTest {
    private companion object {
        const val ANCHOR: Long = 5_000_000L
        const val PREP_ID: Long = 1L
        const val TRANSFER_ID: Long = 30L

        // Per-run migratable ceiling the engine's note cap imposes.
        const val PER_RUN_CAP: Long = 1_000_000L // 0.01 ZEC per run
        const val DUST: Long = 10_000L // 0.0001 ZEC — the protocol dust threshold

        // Start with 2.5x a single run's reach: three rounds' worth (ceil(2_500_000/1_000_000) = 3).
        const val INITIAL_RESIDUAL: Long = 2_500_000L
    }

    /** A committed single-round plan (round 1) plus the residual that round leaves behind. */
    private fun round1Driver(): MigrationSimDriver {
        val driver = MigrationSimDriver()
        driver.seedPlan(
            preparations =
                listOf(
                    MigrationSimDriver.SimPrep(id = PREP_ID, layer = 0, scheduledHeight = ANCHOR - 20L),
                ),
            transfers =
                listOf(
                    MigrationSimDriver.SimTransfer(
                        id = TRANSFER_ID,
                        scheduledHeight = ANCHOR + 5L,
                        anchorBoundary = ANCHOR,
                        dependsOn = listOf(PREP_ID),
                    ),
                ),
            startTip = ANCHOR - 20L,
        )
        driver.mine(id = PREP_ID, height = ANCHOR - 2L)
        driver.setTip(ANCHOR + 10L)
        // The residual the FULL balance leaves once round 1 is committed: a run cap's worth is in
        // round 1's transfer, the rest is still migratable.
        driver.setMigratableResidual(zatoshi = INITIAL_RESIDUAL, perRunCap = PER_RUN_CAP)
        return driver
    }

    @Test
    fun `residual above the per-run reach and above dust indicates another round, dust ends the campaign`() =
        runTest {
            val driver = round1Driver()
            val sdk = driver.sdk
            val opts = NetworkPrivacyOptions(useTor = false)

            // Baseline: the stateless preview sees a balance needing THREE runs.
            assertEquals(3, sdk.estimateMigrationRunCount(), "2_500_000 / 1_000_000 = 3 runs.")
            assertEquals(DUST, sdk.migrationDustThresholdZatoshi())

            // ── Round 1 completes: prove + broadcast the round's single transfer. ──
            sdk.finalizeReadyTransfers()
            val exec = sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(exec is cash.z.ecc.android.sdk.TransferAttemptOutcome.Executed)
            // The engine reports this run's transfers all sent → Complete-with-residual: the RUN is
            // complete, but a migratable balance remains (the multi-round distinction the app keys on).
            assertTrue(sdk.getMigrationState() is MigrationState.Complete, "round 1's run finished.")

            // Continuation decision after round 1: one run's worth was consumed by round 1's transfer.
            driver.setMigratableResidual(zatoshi = INITIAL_RESIDUAL - PER_RUN_CAP) // 1_500_000
            val afterRound1 = sdk.estimateMigrationRunCount()!!
            assertEquals(2, afterRound1, "1_500_000 / 1_000_000 = 2 runs still to go.")
            assertTrue(
                anotherRoundNeeded(sdk),
                "residual 1_500_000 > dust 10_000 and needs >0 further runs — round 2 expected.",
            )

            // ── Round 2 completes: another run's worth clears. ──
            driver.setMigratableResidual(zatoshi = INITIAL_RESIDUAL - 2 * PER_RUN_CAP) // 500_000
            val afterRound2 = sdk.estimateMigrationRunCount()!!
            assertEquals(1, afterRound2, "500_000 still needs one more run.")
            assertTrue(anotherRoundNeeded(sdk), "500_000 > dust — round 3 expected.")

            // ── Round 3 clears all but dust. ──
            driver.setMigratableResidual(zatoshi = DUST - 1L) // 9_999 — below the dust threshold
            assertEquals(
                1,
                sdk.estimateMigrationRunCount(),
                "a sub-dust residual still counts as 1 run to the raw estimate…",
            )
            assertFalse(
                anotherRoundNeeded(sdk),
                "…but the app gates on the dust threshold: 9_999 < 10_000 is negligible — campaign ends.",
            )

            // Exactly-zero residual is unambiguously done.
            driver.setMigratableResidual(zatoshi = 0L)
            assertEquals(0, sdk.estimateMigrationRunCount())
            assertFalse(anotherRoundNeeded(sdk))
        }

    /**
     * The continuation decision the app makes between Keystone rounds: another round is needed only
     * when the migratable residual is both non-dust AND large enough to require at least one further
     * run. Mirrors "residual above the migratable minimum → propose the next round".
     */
    private suspend fun anotherRoundNeeded(sdk: FakeOrchardMigrationSdk): Boolean {
        val residual = sdk.migratableOrchardZatoshi
        val dust = sdk.migrationDustThresholdZatoshi()
        val runs = sdk.estimateMigrationRunCount() ?: 0
        return residual >= dust && runs > 0
    }
}
