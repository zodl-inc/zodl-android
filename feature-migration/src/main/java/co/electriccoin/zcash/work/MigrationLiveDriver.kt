package co.electriccoin.zcash.work

import cash.z.ecc.android.sdk.OrchardMigrationSdk
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.common.repository.MigrationLiveReadout
import co.electriccoin.zcash.ui.common.repository.MigrationTransferStateRepository
import co.electriccoin.zcash.ui.common.repository.readUnreconciledLiveReadout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The app-scoped fast path: while the process is alive, this drives the migration forward far
 * more precisely than WorkManager alone (no Doze/OEM-scheduler batching) — by calling the EXACT
 * SAME [MigrationDriveOnce.run] the WorkManager worker calls, so there is one decision path,
 * never two. [startIfNotRunning] is safe to call from every trigger point that knows a migration
 * might be active (post-commit, app-open) — it is a no-op if a loop for that account is already
 * running. The worker chain remains the durable background path (survives process death); this
 * driver adds nothing to correctness, only speed, while the app is alive.
 */
interface MigrationLiveDriver {
    fun startIfNotRunning(accountKeyId: String)

    /**
     * Cancels this account's loop if one is running — for a flow that resets the engine's own
     * state OUTSIDE the driver's normal step progression (currently only "Restart Migration"),
     * so a loop already sleeping mid-re-arm from before the reset doesn't wake up, act on a plan
     * that no longer exists, and republish a readout describing it. Coroutine cancellation is
     * cooperative (checked at suspension points), so this cannot interrupt work already committed
     * — it only stops the loop from proceeding to its NEXT step. A no-op if no loop is running for
     * this account.
     *
     * NOT a guarantee against a concurrent [MigrationTransferStateRepository.publish]: if the
     * loop's suspension point at the moment of cancellation is already past `publishFreshReadout`'s
     * last SDK read (that function's own final step — the actual `repository.publish()` call — is
     * synchronous, no suspension point between the two), this call returns before that publish
     * lands, and it happens anyway. Reliable only when the loop is asleep in `delay()`/
     * `delayWithPeriodicRefresh()` — which is where a restarted account's driver sits most of the
     * time — not a hard guarantee for every call site.
     */
    fun stop(accountKeyId: String)
}

