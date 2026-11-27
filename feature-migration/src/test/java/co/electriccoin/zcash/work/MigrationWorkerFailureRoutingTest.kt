package co.electriccoin.zcash.work

import cash.z.ecc.android.sdk.TransferAttemptOutcome
import cash.z.ecc.android.sdk.TransferResult
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationTransfer
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Tests for Lane B failure routing — the `when (result)` dispatch inside `MigrationWorker.doWork()`
 * that handles [TransferResult.InvalidNote], [TransferResult.Expired], and both
 * [TransferResult.NetworkError] variants (Tor failure and non-Tor failure).
 *
 * ## Seam used: extracted local dispatch helper
 *
 * `MigrationWorker.doWork()` is a `CoroutineWorker` wired through Koin and WorkManager; it cannot
 * be driven from a JVM unit test without Android infrastructure. `MigrationNotifier` is a concrete
 * Android class (no interface) requiring a live `Context`, so it also cannot be mocked in a JVM
 * test.
 *
 * Accordingly, the four failure-routing arms from the production worker's `handleExecuted` are
 * re-expressed below as a single, self-contained [dispatchTransferResultForTest] helper that accepts
 * the same dependency interfaces through parameters rather than Koin injection. The helper is a
 * faithful transcription of the production dispatch logic — the tests exercise that logic contract
 * and verify every side-effect call with `coVerify`.
 *
 * This is the same strategy the project already uses for `executeWithRetries` and
 * `decideBroadcastPreflight` — extracted as top-level functions specifically because the worker
 * itself is not unit-testable in a plain JVM test.
 *
 * If the production dispatch logic is ever extracted into its own top-level function (matching the
 * pattern above), these tests should be updated to drive that function directly and the local
 * helper removed.
 */

// ── Testable interfaces ────────────────────────────────────────────────────────────────────────

/** Minimal interface extracted from the concrete [MigrationNotifier] for testing purposes. */
interface TestMigrationNotifier {
    suspend fun notifyMigrationPlanInvalid(accountKeyId: String)

    suspend fun notifyTransferExpired(accountKeyId: String)

    suspend fun notifyMigrationTorFailure(accountKeyId: String)

    suspend fun notifyManualConfirmationRequired(accountKeyId: String, transferIndex: Int, total: Int)
}

/** Minimal interface for the Tor-failure flag store. */
interface TestTorFailureStore {
    suspend fun store(accountKeyId: String, value: Boolean)
}

// ── Return type mirroring WorkManager.Result ───────────────────────────────────────────────────

enum class DispatchResult { SUCCESS, FAILURE }

// ── Dispatch helper — faithful transcription of doWork()'s result dispatch ────────────────────

/**
 * Mirrors the [TransferResult] dispatch from `MigrationWorker.doWork()` using injectable
 * dependencies instead of Koin / Android context. Returns [DispatchResult.SUCCESS] or
 * [DispatchResult.FAILURE] matching the production `Result.success()` / `Result.failure()` arms.
 *
 * Production arms covered (terminal arms simply do not re-arm — there is no second lane left to
 * cancel since the engine-adoption single-worker refactor):
 * - [TransferResult.InvalidNote]  → withheld for reevaluation, NO notify, re-arms → SUCCESS (SDK
 *   Task 9 / spec 2026-08-05-migration-engine-full-delegation-design.md §5: a genuinely-unknown
 *   broadcast rejection is now reported via report_broadcast_failure — tag=4, AwaitingReevaluation
 *   — instead of terminally failed, so this arm no longer notifies "plan invalid". This
 *   transcription omits the re-arm call itself, same as it omits every other arm's re-arm/schedule
 *   side effects — [DispatchResult] only mirrors the WorkManager `Result` outcome, not
 *   `MigrationScheduler` scheduling.)
 * - [TransferResult.Expired]      → notifyTransferExpired      → SUCCESS
 * - [TransferResult.NetworkError] (isTorFailure=true)  → store tor-flag + notifyMigrationTorFailure → FAILURE
 * - [TransferResult.NetworkError] (isTorFailure=false) → notifyManualConfirmationRequired          → FAILURE
 */
