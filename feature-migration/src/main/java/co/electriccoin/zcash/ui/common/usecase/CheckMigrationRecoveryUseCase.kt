package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cash.z.ecc.android.sdk.MigrationNextAction
import cash.z.ecc.android.sdk.MigrationState
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.screen.home.HomeArgs
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingArgs
import co.electriccoin.zcash.work.MigrationLiveDriver
import co.electriccoin.zcash.work.MigrationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Single source of truth for migration re-entry routing AND for starting/reviving migration
 * execution on app-lifecycle events — MainActivity's onStart() and RootNavGraph's
 * secretState-driven redirect both delegate here instead of calling the SDK checks directly, so
 * the two never drift out of sync with each other or with this ordering. Cheap and idempotent
 * (NavigationRouter dedupes identical commands; the driver-start/worker-revival block below is
 * per-account idempotent too).
 *
 * After Task 6 (remove app-open auto-navigation), the ONLY auto-navigation that fires here is the
 * Tor-failure branch: a pending background Tor failure routes to the Sending screen so that
 * MigrationSendingVM's init{} reproduces the exact condition, using its own existing routing to
 * resolve or re-surface the failure. That branch stays scoped to the currently SELECTED account —
 * it's about what to show the user right now, unlike the driver-start/worker-revival block below.
 *
 * All other migration states (RequiresAttention, ReadyToSend, Overdue, Complete, the
 * unprovable-anchor attention state) are reachable exclusively via the home banner + button
 * (HomeVM.onMigrationMessageClick), preventing repeated screen hijacking on every launch during a
 * healthy migration.
 *
 * The driver-start/worker-revival block enumerates EVERY account (2026-08-0X), not just the
 * selected one: `MigrationLiveDriver`/`MigrationDriveOnce.DRIVE_LOCK` are per-account already, and
 * a migration committed for a Keystone account keeps running whether or not that account is
 * currently selected in the UI — this is the sole place migration execution gets triggered from
 * (besides `FinalizeMigrationScheduleUseCase`'s own post-commit start for the account that was
 * just committed), so it must not silently skip a non-selected account. It and the
 * stale-write-ahead-plan clear are NOT navigation — they are retained unchanged in spirit, just
 * widened from one account to all of them.
 */