class MigrationLiveDriverImpl(
    private val migrationDriveOnce: MigrationDriveOnce,
    private val getOrchardMigrationSdk: suspend (String) -> OrchardMigrationSdk?,
    private val migrationTransferStateRepository: MigrationTransferStateRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : MigrationLiveDriver {
    /**
     * Atomic per-account start-if-absent, race-free by construction: [ConcurrentHashMap] gives
     * an atomic `putIfAbsent`, and each job's own [Job.invokeOnCompletion] removes exactly the
     * entry it installed (compare-and-remove via the two-arg overload) — no separate suspend-
     * based lock needed. The job is LAZY and `putIfAbsent` happens BEFORE `.start()` (an earlier
     * draft called `.start()` first and only attached its own removal hook afterward, leaving a
     * window where a second, concurrent call could see the map still empty and start a genuinely
     * duplicate, concurrently-running loop for the same account). If we lose the `putIfAbsent`
     * race and the winner is already [Job.isCompleted] by the time we check, we retry
     * immediately (it is safe: the winner is gone, or about to be removed by its own hook); if
     * the winner is still active, this call is a no-op — see [startIfNotRunning] for why no
     * completion hook is attached in that case.
     */
    private val runningJobs = ConcurrentHashMap<String, Job>()

    override fun startIfNotRunning(accountKeyId: String) {
        val candidate = scope.launch(start = CoroutineStart.LAZY) { loop(accountKeyId) }
        val existing = runningJobs.putIfAbsent(accountKeyId, candidate)
        if (existing == null) {
            candidate.invokeOnCompletion { runningJobs.remove(accountKeyId, candidate) }
            candidate.start()
        } else {
            candidate.cancel()
            if (existing.isCompleted) {
                // Already gone by the time we looked — safe to retry immediately.
                startIfNotRunning(accountKeyId)
            } else {
                // A loop for this account is genuinely still active — this call is fully
                // satisfied by it: the running loop re-evaluates the engine's own state on every
                // iteration, so it will itself pick up whatever prompted this call. No completion
                // hook is attached here (a prior draft attached one unconditionally, which forced
                // one extra full loop run after every merely-coincident double call, even when the
                // active loop was going to reach a legitimate Terminal on its own) — if the active
                // loop stops before the account is actually done, the next natural trigger point
                // (app-open, post-commit) or the durable WorkManager chain calls
                // startIfNotRunning again.
                migrationLog("MigrationLiveDriver: already driving $accountKeyId — no-op.")
            }
        }
    }

    override fun stop(accountKeyId: String) {
        runningJobs[accountKeyId]?.cancel()
    }

    private suspend fun loop(accountKeyId: String) {
        migrationLog("MigrationLiveDriver: starting live loop for $accountKeyId")
        // Priming publish (2026-08-0X): populate the cache near-instantly, before the first
        // driveOnce.run() call below — which can itself take many seconds (confirmed live: up
        // to ~13s for a real broadcast) and previously left the repository empty/stale for that
        // whole window. Uses the SDK's mutex-free loggedRead lane (readUnreconciledLiveReadout,
        // shared with the Home/Progress cold-start fallbacks) specifically so this never queues
        // behind MIGRATION_DB_ACCESS_MUTEX itself — the entire point of priming is to be fast
        // precisely when a real step might be holding that mutex (2026-08-07 read/write-
        // separation design; Fable review caught an earlier draft that reused the *reconciled*
        // publishFreshReadout() here, which would have taken the mutex and defeated this).
        // Best-effort and non-fatal: if the SDK can't be resolved yet, this priming step is
        // silently skipped and the loop below's own first-iteration resolution decides the
        // loop's fate the same way it always has — this line must never itself decide that.
        getOrchardMigrationSdk(accountKeyId)?.let { sdk ->
            runCatching { migrationTransferStateRepository.publish(accountKeyId, sdk.readUnreconciledLiveReadout()) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }

        while (true) {
            try {
                val sdk =
                    getOrchardMigrationSdk(accountKeyId) ?: run {
                        migrationLog("MigrationLiveDriver: SDK unavailable for $accountKeyId — stopping.")
                        return
                    }
                val result =
                    migrationDriveOnce.run(
                        sdk,
                        accountKeyId,
                        allowForcedBroadcastWindow = true,
                        // true: the live driver has no execution-time ceiling (unlike the worker),
                        // so it is the only safe caller for a forced-broadcast-window wait that can
                        // run for nearly a full privacy buffer.
                        driveByLiveLoop = true,
                    )
                if (result !is DriveOnceResult.LockBusy) {
                    // A step actually ran (ReArmed) or the migration reached a terminal state —
                    // either way, publish one fresh read for every screen/use-case that used to
                    // poll OrchardMigrationSdk.getMigrationTransferStates()/estimatedChainTip()/
                    // estimatedSecondsPerBlock() on its own: these are reads the driver's loop was
                    // already going to serialize through its own coroutine, not a second concurrent
                    // caller competing for the SDK's single-threaded DB I/O executor the way an
                    // independent screen poll did. Skipped on LockBusy — another caller is mid-step,
                    // nothing changed from THIS call's perspective, and the winning caller publishes
                    // when it finishes.
                    publishFreshReadout(sdk, accountKeyId)
                }
                when (result) {
                    is DriveOnceResult.ReArmed -> {
                        // Floor a floorless re-arm value (nextWake's privacy-gap term can be
                        // exactly zero) so this in-process loop never spins tightly — WorkManager
                        // dispatch latency is the accidental brake for the worker path; this loop
                        // has no equivalent brake unless we supply one. EXCEPT already-deliberate
                        // short constants (result.respectAntiSpinFloor == false, e.g.
                        // PREP_FAST_TRACK_REARM) — flooring those defeats their whole purpose
                        // (chaining ready prep-batch broadcasts back-to-back within one tree).
                        val effectiveDelay =
                            if (result.respectAntiSpinFloor) {
                                maxOf(result.delay, MIN_REARM_SECONDS.seconds)
                            } else {
                                result.delay
                            }
                        delayWithPeriodicRefresh(effectiveDelay, sdk, accountKeyId)
                    }

                    is DriveOnceResult.LockBusy -> {
                        delay(result.retryDelay)
                    }

                    DriveOnceResult.Terminal -> {
                        migrationLog("MigrationLiveDriver: $accountKeyId reached a terminal state — stopping loop.")
                        return
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // MOB-1664 (self-heal, mirrors CheckMigrationRecoveryUseCase's 2026-08-07
                // hardening for the same underlying risk — a "database is locked" throw past the
                // Rust layer's 15s busy_timeout, or any other transient failure mid-step). This
                // used to be one try/catch around the WHOLE while(true) loop, so any failure fell
                // through and ended the loop's coroutine permanently: nothing on the Progress
                // screen restarts it (it only observes, never drives), so a foreground user
                // watching a stuck "Ready now" transfer had no way back in short of backgrounding
                // and re-foregrounding the app (the only triggers that call startIfNotRunning
                // again). Catching per-iteration instead means the loop survives: log, back off,
                // and let the next iteration retry with a fresh SDK/DB read.
                migrationLog(
                    "MigrationLiveDriver: step failed for $accountKeyId (transient) — retrying in " +
                        "${MIN_REARM_SECONDS}s.",
                    e
                )
                delay(MIN_REARM_SECONDS.seconds)
            }
        }
    }

    /**
     * Sleeps out [total], but for a wait long enough to matter (a re-armed gap can legitimately be
     * hours — the next transfer's own schedule, not a bug), wakes every [STALENESS_REFRESH_INTERVAL]
     * to republish a fresh readout. The driver is genuinely idle for the whole span (this call IS the
     * sleep — no step is running concurrently with it), so this refresh never contends with the
     * driver's own work; it only ever races an unrelated caller (worker/another live-driver start),
     * exactly like the loop's own per-step publish already does. Bounds Progress-screen (and any
     * other consumer's) staleness to roughly this interval instead of "however long until the next
     * actual step" (2026-08-06 Fable review: previously unbounded, and re-opening the screen didn't
     * help once the repository held a non-null value).
     */
    private suspend fun delayWithPeriodicRefresh(total: Duration, sdk: OrchardMigrationSdk, accountKeyId: String) {
        var remaining = total
        while (remaining > STALENESS_REFRESH_INTERVAL) {
            delay(STALENESS_REFRESH_INTERVAL)
            remaining -= STALENESS_REFRESH_INTERVAL
            publishFreshReadout(sdk, accountKeyId)
        }
        delay(remaining)
    }

    /**
     * runCatching: a failed read (e.g. "database is locked" outlasting the SDK's own bounded retry)
     * must not kill this loop the way a failed drive step would — unlike [MigrationDriveOnce.run]'s
     * own work, this publish is a side-channel for OTHER screens' benefit, not something this loop's
     * own control flow depends on (2026-08-06 Fable review: an unguarded read here escaping to the
     * outer catch stopped the whole live-driver loop mid-migration on a transient DB-lock read
     * failure, worse than having no publish at all). Skipping just leaves the repository's
     * last-published value in place until the next successful read.
     */
    private suspend fun publishFreshReadout(sdk: OrchardMigrationSdk, accountKeyId: String) {
        runCatching {
            MigrationLiveReadout(
                states = sdk.getMigrationTransferStates(),
                estimatedTip = sdk.estimatedChainTip(),
                estimatedSecondsPerBlock = sdk.estimatedSecondsPerBlock(),
                // Both added 2026-08-07 so the Home banner (MigrationHomeMessageSourceImpl) can
                // observe this same readout instead of independently calling getMigrationState()/
                // hasOverdueTransfers() itself — those two are on the SDK's mutex-gated `logged`
                // lane, the exact mechanism that produced a live-reproduced ~10-16s Home-banner
                // load delay tonight.
                migrationState = sdk.getMigrationState(),
                hasOverdueTransfers = sdk.hasOverdueTransfers(),
                nu7ActivationHeight = sdk.nu7ActivationHeight,
            )
        }.onSuccess { migrationTransferStateRepository.publish(accountKeyId, it) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }
}

/**
 * How stale a published [MigrationLiveReadout] is allowed to get during a long re-armed wait
 * before [MigrationLiveDriverImpl.delayWithPeriodicRefresh] wakes to republish. The refresh read is
 * on the SDK's no-mutex pure-read lane and runs only while the driver is otherwise idle (mid-sleep,
 * not mid-step), so — unlike the independent screen poll this repository replaced — it never
 * contends with the driver's own work.
 */
private val STALENESS_REFRESH_INTERVAL = 60.seconds