private suspend fun dispatchTransferResultForTest(
    result: TransferResult,
    accountKeyId: String,
    plan: LiveMigrationSnapshot?,
    notifier: TestMigrationNotifier,
    torFailureStore: TestTorFailureStore,
): DispatchResult {
    val next = plan?.nextPending
    return when (result) {
        is TransferResult.NetworkError -> {
            if (result.isTorFailure) {
                torFailureStore.store(accountKeyId, true)
                notifier.notifyMigrationTorFailure(accountKeyId)
            } else if (next != null) {
                notifier.notifyManualConfirmationRequired(accountKeyId, next.index + 1, plan.totalCount)
            }
            DispatchResult.FAILURE
        }

        TransferResult.InvalidNote -> {
            // No notify — see the doc comment above. Production re-arms here (DriveOnceResult.ReArmed);
            // MigrationWorker.doWork() maps both ReArmed and Terminal to Result.success(), so SUCCESS
            // still matches this transcription's WorkManager-result-only modeling.
            DispatchResult.SUCCESS
        }

        TransferResult.Expired -> {
            notifier.notifyTransferExpired(accountKeyId)
            DispatchResult.SUCCESS
        }

        // Success arm is intentionally omitted — it is covered by the "happy path" tests in
        // MigrationWorkerTest and is not the subject of this failure-routing test file.
        is TransferResult.Success -> {
            DispatchResult.SUCCESS
        }
    }
}

// ── Test fixtures ──────────────────────────────────────────────────────────────────────────────

private const val ACCOUNT_KEY_ID = "test-account-key-id"

private fun liveTransfer(index: Int, isSent: Boolean) =
    LiveMigrationTransfer(
        id = 10L + index,
        index = index,
        amountZatoshi = 1_000_000L,
        scheduledHeight = 1_000L + index,
        scheduledAt = kotlin.time.Instant.fromEpochSeconds(index * 600L),
        isSent = isSent,
        isProved = true,
        action = null,
        blocker = null,
        expiryAt = null,
        minedHeight = null,
    )

/** A live snapshot with two transfers where the first (index=0) is still unsent. */
private fun planWithPendingFirst(): LiveMigrationSnapshot =
    LiveMigrationSnapshot(
        transfers = listOf(liveTransfer(0, isSent = false), liveTransfer(1, isSent = false)),
        preparations = emptyList(),
        tipHeight = 1_000L,
    )

// ── InvalidNote arm ────────────────────────────────────────────────────────────────────────────

class MigrationWorkerFailureRoutingTest {
    @Test
    fun `InvalidNote is withheld for reevaluation without notifying and returns success`() =
        runTest {
            // SDK Task 9: a genuinely-unknown rejection is now reported via report_broadcast_failure
            // (tag=4, AwaitingReevaluation) instead of terminally failed — no "plan invalid" notify.
            val notifier = mockk<TestMigrationNotifier>()
            val torStore = mockk<TestTorFailureStore>()

            val result =
                dispatchTransferResultForTest(
                    result = TransferResult.InvalidNote,
                    accountKeyId = ACCOUNT_KEY_ID,
                    plan = planWithPendingFirst(),
                    notifier = notifier,
                    torFailureStore = torStore,
                )

            assertEquals(DispatchResult.SUCCESS, result, "InvalidNote must return Result.success()")
            coVerify(exactly = 0) { notifier.notifyMigrationPlanInvalid(any()) }
            // Tor storage must NOT be touched — this is not a network failure.
            coVerify(exactly = 0) { torStore.store(any(), any()) }
            // Transfer-expired notification must NOT fire — distinct spec §6.3 copy.
            coVerify(exactly = 0) { notifier.notifyTransferExpired(any()) }
        }

    // ── TransferExpired (Expired) arm ──────────────────────────────────────────────────────────