class CheckMigrationRecoveryUseCase(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val navigationRouter: NavigationRouter,
    private val pendingMigrationTorFailureStorageProvider: PendingMigrationTorFailureStorageProvider,
    private val accountDataSource: AccountDataSource,
    private val context: Context,
    private val migrationLiveDriver: MigrationLiveDriver,
    /** Extracted for testability — production default checks WorkManager. */
    private val getWorkerRunState: suspend (String) -> MigrationWorkerRunState = {
        migrationWorkerRunState(context, it)
    },
    /**
     * Extracted for testability — production default enqueues an immediate WorkManager run
     * (`Duration.ZERO`), replacing whatever delay the worker last armed for itself.
     */
    private val scheduleNow: suspend (String) -> Unit = { MigrationScheduler(context).schedule(it, Duration.ZERO) },
) {
    suspend operator fun invoke() {
        // Three independent triggers exist (MainActivity.onStart, RootNavGraph unlock, and any
        // future caller); without a throttle they cascade — each replaceAll builds a fresh Home
        // entry whose composition can re-trigger recovery, observed live as 7 redirects (and 3
        // duplicate catch-up shifts) within 8 seconds. One pass per window is enough: routing is
        // idempotent for the user and isSyncBlocked() protects sync regardless.
        throttleMutex.withLock {
            val elapsed = lastRunMark?.elapsedNow()
            if (elapsed != null && elapsed < RUN_THROTTLE) {
                migrationLog("MigrationRecovery: throttled (ran ${elapsed.inWholeMilliseconds}ms ago)")
                return
            }
            lastRunMark = TimeSource.Monotonic.markNow()
        }
        // No wallet YET — on a cold start this fires before the synchronizer initializes, and
        // silently consuming the throttle window here left recovery permanently ineffective
        // (observed live: 1st call = SDK null + throttle stamped, 2nd call 3s later = throttled;
        // both lanes stayed dead after a reinstall). Un-stamp the throttle so the next trigger
        // (foreground/unlock/onStart all re-fire) gets a real attempt once the wallet is up.
        // getOrchardMigrationSdk() itself now throws rather than returning null on a wallet-less
        // install (it's a precondition for every migration flow proper), so this gate has to run
        // BEFORE calling it — this is the one genuinely wallet-independent call site the SDK
        // use case's own kdoc calls out.
        if (persistableWalletProvider.getPersistableWallet() == null) {
            throttleMutex.withLock { lastRunMark = null }
            migrationLog("MigrationRecovery: SDK not ready — will retry on next trigger.")
            return
        }
        val sdk = getOrchardMigrationSdk()

        // The whole engine-reading/routing body below is guarded (2026-08-07): a "database is
        // locked" throw here used to be unguarded and would crash the app-open/foreground-return
        // path — this use case drives navigation, not a screen with its own LCE error state to
        // fall back on. On failure, un-stamp the throttle (mirrors the wallet-not-ready branch
        // above) so a transient read failure doesn't cost a silent 10s window before the next
        // trigger gets a real attempt.
        runCatching {
            // Worker reconciliation + app-open acceleration, for EVERY account (2026-08-0X — was
            // selected-account-only; a Keystone account's committed migration must keep getting the
            // live-driver fast path and worker revival regardless of which account is selected in
            // the UI right now). Self-heals after process kill, device reboot, or an app upgrade
            // that cleared WorkManager state, without requiring the user to re-enter the migration
            // flow (the worker's re-arm only happens at the end of its own run and its due alarms
            // don't survive a package update — see OnMigrationSyncCompletedUseCase; duplicated here
            // because the SYNCED hook needs a synced foreground synchronizer, which a freshly
            // relaunched app may not reach for minutes).
            // Gate on the ENGINE's state, not only the app-side plan cache: the cache can be lost
            // (observed live: repository empty while the engine held a run with 8/9 broadcast and the
            // last transfer proved) and the engine is the single source of truth — a live in-progress
            // migration must always have its worker chain running.
            //
            // getMigrationStateUnreconciled(), not getMigrationState(): this router never mutates
            // (2026-08-07 read/write-separation design) — it starts the live driver below regardless,
            // which reconciles on its own first cycle, so any staleness here self-corrects immediately.
            accountDataSource.getAllAccounts().filter { it.isSpendable }.forEach { account ->
                val accountKeyId = account.sdkAccount.accountUuid.toStorageKeyId()
                val accountSdk = getOrchardMigrationSdk(accountKeyId) ?: return@forEach
                val engineInProgress = accountSdk.getMigrationStateUnreconciled() is MigrationState.InProgress
                if (engineInProgress) {
                    // The live driver is the fast path while the app is alive — starting it here
                    // covers both cold start (process was killed mid-migration) and every subsequent
                    // foreground return; it is a no-op if already running for this account. It
                    // supersedes the old "accelerate a SCHEDULED worker to run now" logic entirely:
                    // the live driver already drives the account forward on its own once running, so
                    // there is nothing left to separately nudge.
                    migrationLiveDriver.startIfNotRunning(accountKeyId)
                    when (getWorkerRunState(accountKeyId)) {
                        MigrationWorkerRunState.RUNNING, MigrationWorkerRunState.SCHEDULED -> {
                            // Nothing to do — RUNNING is already executing; SCHEDULED will either fire
                            // on its own or be superseded by the live driver's own re-arm (reArm's
                            // MigrationScheduler.schedule call), whichever comes first.
                            migrationLog(
                                "MigrationRecovery: migration worker already active (RUNNING or SCHEDULED) " +
                                    "for $accountKeyId — nothing to do."
                            )
                        }

                        MigrationWorkerRunState.ABSENT -> {
                            // Revival: recovers the DURABLE background chain after process kill, device
                            // reboot, or an app upgrade that cleared WorkManager state — the live driver
                            // covers speed while alive, but only the worker chain survives process death.
                            migrationLog(
                                "MigrationRecovery: migration worker absent for $accountKeyId — scheduling now."
                            )
                            scheduleNow(accountKeyId)
                        }
                    }
                }
            }

            if (pendingMigrationTorFailureStorageProvider.get()) {
                // The flag only records THAT a background attempt once failed on Tor, not what the
                // engine's next due transfer needs NOW — the plan moves on (proving, dependency
                // mining, rescheduling) between when it was set and the next app open, and the flag
                // is never invalidated by any of that. Re-verify against the engine's live state
                // before trusting it: only navigate when the earliest not-yet-sent transfer is
                // actually broadcast-ready. Otherwise this flag is stale (observed live: the flag
                // survived from an old Tor failure while the actual next-due transfer was stuck
                // needing PROVE for an unrelated reason) — MigrationSendingVM would immediately hit
                // AwaitingProof/NothingDue and show a "Couldn't Send" sheet whose message has nothing
                // to do with Tor, on every single app open, for a transfer that was never going to
                // attempt a network send in the first place. Clear it instead; the transfer's actual
                // blocker is already covered by the normal Migration Progress home banner.
                val nextTransfer =
                    sdk
                        .getMigrationTransferStates()
                        ?.transfers
                        ?.filter { it.isTransfer && !it.isSent }
                        ?.minByOrNull { it.scheduledHeight }
                if (nextTransfer?.action == MigrationNextAction.BROADCAST) {
                    // A background attempt failed specifically because of Tor — route through the Sending
                    // screen first rather than straight to MigrationProgressArgs/MigrationTorFailureArgs:
                    // MigrationSendingVM's init{} always attempts a send immediately on construction,
                    // reproducing the exact condition that failed in the background using the current
                    // migration Tor setting. If it fails again, MigrationSendingVM's own existing
                    // sendOnce() logic already forwards to MigrationTorFailureArgs — no need to duplicate
                    // that routing here.
                    migrationLog("MigrationRecovery: pending background Tor failure — redirecting to Sending.")
                    navigationRouter.replaceAll(HomeArgs, MigrationSendingArgs)
                } else {
                    migrationLog(
                        "MigrationRecovery: pending background Tor failure flag is stale " +
                            "(next transfer action=${nextTransfer?.action}) — clearing."
                    )
                    pendingMigrationTorFailureStorageProvider.store(false)
                }
            }
            // No stale write-ahead plan clearing anymore: nothing plan-shaped is persisted app-side —
            // the engine's own state is the single, authoritative record (a commit that never happened
            // simply leaves the engine NotStarted, and every screen renders that live).
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            migrationLog(
                "MigrationRecovery: engine read failed (${e.message}) — un-stamping the throttle for a clean retry.",
                e
            )
            throttleMutex.withLock { lastRunMark = null }
        }
    }

    companion object {
        private val RUN_THROTTLE: Duration = 10_000.milliseconds

        private val throttleMutex = Mutex()

        @Volatile
        private var lastRunMark: TimeSource.Monotonic.ValueTimeMark? = null

        /** Tests run in one JVM — reset the shared throttle between them. */
        internal fun resetRunThrottleForTests() {
            lastRunMark = null
        }
    }
}

