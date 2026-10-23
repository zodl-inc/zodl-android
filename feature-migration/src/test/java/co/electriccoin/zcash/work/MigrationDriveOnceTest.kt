package co.electriccoin.zcash.work

import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationBlocker
import cash.z.ecc.android.sdk.MigrationPeek
import cash.z.ecc.android.sdk.MigrationProgress
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.MigrationStepKind
import cash.z.ecc.android.sdk.MigrationSyncWakeup
import cash.z.ecc.android.sdk.MigrationTransferState
import cash.z.ecc.android.sdk.MigrationTransferStates
import cash.z.ecc.android.sdk.TransferAttemptOutcome
import cash.z.ecc.android.sdk.TransferResult
import cash.z.ecc.android.sdk.model.TransactionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MigrationDriveOnceTest {
    // ── executeWithRetries ────────────────────────────────────────────────────

    @Test
    fun `a Success on the first attempt does not retry`() =
        runTest {
            var callCount = 0
            val result =
                executeWithRetries(retryDelayMs = 0) {
                    callCount++
                    TransferAttemptOutcome.Executed(TransferResult.Success(TransactionId.new("txid".toByteArray())))
                }

            assertIs<TransferAttemptOutcome.Executed>(result)
            assertIs<TransferResult.Success>(result.result)
            assertEquals(1, callCount)
        }

    @Test
    fun `a retryable NetworkError retries up to maxAttempts then stops`() =
        runTest {
            var callCount = 0
            val result =
                executeWithRetries(maxAttempts = 3, retryDelayMs = 0) {
                    callCount++
                    TransferAttemptOutcome.Executed(TransferResult.NetworkError(retryable = true))
                }

            assertIs<TransferAttemptOutcome.Executed>(result)
            assertIs<TransferResult.NetworkError>(result.result)
            assertEquals(3, callCount)
        }

    @Test
    fun `a non-retryable NetworkError stops immediately without exhausting maxAttempts`() =
        runTest {
            var callCount = 0
            val result =
                executeWithRetries(maxAttempts = 3, retryDelayMs = 0) {
                    callCount++
                    TransferAttemptOutcome.Executed(TransferResult.NetworkError(retryable = false))
                }

            assertIs<TransferAttemptOutcome.Executed>(result)
            assertIs<TransferResult.NetworkError>(result.result)
            assertEquals(1, callCount)
        }

    @Test
    fun `a NothingDue result stops immediately without retrying`() =
        runTest {
            var callCount = 0
            val result =
                executeWithRetries(maxAttempts = 3, retryDelayMs = 0) {
                    callCount++
                    TransferAttemptOutcome.NothingDue
                }

            assertIs<TransferAttemptOutcome.NothingDue>(result)
            assertEquals(1, callCount)
        }

    @Test
    fun `an AwaitingProof result stops immediately without retrying`() =
        runTest {
            var callCount = 0
            val result =
                executeWithRetries(maxAttempts = 3, retryDelayMs = 0) {
                    callCount++
                    TransferAttemptOutcome.AwaitingProof(1L)
                }

            assertIs<TransferAttemptOutcome.AwaitingProof>(result)
            assertEquals(1, callCount)
        }

    @Test
    fun `a retryable NetworkError that later succeeds stops as soon as it succeeds`() =
        runTest {
            var callCount = 0
            val result =
                executeWithRetries(maxAttempts = 3, retryDelayMs = 0) {
                    callCount++
                    if (callCount < 2) {
                        TransferAttemptOutcome.Executed(TransferResult.NetworkError(retryable = true))
                    } else {
                        TransferAttemptOutcome.Executed(TransferResult.Success(TransactionId.new("txid".toByteArray())))
                    }
                }

            assertIs<TransferAttemptOutcome.Executed>(result)
            assertIs<TransferResult.Success>(result.result)
            assertEquals(2, callCount)
        }

    // ── decideBroadcastPreflight ──────────────────────────────────────────────

    @Test
    fun `a broadcast run defers inside the quiet gap`() {
        assertEquals(
            BroadcastPreflight.DEFER,
            decideBroadcastPreflight(
                synchronizerSyncing = false,
                nowEpochSeconds = 1000,
                lastNetworkActivityEpochSeconds = 700,
                privacyBufferSeconds = 600,
            )
        )
    }

    @Test
    fun `a broadcast run proceeds when all sources quiet past the gap`() {
        assertEquals(
            BroadcastPreflight.BROADCAST,
            decideBroadcastPreflight(false, 1000, 100, 600)
        )
    }

    @Test
    fun `a broadcast run proceeds when no sync ever happened`() {
        assertEquals(
            BroadcastPreflight.BROADCAST,
            decideBroadcastPreflight(false, 1000, null, 600)
        )
    }

    @Test
    fun `a broadcast run defers while the synchronizer is syncing`() {
        assertEquals(
            BroadcastPreflight.DEFER,
            decideBroadcastPreflight(
                synchronizerSyncing = true,
                nowEpochSeconds = 1000,
                lastNetworkActivityEpochSeconds = null,
                privacyBufferSeconds = 600,
            )
        )
    }

    @Test
    fun `a broadcast run proceeds when the gap exactly elapsed`() {
        // now=1000, last=400, buffer=600 → gap=600, exactly elapsed → BROADCAST
        assertEquals(
            BroadcastPreflight.BROADCAST,
            decideBroadcastPreflight(false, 1000, 400, 600)
        )
    }

    // ── broadcastDueByEstimate (estimated-tip acceleration) ───────────────────

    private fun tx(
        id: Long,
        scheduled: Long,
        isProved: Boolean = true,
        isSent: Boolean = false,
        blocker: MigrationBlocker? = null,
    ) = MigrationTransferState(
        id = id,
        isTransfer = true,
        isSent = isSent,
        isProved = isProved,
        scheduledHeight = scheduled,
        anchorBoundaryHeight = scheduled - 10,
        blocker = blocker,
    )

    private fun states(vararg transfers: MigrationTransferState) =
        MigrationTransferStates(transfers = transfers.toList(), tipHeight = 100L)

    @Test
    fun `a proved unsent transfer due by estimate accelerates the broadcast`() {
        assertTrue(broadcastDueByEstimate(states(tx(1, scheduled = 150)), estimatedTip = 150))
    }

    @Test
    fun `an unproved transfer never accelerates`() {
        assertFalse(broadcastDueByEstimate(states(tx(1, scheduled = 150, isProved = false)), estimatedTip = 200))
    }

    @Test
    fun `a not-yet-due transfer never accelerates`() {
        assertFalse(broadcastDueByEstimate(states(tx(1, scheduled = 150)), estimatedTip = 149))
    }

    @Test
    fun `an unprovable-anchor transfer never accelerates`() {
        assertFalse(
            broadcastDueByEstimate(
                states(tx(1, scheduled = 150, blocker = MigrationBlocker.UNPROVABLE_ANCHOR)),
                estimatedTip = 200,
            )
        )
    }

    @Test
    fun `an unavailable estimate never accelerates`() {
        assertFalse(broadcastDueByEstimate(states(tx(1, scheduled = 150)), estimatedTip = -1))
    }

    // ── computeEngineWakeDelay (core sync call §2.4's end-state: the engine's own peek-ahead,
    //    with NO home-grown merge of a sync-wakeup schedule and a due-height scan) ────────────

    @Test
    fun `the peek height converts to a wall-clock delay at the measured block rate`() {
        val delay =
            computeEngineWakeDelay(
                est = 100,
                secondsPerBlock = 10,
                nu7ActivationHeight = null,
                peek = MigrationPeek(height = 250, kind = MigrationStepKind.BROADCAST),
            )
        // (250-100)*10 = 1500s.
        assertEquals(1500.seconds, delay)
    }

    @Test
    fun `a past or near peek height floors at the minimum re-arm`() {
        val delay =
            computeEngineWakeDelay(
                est = 100,
                secondsPerBlock = 10,
                nu7ActivationHeight = null,
                peek = MigrationPeek(height = 90, kind = MigrationStepKind.PROVE),
            )
        assertEquals(MIN_REARM_SECONDS.seconds, delay)
    }

    @Test
    fun `an unavailable estimate falls back to the cadence`() {
        assertNull(
            computeEngineWakeDelay(
                est = -1,
                secondsPerBlock = 10,
                nu7ActivationHeight = null,
                peek = MigrationPeek(height = 300, kind = MigrationStepKind.BROADCAST),
            )
        )
    }

    @Test
    fun `no peek falls back to the cadence`() {
        assertNull(computeEngineWakeDelay(est = 100, secondsPerBlock = 10, nu7ActivationHeight = null, peek = null))
    }

    // ── nextWake (engine peek folded with the app privacy gap; §3's gap term is untouched by
    //    the peek-ahead adoption — see its doc) ────────────────────────────────────────────

    @Test
    fun `a ready-but-gapped broadcast re-arms to the quiet expiry`() {
        // proved, unsent transfer due at est=1000; synced 60s ago, buffer 180s -> 120s remaining.
        val delay =
            nextWake(
                states = states(tx(1, scheduled = 1000)),
                est = 1000,
                secondsPerBlock = 3,
                lastActivityEpochSeconds = 1_000_000L,
                privacyBufferSeconds = 180L,
                nowEpochSeconds = 1_000_060L,
                nu7ActivationHeight = null,
                peek = null,
            )
        assertEquals(120.seconds, delay)
    }

    @Test
    fun `an earlier engine peek wins over the gap`() {
        val delay =
            nextWake(
                states = states(tx(1, scheduled = 1000)),
                est = 1000,
                secondsPerBlock = 1,
                lastActivityEpochSeconds = 1_000_000L,
                privacyBufferSeconds = 600L,
                nowEpochSeconds = 1_000_100L,
                nu7ActivationHeight = null,
                peek = MigrationPeek(height = 1100, kind = MigrationStepKind.PROVE),
            )
        // engine peek at 1100: (1100-1000)*1 = 100s < 500s gap remaining -> 100s wins.
        assertEquals(100.seconds, delay)
    }

    @Test
    fun `no broadcast-ready transfer means the gap term does not apply`() {
        val delay =
            nextWake(
                states = states(tx(1, scheduled = 2000, isProved = false, blocker = MigrationBlocker.ANCHOR_BOUNDARY)),
                est = 1000,
                secondsPerBlock = 3,
                lastActivityEpochSeconds = 1_000_000L,
                privacyBufferSeconds = 180L,
                nowEpochSeconds = 1_000_178L, // if the gap applied, only 2s would remain
                nu7ActivationHeight = null,
                peek = MigrationPeek(height = 2000, kind = MigrationStepKind.PROVE),
            )
        // Unproved -> not broadcast-ready -> gap term is inert; falls through to the engine
        // peek's own delay (2000-1000)*3 = 3000s, wildly different from the near-elapsed gap.
        assertEquals(3000.seconds, delay)
    }

    @Test
    fun `a gap already elapsed re-arms at zero, not negative`() {
        val delay =
            nextWake(
                states = states(tx(1, scheduled = 1000)),
                est = 1000,
                secondsPerBlock = 3,
                lastActivityEpochSeconds = 1_000_000L,
                privacyBufferSeconds = 180L,
                nowEpochSeconds = 1_000_500L, // 500s since last activity, buffer only 180s
                nu7ActivationHeight = null,
                peek = null,
            )
        assertEquals(0.seconds, delay)
    }

    // ── isMigrationActuallyComplete (Task 7: Superseded-vs-Complete disambiguation) ──────────
    // nextStep()'s STEP_COMPLETE fires for EVERY terminal status (state.rs's is_terminal()
    // short-circuit) — genuinely Complete, but also a migration Step 7's nextStep() just marked
    // Superseded (or one that's Failed). A Superseded migration maps to
    // MigrationState.ReadyToPropose (see derive_migration_state in migration.rs), never
    // MigrationState.Complete — this is exactly what handleCompleteStep gates completeRun's
    // effects on.

    @Test
    fun `a genuinely complete migration state is treated as complete`() {
        assertTrue(isMigrationActuallyComplete(MigrationState.Complete))
    }

    @Test
    fun `a superseded migration reports ReadyToPropose, which is not treated as complete`() {
        // The Superseded status maps here — a replan-superseded migration accepts a replacement
        // commit immediately, exactly ReadyToPropose's meaning, even though a prior (now
        // superseded) migration record still exists in the store.
        assertFalse(isMigrationActuallyComplete(MigrationState.ReadyToPropose))
    }

    @Test
    fun `a RequiresAttention migration is not treated as complete`() {
        assertFalse(isMigrationActuallyComplete(MigrationState.RequiresAttention(AttentionReason.TransferExpired)))
    }

    @Test
    fun `NotStarted, SplitPendingConfirmation, and InProgress are not treated as complete`() {
        assertFalse(isMigrationActuallyComplete(MigrationState.NotStarted))
        assertFalse(isMigrationActuallyComplete(MigrationState.SplitPendingConfirmation))
        assertFalse(isMigrationActuallyComplete(MigrationState.InProgress(MigrationProgress(3, 10, null))))
    }

    // ── waitingDisposition (what a genuine engine Waiting resolves to) ────────

    @Test
    fun `all sent means a completion sweep`() {
        assertEquals(
            WaitingDisposition.COMPLETION_SWEEP,
            waitingDisposition(allSent = true, hasUnprovableBlocker = false)
        )
    }

    @Test
    fun `an unprovable blocker with unsent work surfaces the blocker`() {
        assertEquals(
            WaitingDisposition.SURFACE_UNPROVABLE,
            waitingDisposition(allSent = false, hasUnprovableBlocker = true)
        )
    }

    @Test
    fun `neither condition just re-arms`() {
        assertEquals(WaitingDisposition.RE_ARM, waitingDisposition(allSent = false, hasUnprovableBlocker = false))
    }

    @Test
    fun `all sent takes priority over an unprovable blocker`() {
        assertEquals(
            WaitingDisposition.COMPLETION_SWEEP,
            waitingDisposition(allSent = true, hasUnprovableBlocker = true)
        )
    }

    // ── NU7 characterization pins (MOB-2287): pre-NU7 behaviour at the 75 s/block rate. ──

    @Test
    fun `characterization pin of pre-NU7 75s - engine wake delay is block delta times 75`() {
        // NU7: revisit - spans activation once helper is height-aware
        val delay =
            computeEngineWakeDelay(
                est = 1_000,
                secondsPerBlock = 75,
                nu7ActivationHeight = null,
                peek = MigrationPeek(height = 1_100, kind = MigrationStepKind.BROADCAST),
            )
        assertEquals((100 * 75).seconds, delay)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - engine wake delay floors at 60s for 0 and 1 block`() {
        // NU7: revisit - spans activation once helper is height-aware
        // 1 block * 75 s = 75 s is above the floor; 0 blocks (due now) and past peeks floor to 60 s.
        val peekNow = MigrationPeek(height = 1_000, kind = MigrationStepKind.PROVE)
        assertEquals(
            60.seconds,
            computeEngineWakeDelay(
                est = 1_000,
                secondsPerBlock = 75,
                nu7ActivationHeight = null,
                peek = peekNow
            )
        )
        val peekNext = MigrationPeek(height = 1_001, kind = MigrationStepKind.PROVE)
        assertEquals(
            75.seconds,
            computeEngineWakeDelay(
                est = 1_000,
                secondsPerBlock = 75,
                nu7ActivationHeight = null,
                peek = peekNext
            )
        )
    }

    @Test
    fun `characterization pin of pre-NU7 75s - nextWake takes the engine peek at 75s per block when no gap`() {
        // NU7: revisit - spans activation once helper is height-aware
        val delay =
            nextWake(
                states = states(tx(1, scheduled = 5_000)),
                est = 1_000,
                secondsPerBlock = 75,
                lastActivityEpochSeconds = null,
                privacyBufferSeconds = 180L,
                nowEpochSeconds = 1_000_000L,
                nu7ActivationHeight = null,
                peek = MigrationPeek(height = 1_010, kind = MigrationStepKind.PROVE),
            )
        assertEquals((10 * 75).seconds, delay)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - rearm and cadence constants`() {
        assertEquals(60L, MIN_REARM_SECONDS)
        assertEquals(60.seconds, REARM_FLOOR)
        assertEquals(1.seconds, PREP_FAST_TRACK_REARM)
        assertEquals(180.seconds, SYNC_TIMEOUT)
        // Fallback cadence: 5 min testnet / 60 min mainnet; unit tests run under whichever flavor is active.
        val cadence = migrationCadence()
        assertTrue(cadence == 300.seconds || cadence == 3_600.seconds, "unexpected cadence $cadence")
    }

    @Test
    fun `NU7 - engine wake delay splits at the activation height`() {
        // est 1_000, peek 1_100, activation 1_040: 39 blocks at 75 s then 61 at 25 s.
        val delay =
            computeEngineWakeDelay(
                est = 1_000,
                secondsPerBlock = 75,
                peek = MigrationPeek(height = 1_100, kind = MigrationStepKind.BROADCAST),
                nu7ActivationHeight = 1_040,
            )
        assertEquals((39 * 75 + 61 * 25).seconds, delay)
    }

    @Test
    fun `NU7 - engine wake delay still floors at 60s`() {
        val peek = MigrationPeek(height = 1_001, kind = MigrationStepKind.PROVE)
        assertEquals(
            60.seconds,
            computeEngineWakeDelay(est = 1_000, secondsPerBlock = 25, peek = peek, nu7ActivationHeight = 500)
        )
    }
}