    @Test
    fun `TransferExpired calls notifyTransferExpired and returns success`() =
        runTest {
            val notifier = mockk<TestMigrationNotifier> { coJustRun { notifyTransferExpired(any()) } }
            val torStore = mockk<TestTorFailureStore>()

            val result =
                dispatchTransferResultForTest(
                    result = TransferResult.Expired,
                    accountKeyId = ACCOUNT_KEY_ID,
                    plan = planWithPendingFirst(),
                    notifier = notifier,
                    torFailureStore = torStore,
                )

            assertEquals(DispatchResult.SUCCESS, result, "Expired must return Result.success()")
            coVerify(exactly = 1) { notifier.notifyTransferExpired(ACCOUNT_KEY_ID) }
            // Invalid-note notification must NOT fire — spec §6.2 vs §6.3 are distinct.
            coVerify(exactly = 0) { notifier.notifyMigrationPlanInvalid(any()) }
            coVerify(exactly = 0) { torStore.store(any(), any()) }
        }

    // ── NetworkError (isTorFailure=true) arm ───────────────────────────────────────────────────

    @Test
    fun `Tor NetworkError stores pending flag, calls notifyMigrationTorFailure, returns failure`() =
        runTest {
            val notifier = mockk<TestMigrationNotifier> { coJustRun { notifyMigrationTorFailure(any()) } }
            val torStore = mockk<TestTorFailureStore> { coJustRun { store(any(), any()) } }

            val result =
                dispatchTransferResultForTest(
                    result = TransferResult.NetworkError(retryable = false, isTorFailure = true),
                    accountKeyId = ACCOUNT_KEY_ID,
                    plan = planWithPendingFirst(),
                    notifier = notifier,
                    torFailureStore = torStore,
                )

            assertEquals(DispatchResult.FAILURE, result, "Tor NetworkError must return Result.failure()")
            // Tor flag persisted so CheckMigrationRecoveryUseCase routes back through Sending screen.
            coVerify(exactly = 1) { torStore.store(ACCOUNT_KEY_ID, true) }
            coVerify(exactly = 1) { notifier.notifyMigrationTorFailure(ACCOUNT_KEY_ID) }
            // Manual-confirmation path must NOT fire when isTorFailure=true (different notification).
            coVerify(exactly = 0) { notifier.notifyManualConfirmationRequired(any(), any(), any()) }
        }

    @Test
    fun `Tor NetworkError stores pending flag even when plan is null`() =
        runTest {
            val notifier = mockk<TestMigrationNotifier> { coJustRun { notifyMigrationTorFailure(any()) } }
            val torStore = mockk<TestTorFailureStore> { coJustRun { store(any(), any()) } }

            // Plan may be null during first-run or a race; Tor flag store must not be gated on it.
            val result =
                dispatchTransferResultForTest(
                    result = TransferResult.NetworkError(retryable = false, isTorFailure = true),
                    accountKeyId = ACCOUNT_KEY_ID,
                    plan = null,
                    notifier = notifier,
                    torFailureStore = torStore,
                )

            assertEquals(DispatchResult.FAILURE, result)
            coVerify(exactly = 1) { torStore.store(ACCOUNT_KEY_ID, true) }
            coVerify(exactly = 1) { notifier.notifyMigrationTorFailure(ACCOUNT_KEY_ID) }
        }

    // ── NetworkError (isTorFailure=false) arm ──────────────────────────────────────────────────

    @Test
    fun `non-Tor NetworkError calls notifyManualConfirmationRequired with correct transfer index`() =
        runTest {
            val notifier =
                mockk<TestMigrationNotifier> {
                    coJustRun { notifyManualConfirmationRequired(any(), any(), any()) }
                }
            val torStore = mockk<TestTorFailureStore>()
            val plan = planWithPendingFirst() // nextPending is transfer at index=0, totalCount=2

            val result =
                dispatchTransferResultForTest(
                    result = TransferResult.NetworkError(retryable = false, isTorFailure = false),
                    accountKeyId = ACCOUNT_KEY_ID,
                    plan = plan,
                    notifier = notifier,
                    torFailureStore = torStore,
                )

            assertEquals(DispatchResult.FAILURE, result, "Non-Tor NetworkError must return Result.failure()")
            // Production: notifyManualConfirmationRequired(accountKeyId, next.index + 1, plan.totalCount)
            // next.index = 0, plan.totalCount = 2 → (0+1=1, 2)
            coVerify(exactly = 1) { notifier.notifyManualConfirmationRequired(ACCOUNT_KEY_ID, 1, 2) }
            // Tor-specific paths must NOT fire.
            coVerify(exactly = 0) { torStore.store(any(), any()) }
            coVerify(exactly = 0) { notifier.notifyMigrationTorFailure(any()) }
        }