/**
 * Production implementation of the migration-worker active check — reads WorkManager unique work
 * state (per-account unique work name). Extracted so [CheckMigrationRecoveryUseCase] tests can
 * supply a lambda stub instead of needing a real WorkManager context (unit tests can't
 * initialise WorkManager).
 */
internal suspend fun isMigrationWorkerActiveInWorkManager(context: Context, accountKeyId: String): Boolean =
    withContext(Dispatchers.IO) {
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(
                MigrationScheduler.workId(accountKeyId)
            ).get()
    }.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

/**
 * What the migration worker's unique work is doing right now, distinguishing [SCHEDULED] (a
 * worker run is already armed for later — nothing to do here; the live driver, started
 * unconditionally whenever a migration is in progress, covers acceleration) from [RUNNING]
 * (already executing — also nothing to do) and [ABSENT] (needs reviving).
 * [isMigrationWorkerActiveInWorkManager] collapses the first two together, which is all
 * [OnMigrationSyncCompletedUseCase] needs; this finer state is what
 * [CheckMigrationRecoveryUseCase]'s app-open trigger needs.
 */
enum class MigrationWorkerRunState { RUNNING, SCHEDULED, ABSENT }

/**
 * Production implementation of the migration-worker run-state check — reads WorkManager unique
 * work state (per-account unique work name). Extracted so [CheckMigrationRecoveryUseCase] tests
 * can supply a lambda stub instead of needing a real WorkManager context (unit tests can't
 * initialise WorkManager).
 */
internal suspend fun migrationWorkerRunState(context: Context, accountKeyId: String): MigrationWorkerRunState {
    val infos =
        withContext(Dispatchers.IO) {
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(
                    MigrationScheduler.workId(accountKeyId)
                ).get()
        }
    return when {
        infos.any { it.state == WorkInfo.State.RUNNING } -> MigrationWorkerRunState.RUNNING
        infos.any { it.state == WorkInfo.State.ENQUEUED } -> MigrationWorkerRunState.SCHEDULED
        else -> MigrationWorkerRunState.ABSENT
    }
}