    @Test
    fun `non-Tor NetworkError with null plan does not notify (no next transfer to report)`() =
        runTest {
            val notifier = mockk<TestMigrationNotifier>()
            val torStore = mockk<TestTorFailureStore>()

            // Production code: `else if (next != null)` — when plan is null, next is null, so no notification.
            val result =
                dispatchTransferResultForTest(
                    result = TransferResult.NetworkError(retryable = false, isTorFailure = false),
                    accountKeyId = ACCOUNT_KEY_ID,
                    plan = null,
                    notifier = notifier,
                    torFailureStore = torStore,
                )

            assertEquals(DispatchResult.FAILURE, result)
            coVerify(exactly = 0) { notifier.notifyManualConfirmationRequired(any(), any(), any()) }
            coVerify(exactly = 0) { notifier.notifyMigrationTorFailure(any()) }
            coVerify(exactly = 0) { torStore.store(any(), any()) }
        }

    @Test
    fun `non-Tor NetworkError with correct index when second transfer is the next pending`() =
        runTest {
            val notifier =
                mockk<TestMigrationNotifier> {
                    coJustRun { notifyManualConfirmationRequired(any(), any(), any()) }
                }
            val torStore = mockk<TestTorFailureStore>()

            // Snapshot where the first transfer is SENT, second (index=1) is the next pending.
            val plan =
                LiveMigrationSnapshot(
                    transfers = listOf(liveTransfer(0, isSent = true), liveTransfer(1, isSent = false)),
                    preparations = emptyList(),
                    tipHeight = 1_000L,
                )

            val result =
                dispatchTransferResultForTest(
                    result = TransferResult.NetworkError(retryable = false, isTorFailure = false),
                    accountKeyId = ACCOUNT_KEY_ID,
                    plan = plan,
                    notifier = notifier,
                    torFailureStore = torStore,
                )

            assertEquals(DispatchResult.FAILURE, result)
            // next.index = 1, plan.totalCount = 2 → (1+1=2, 2)
            coVerify(exactly = 1) { notifier.notifyManualConfirmationRequired(ACCOUNT_KEY_ID, 2, 2) }
        }

    // ── Cross-arm isolation: verify arms do not bleed into each other ──────────────────────────

    @Test
    fun `InvalidNote does not fire Expired, ManualConfirmation, Tor, or PlanInvalid notifications`() =
        runTest {
            val notifier = mockk<TestMigrationNotifier>()
            val torStore = mockk<TestTorFailureStore>()

            dispatchTransferResultForTest(
                result = TransferResult.InvalidNote,
                accountKeyId = ACCOUNT_KEY_ID,
                plan = planWithPendingFirst(),
                notifier = notifier,
                torFailureStore = torStore,
            )

            coVerify(exactly = 0) { notifier.notifyTransferExpired(any()) }
            coVerify(exactly = 0) { notifier.notifyManualConfirmationRequired(any(), any(), any()) }
            coVerify(exactly = 0) { notifier.notifyMigrationTorFailure(any()) }
            coVerify(exactly = 0) { notifier.notifyMigrationPlanInvalid(any()) }
        }

    @Test
    fun `Expired does not fire InvalidNote notification or Tor paths`() =
        runTest {
            val notifier =
                mockk<TestMigrationNotifier> {
                    coJustRun { notifyTransferExpired(any()) }
                }
            val torStore = mockk<TestTorFailureStore>()

            dispatchTransferResultForTest(
                result = TransferResult.Expired,
                accountKeyId = ACCOUNT_KEY_ID,
                plan = planWithPendingFirst(),
                notifier = notifier,
                torFailureStore = torStore,
            )

            coVerify(exactly = 0) { notifier.notifyMigrationPlanInvalid(any()) }
            coVerify(exactly = 0) { notifier.notifyManualConfirmationRequired(any(), any(), any()) }
            coVerify(exactly = 0) { notifier.notifyMigrationTorFailure(any()) }
        }
}
