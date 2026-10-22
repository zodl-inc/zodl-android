package co.electriccoin.zcash.work

import cash.z.ecc.android.sdk.CloseableSynchronizer
import cash.z.ecc.android.sdk.MigrationAdvanceStep
import cash.z.ecc.android.sdk.MigrationBlocker
import cash.z.ecc.android.sdk.MigrationPeek
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.MigrationTransferState
import cash.z.ecc.android.sdk.MigrationTransferStates
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.TransferAttemptOutcome
import cash.z.ecc.android.sdk.TransferResult
import co.electriccoin.zcash.migration.BuildConfig
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.estimatedSecondsBetweenHeights
import co.electriccoin.zcash.ui.common.model.migration.toSnapshot
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.LastNetworkActivityStorageProvider
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What the caller should do after one [MigrationDriveOnce.run] call.
 *  - [ReArmed]: a step ran to completion and already re-armed the WorkManager chain (via
 *    `MigrationScheduler.schedule` inside `reArm`) to [delay] — the CALLER's own next check
 *    (the live driver's loop) should wait the same [delay]; the worker discards this (it's
 *    already re-armed, nothing more to do).
 *  - [LockBusy]: another caller was mid-step; THIS call did nothing (no `reArm` ran). The
 *    worker MUST explicitly self-schedule using [retryDelay] here, or a lost race silently
 *    kills the durable WorkManager chain (nothing else will re-arm it). The live driver just
 *    waits [retryDelay] and tries again.
 *  - [Terminal]: the migration reached `Complete`, or a state that intentionally never
 *    self-schedules (`Rebuild`, `Replan`, `Expired`, `NetworkError`) — all of these need a
 *    user-driven reschedule/re-plan (or are simply done), so nothing here re-arms. Neither caller
 *    should re-arm; the live driver stops. `InvalidNote` is NOT in this list (SDK Task 9): a
 *    genuinely-unknown broadcast rejection is withheld (`Blocker::AwaitingReevaluation`) rather
 *    than terminally failed, so [handleExecuted]'s `InvalidNote` arm re-arms instead of stopping —
 *    see its doc comment.
 */
sealed class DriveOnceResult {
    data class ReArmed(
        val delay: Duration,
        /** False only for already-deliberate short constants (e.g. PREP_FAST_TRACK_REARM) where
         *  the live driver's anti-spin floor would defeat their purpose. True (default) for every
         *  genuinely floorless nextWake()-derived gap. */
        val respectAntiSpinFloor: Boolean = true
    ) : DriveOnceResult()

    data class LockBusy(
        val retryDelay: Duration
    ) : DriveOnceResult()

    object Terminal : DriveOnceResult()
}

/**
 * The single "what now, do it, re-arm" function — the shared decision-and-execution logic that
 * both the WorkManager worker (survives process death) and the app-scoped live-process driver
 * (faster while the app is alive) call, so there is exactly one execution path, never two
 * divergent ones. Internally guarded by [DRIVE_LOCK] so an already-in-flight call from one
 * caller is never interrupted by the other (an already-*scheduled-but-not-fired* WorkManager job
 * is superseded for free via `ExistingWorkPolicy.REPLACE` inside `reArm`; only an *actively
 * executing* step needs this lock).
 */
class MigrationDriveOnce(
    private val applicationContext: android.content.Context,
    private val migrationNotifier: MigrationNotifier,
    private val isMigrationTorEnabledStorageProvider: IsMigrationTorEnabledStorageProvider,
    private val pendingMigrationTorFailureStorageProvider: PendingMigrationTorFailureStorageProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val lastNetworkActivity: LastNetworkActivityStorageProvider,
    private val applicationStateProvider: ApplicationStateProvider,
) {
    /**
     * @param allowForcedBroadcastWindow Only `true` for [MigrationLiveDriver] — the worker (via
     * [MigrationWorker.doWork]) must NEVER pass `true`: forcing a broadcast window can wait
     * nearly a full privacy buffer (up to 10 minutes on mainnet) before broadcasting, which
     * cannot fit inside WorkManager's own ~10-minute `doWork()` execution ceiling. The live
     * driver has no such ceiling (it runs only as long as the app process is alive), so it is
     * the only safe caller for this branch.
     */
    suspend fun run(
        sdk: OrchardMigrationSdk,
        accountKeyId: String,
        allowForcedBroadcastWindow: Boolean = false,
        driveByLiveLoop: Boolean = false,
    ): DriveOnceResult {
        if (!DRIVE_LOCK.tryLock()) {
            migrationLog("MigrationDriveOnce: another drive is in progress — skipping, short retry.")
            return DriveOnceResult.LockBusy(SKIP_RETRY_DELAY)
        }
        liveLoopActive = driveByLiveLoop
        try {
            // This IS a run — stamp the heartbeat regardless of which caller drove it, so the
            // dead-man's-switch alarm (MigrationTransferDueReceiver) never fires a spurious
            // "step due" notification just because the WORKER specifically didn't fire (the live
            // driver may have driven this account forward instead).
            MigrationWorkerHeartbeat.stampRun(applicationContext, accountKeyId)

            val result = sdk.nextStep()
            if (result == null) {
                migrationLog("MigrationDriveOnce: no migration in progress — nothing to do.")
                return DriveOnceResult.Terminal
            }
            val step = result.step
            migrationLog("MigrationDriveOnce: run start account=$accountKeyId step=$step")
            return when (step) {
                MigrationAdvanceStep.Complete -> {
                    handleCompleteStep(sdk, accountKeyId) { completeRun(accountKeyId) }
                }

                is MigrationAdvanceStep.Rebuild -> {
                    rebuildRun(sdk, accountKeyId, step.transferId)
                    DriveOnceResult.Terminal
                }

                MigrationAdvanceStep.Replan -> {
                    replanRun(sdk, accountKeyId)
                    DriveOnceResult.Terminal
                }

                is MigrationAdvanceStep.Prove -> {
                    syncRun(sdk, accountKeyId, allowForcedBroadcastWindow)
                }

                MigrationAdvanceStep.Reevaluate -> {
                    syncRun(sdk, accountKeyId, allowForcedBroadcastWindow)
                }

                is MigrationAdvanceStep.Broadcast -> {
                    broadcastRun(sdk, accountKeyId, step.transferId, allowForcedBroadcastWindow)
                }

                MigrationAdvanceStep.Waiting -> {
                    waitingRun(sdk, accountKeyId)
                }
            }
        } finally {
            DRIVE_LOCK.unlock()
        }
    }

    /**
     * Lets a caller OTHER than the worker/live-driver pair (currently: [MigrationSendingVM]'s
     * manual-resume send) mutate the engine's send state under the same [DRIVE_LOCK] that guards
     * [run] — without going through [run]'s own Prove/Broadcast/Waiting dispatch, which
     * [MigrationSendingVM] has its own distinct retry/preflight shape for (2026-08-06 DRIVE_LOCK
     * bypass fix). Returns `null` if the lock is busy (a worker/live-driver step is actively
     * executing) instead of suspending — callers that need to wait should retry using their own
     * existing backoff, not add a second one here.
     */
    suspend fun <T> withExclusiveAccess(block: suspend () -> T): T? {
        if (!DRIVE_LOCK.tryLock()) {
            return null
        }
        try {
            return block()
        } finally {
            DRIVE_LOCK.unlock()
        }
    }

    /**
     * The engine answers `nextStep` at the SCANNED tip, which can lag wall clock by hours in a
     * backgrounded wallet — nextStep is now Broadcast-authoritative (it checks
     * `next_broadcastable` at the estimated tip internally), so a due broadcast is dispatched
     * straight to [broadcastRun] by [run] and never reaches this function. `waitingRun` only
     * handles a genuine engine `Waiting`: sweep to completion, surface an unprovable blocker, or
     * re-arm.
     */
    private suspend fun waitingRun(sdk: OrchardMigrationSdk, accountKeyId: String): DriveOnceResult {
        val states = sdk.getMigrationTransferStates()
        val allSent = states != null && states.transfers.isNotEmpty() && states.transfers.all { it.isSent }
        val hasUnprovableBlocker = states?.transfers?.any { it.blocker == MigrationBlocker.UNPROVABLE_ANCHOR } == true
        return when (waitingDisposition(allSent, hasUnprovableBlocker)) {
            WaitingDisposition.COMPLETION_SWEEP -> {
                // Everything broadcast, awaiting mining. Mining is only observed by a scan-driven
                // reconcile, so the sweep must be a REAL sync run — a passive wait would never let
                // the engine reach Complete in the background (review M2).
                migrationLog("MigrationDriveOnce: all transactions sent — completion sweep sync run.")
                syncRun(sdk, accountKeyId, allowForcedBroadcastWindow = false)
            }

            WaitingDisposition.SURFACE_UNPROVABLE -> {
                surfaceUnprovableBlocker(sdk, accountKeyId, states)
                DriveOnceResult.ReArmed(reArm(sdk, accountKeyId))
            }

            WaitingDisposition.RE_ARM -> {
                // Mirrors COMPLETION_SWEEP above: a passive wait can never let the scanned tip
                // catch up to a boundary the engine's peek says will become Prove-ready — only a
                // real sync can. syncRun's own else-fallback (below) safely re-arms if, after
                // syncing, there is still genuinely nothing due — no busy-loop risk. Root cause
                // and live diagnosis: 2026-08-06 overnight stall (spec in z/wt/migration/spec/).
                syncRun(sdk, accountKeyId, allowForcedBroadcastWindow = false)
            }
        }
    }

    /**
     * A sync (prove) run: syncToTip + finalizeReadyTransfers + reconcile, gated by the
     * post-broadcast privacy buffer. Afterwards the engine is asked again: a ready PREPARATION
     * broadcasts immediately, in this same call — core sync call 2026-08-05 §2.1: upstream only
     * ever surfaces a Preparation for proving once its own broadcast schedule is already due, so
     * there is no separate nextStep() round-trip to wait through. A due TRANSFER still waits out
     * the quiet gap this sync just opened (existing behavior, unchanged).
     */
    private suspend fun syncRun(
        sdk: OrchardMigrationSdk,
        accountKeyId: String,
        allowForcedBroadcastWindow: Boolean,
    ): DriveOnceResult {
        if (sdk.isSyncBlocked().first()) {
            migrationLog("MigrationDriveOnce: sync run blocked by the post-broadcast privacy gate — deferring.")
            return DriveOnceResult.ReArmed(reArm(sdk, accountKeyId, floor = sdk.privacySyncBufferDuration()))
        }
        val burst = synchronizerProvider.getSynchronizerOrNull()?.syncToTip(timeout = SYNC_TIMEOUT)
        migrationLog("MigrationDriveOnce: syncToTip result=$burst")
        val proved = sdk.finalizeReadyTransfers()
        migrationLog("MigrationDriveOnce: proved=$proved")
        // Post Pass-3-removal (2026-08-05 migration-engine-delegation work): this can now only report
        // true for a migration that was ALREADY Failed by a prior recordTransferResult tag=2/3 call —
        // there is no live foreign-spend detection path left here (advance_migration's own candidate
        // checks own that now, reached through nextStep()). Kept because "was this plan already dead"
        // is still a correct, cheap early-exit for this function.
        if (sdk.reconcileInvalidations()) {
            // The plan is invalid (input notes spent externally) — notify and do NOT re-arm; the
            // app-open router (CheckMigrationRecoveryUseCase) takes over from here.
            migrationNotifier.notifyMigrationPlanInvalid(accountKeyId)
            MigrationScheduler(applicationContext).cancel(accountKeyId)
            migrationLog("MigrationDriveOnce: reconcile found an invalidation — stopping the work chain.")
            return DriveOnceResult.Terminal
        }
        lastNetworkActivity.stampNow()

        return when (val nextStep = sdk.nextStep()?.step) {
            is MigrationAdvanceStep.Broadcast -> {
                // §2.1 same-beat check: key off the ENGINE-NAMED transferId nextStep() just
                // returned, not a schedule-order scan (nextDueUnsentIsPreparation's earliestUnsent
                // could name a different transaction than the one the engine actually served,
                // which would defer a due preparation by a full privacy buffer — latency only,
                // but avoidable now that the id is already in hand).
                val prep = isPreparationTransfer(sdk.getMigrationTransferStates(), nextStep.transferId)
                if (prep) {
                    migrationLog(
                        "MigrationDriveOnce: sync done, next=$nextStep (preparation) — broadcasting same-beat."
                    )
                    broadcastRun(sdk, accountKeyId, nextStep.transferId, allowForcedBroadcastWindow)
                } else {
                    val chainDelay = sdk.privacySyncBufferDuration()
                    scheduleUnlessLiveLoop(accountKeyId, chainDelay)
                    migrationLog("MigrationDriveOnce: sync done, next=$nextStep — broadcast run in $chainDelay")
                    DriveOnceResult.ReArmed(chainDelay)
                }
            }

            MigrationAdvanceStep.Complete -> {
                handleCompleteStep(sdk, accountKeyId) { completeRun(accountKeyId) }
            }

            is MigrationAdvanceStep.Rebuild -> {
                rebuildRun(sdk, accountKeyId, nextStep.transferId)
                DriveOnceResult.Terminal
            }

            MigrationAdvanceStep.Replan -> {
                // Same handling as run()'s first-read Replan branch — this post-sync re-ask can
                // discover Replan too (a sync just found the plan dead), and must not fall through
                // to the generic else below: that branch only re-arms, so the user-driven
                // notifyRescheduleRequired notification would never fire on this path even though
                // sdk.nextStep() already marks the migration Superseded either way.
                replanRun(sdk, accountKeyId)
                DriveOnceResult.Terminal
            }

            else -> {
                // Prove again (boundary not yet settled at the new tip) or Waiting.
                migrationLog("MigrationDriveOnce: sync done, next=$nextStep — re-arming.")
                surfaceUnprovableBlocker(sdk, accountKeyId, sdk.getMigrationTransferStates())
                DriveOnceResult.ReArmed(reArm(sdk, accountKeyId))
            }
        }
    }

    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    private suspend fun broadcastRun(
        sdk: OrchardMigrationSdk,
        accountKeyId: String,
        transferId: Long,
        allowForcedBroadcastWindow: Boolean,
    ): DriveOnceResult {
        val states = sdk.getMigrationTransferStates()
        val est = sdk.estimatedChainTip()
        // The transaction the ENGINE already named via nextStep()'s own Broadcast(transferId) —
        // looked up by id, not re-predicted locally (core sync call §2.4: no local mirror of
        // next_broadcastable) — for the fast-track preflight and the post-send notification kind.
        val nextCandidate = states?.transfers?.firstOrNull { it.id == transferId }
        val prepFastTrack =
            nextCandidate != null &&
                !nextCandidate.isTransfer &&
                nextCandidate.isProved &&
                nextCandidate.scheduledHeight <= est

        // status is a Flow<Status> — timeout if cold; null synchronizer is non-syncing.
        // timeout → assume SYNCING → defer (privacy-safe default; production status is a StateFlow
        // and answers immediately).
        val syncing =
            synchronizerProvider.synchronizer.value?.let { synchronizer ->
                withTimeoutOrNull(STATUS_READ_TIMEOUT) { synchronizer.status.first() } ?: Synchronizer.Status.SYNCING
            } == Synchronizer.Status.SYNCING
        val lastActivity = lastNetworkActivity.get()
        val preflight =
            decideBroadcastPreflight(
                synchronizerSyncing = syncing,
                nowEpochSeconds = nowEpochSeconds(),
                lastNetworkActivityEpochSeconds = lastActivity?.epochSecond,
                privacyBufferSeconds = sdk.privacySyncBufferDuration().inWholeSeconds,
                prepFastTrack = prepFastTrack,
            )
        migrationLog(
            "MigrationDriveOnce: broadcast preflight=$preflight " +
                "(syncing=$syncing, prepFastTrack=$prepFastTrack, lastNetworkActivity=$lastActivity)"
        )
        if (preflight == BroadcastPreflight.DEFER) {
            if (syncing && allowForcedBroadcastWindow && applicationStateProvider.isInForeground.first()) {
                // Force a window — ONLY the live driver ever reaches here (allowForcedBroadcastWindow),
                // and only while a FRESH read confirms the app is actually foreground right now (do not
                // trust `syncing` alone as a foreground proxy: the engine's own status is a stale
                // last-tick value that onBackground() does not reset).
                val closeable = synchronizerProvider.getSynchronizerOrNull() as? CloseableSynchronizer
                if (closeable == null) {
                    val deferDelay = sdk.privacySyncBufferDuration()
                    scheduleUnlessLiveLoop(accountKeyId, deferDelay)
                    migrationLog("MigrationDriveOnce: no pausable synchronizer — deferring broadcast $deferDelay.")
                    return DriveOnceResult.ReArmed(deferDelay)
                }
                closeable.pause()
                lastNetworkActivity.stampNow()
                migrationLog("MigrationDriveOnce: paused foreground sync to open a broadcast window.")
                try {
                    val gap = quietGapRemaining(sdk.privacySyncBufferDuration())
                    if (gap.isPositive()) {
                        migrationLog("MigrationDriveOnce: waiting privacy quiet gap $gap before broadcast.")
                        delay(gap)
                    }
                    // Re-ask the engine — up to a full privacy buffer may have elapsed during the
                    // wait above, and it may no longer report Broadcast at all (something changed),
                    // or name a different transaction than the one that triggered this run.
                    val freshStep = sdk.nextStep()?.step
                    if (freshStep !is MigrationAdvanceStep.Broadcast) {
                        migrationLog(
                            "MigrationDriveOnce: after the wait, engine no longer reports Broadcast " +
                                "(now=$freshStep) — re-arming."
                        )
                        return DriveOnceResult.ReArmed(reArm(sdk, accountKeyId))
                    }
                    val freshStates = sdk.getMigrationTransferStates()
                    val freshCandidate = freshStates?.transfers?.firstOrNull { it.id == freshStep.transferId }
                    return attemptBroadcast(sdk, accountKeyId, freshCandidate)
                } finally {
                    closeable.resume()
                    migrationLog("MigrationDriveOnce: resumed foreground sync (SDK gate now governs).")
                }
            } else {
                // Quiet-gap-only defer (nothing running to pause, or the worker caller, or genuinely
                // backgrounded) — unchanged from today's behavior.
                val deferDelay = if (prepFastTrack) PREP_FAST_TRACK_REARM else sdk.privacySyncBufferDuration()
                scheduleUnlessLiveLoop(accountKeyId, deferDelay)
                migrationLog(
                    "MigrationDriveOnce: deferring broadcast $deferDelay — a sync source is live or the quiet " +
                        "gap is unmet."
                )
                return DriveOnceResult.ReArmed(deferDelay, respectAntiSpinFloor = !prepFastTrack)
            }
        }
        return attemptBroadcast(sdk, accountKeyId, nextCandidate)
    }

    /**
     * Moved verbatim (including the exact reasoning comment) from
     * `MigrationProgressVM.quietGapRemaining` — the privacy quiet-gap wait shared by the
     * foreground-forced broadcast window above and the VM's own foreground broadcast pass.
     */
    private suspend fun quietGapRemaining(privacyBuffer: Duration): Duration {
        val last = lastNetworkActivity.get() ?: return Duration.ZERO
        val elapsed = (Clock.System.now().epochSeconds - last.epochSecond).seconds
        val remaining = privacyBuffer - elapsed
        return if (remaining.isPositive()) remaining else Duration.ZERO
    }

    @Suppress("ReturnCount")
    private suspend fun attemptBroadcast(
        sdk: OrchardMigrationSdk,
        accountKeyId: String,
        nextCandidate: MigrationTransferState?,
    ): DriveOnceResult {
        val snapshotBefore = sdk.snapshot()
        val useTor = isMigrationTorEnabledStorageProvider.get(accountKeyId)

        // Hard timeout around the whole broadcast attempt: a cold-bootstrapping Tor client can
        // hang the submit indefinitely (observed live: tx stuck in-flight 10+ minutes until the
        // WorkManager execution ceiling killed the worker and nothing re-armed). On timeout the
        // native call may still complete detached — a re-submit of the same tx is safely
        // classified as a duplicate by the SDK (F2 classifier + mined-height probe), so
        // re-arming for another attempt is correct.
        val outcome =
            withTimeoutOrNull(BROADCAST_ATTEMPT_TIMEOUT) {
                executeWithRetries {
                    sdk.executeNextPendingTransfer(NetworkPrivacyOptions(useTor = useTor), useEstimatedTip = true)
                }
            } ?: run {
                migrationLog(
                    "MigrationDriveOnce: broadcast attempt timed out after $BROADCAST_ATTEMPT_TIMEOUT — re-arming."
                )
                return DriveOnceResult.ReArmed(reArm(sdk, accountKeyId, floor = REARM_FLOOR))
            }
        return when (outcome) {
            is TransferAttemptOutcome.NothingDue -> {
                // The estimate raced ahead of the engine's own due check — re-arm normally.
                val delay = reArm(sdk, accountKeyId)
                migrationLog("MigrationDriveOnce: NothingDue — re-armed for the next window.")
                DriveOnceResult.ReArmed(delay)
            }

            is TransferAttemptOutcome.AwaitingProof -> {
                // Defensive: nextStep said Broadcast, so the engine had a proved transaction — a
                // proof can only have vanished through a concurrent reorg/rescan. Re-arm floored;
                // the next run re-asks the engine from scratch.
                migrationLog(
                    "MigrationDriveOnce: AwaitingProof for ${outcome.transferId} despite a Broadcast step — " +
                        "re-arming."
                )
                DriveOnceResult.ReArmed(reArm(sdk, accountKeyId, floor = REARM_FLOOR))
            }

            is TransferAttemptOutcome.Executed -> {
                handleExecuted(
                    sdk,
                    accountKeyId,
                    outcome.result,
                    snapshotBefore,
                    sentWasPrep = nextCandidate?.isTransfer == false,
                )
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun handleExecuted(
        sdk: OrchardMigrationSdk,
        accountKeyId: String,
        result: TransferResult,
        snapshotBefore: LiveMigrationSnapshot?,
        sentWasPrep: Boolean,
    ): DriveOnceResult =
        when (result) {
            is TransferResult.Success -> {
                migrationLog("MigrationDriveOnce: sent — txId=${result.txId.txIdString()}")
                // Classify zip318_kind immediately rather than waiting on the mempool watcher or a
                // later rescan — our own broadcast path already stores `raw` locally, which makes
                // the normal enhancement queue skip this txid forever (`raw IS NOT NULL` guard), so
                // without this call the tx would sit permanently NOT_CLASSIFIED ("Sent" instead of
                // "Migrating…"/"Migrated"). One call is enough for both labels: mined-height driven
                // Pending→Success derivation is independent of zip318_kind and isn't reset by it.
                synchronizerProvider.getSynchronizerOrNull()?.enhanceTransaction(result.txId)
                // Everything below reads the engine's post-send state live — there is no cache to
                // write through anymore (the banner reads the same live states).
                val snapshot = sdk.snapshot()
                if (sentWasPrep) {
                    // "Transfer 0 of 11 complete" after a note split confused users (the crossing
                    // count ignores splits) — splits announce their own progress.
                    migrationNotifier.notifyNoteSplitProgress(
                        accountKeyId,
                        completedSplits = snapshot?.preparations?.count { it.isSent } ?: 0,
                        totalSplits = snapshot?.preparations?.size ?: 0,
                    )
                }
                // Single post-send engine read for every decision below (review L5).
                val postStates = sdk.getMigrationTransferStates()
                val anyUnsent = postStates?.transfers?.any { !it.isSent } == true
                if (anyUnsent) {
                    // Prep fast-track: whole ready prep batches go back-to-back — no send spacing
                    // between preparations (one logical tree, in-pool, nothing to de-correlate).
                    // CROSSINGS take the opposite rule: never two sends closer than the privacy
                    // buffer, even in catch-up (a starved worker once fired 5 overdue crossings in
                    // ~51 s — grid spacing means nothing if catch-up collapses it into one
                    // network-timing cluster). The non-fast-track case delegates to reArm — it
                    // targets the EARLIEST relevant moment across engine wake-ups and ALL unsent
                    // heights INCLUDING preparations; an ad-hoc crossing-only delay here used to
                    // sleep past inter-layer prep windows and compress the serial prep tail toward
                    // the crossings' anchor boundaries — the exact tx9 latency condition (review H1).
                    if (nextDueUnsentIsPreparation(postStates, sdk.estimatedChainTip())) {
                        scheduleUnlessLiveLoop(accountKeyId, PREP_FAST_TRACK_REARM)
                        migrationLog("MigrationDriveOnce: ready preparation next — chaining in $PREP_FAST_TRACK_REARM")
                        if (!sentWasPrep && snapshot != null) {
                            migrationNotifier.notifyTransferComplete(
                                accountKeyId,
                                snapshot.completedCount,
                                snapshot.totalCount,
                            )
                        }
                        DriveOnceResult.ReArmed(PREP_FAST_TRACK_REARM, respectAntiSpinFloor = false)
                    } else {
                        val delay = reArm(sdk, accountKeyId, floor = sdk.privacySyncBufferDuration())
                        if (!sentWasPrep && snapshot != null) {
                            migrationNotifier.notifyTransferComplete(
                                accountKeyId,
                                snapshot.completedCount,
                                snapshot.totalCount,
                            )
                        }
                        DriveOnceResult.ReArmed(delay)
                    }
                } else {
                    migrationNotifier.notifyMigrationComplete(accountKeyId)
                    // Everything sent — keep observing until the engine reports Complete (the
                    // completeRun stop). The next wake lands in waitingRun's completion-sweep
                    // branch, which runs a REAL sync so mining is actually observed (review M2).
                    val delay = reArm(sdk, accountKeyId, floor = sdk.privacySyncBufferDuration())
                    migrationLog("MigrationDriveOnce: all transfers sent — completion sweep armed.")
                    DriveOnceResult.ReArmed(delay)
                }
            }

            is TransferResult.NetworkError -> {
                // Retries already exhausted (or the failure was non-retryable) inside
                // executeWithRetries — settle into an error state now rather than asking
                // WorkManager for yet another attempt.
                migrationLog("MigrationDriveOnce: network error after retries, isTorFailure=${result.isTorFailure}")
                if (result.isTorFailure) {
                    // Persist a flag so app-open reconciliation (CheckMigrationRecoveryUseCase)
                    // routes back through the Sending screen instead of the generic
                    // manual-confirmation path, and surface a distinct notification.
                    pendingMigrationTorFailureStorageProvider.store(accountKeyId, true)
                    migrationNotifier.notifyMigrationTorFailure(accountKeyId)
                } else if (snapshotBefore?.nextPending != null) {
                    // Nothing else re-arms a future attempt for a non-retryable failure — the
                    // user must open the app and act, same as a missed/stalled window.
                    migrationNotifier.notifyManualConfirmationRequired(
                        accountKeyId,
                        snapshotBefore.nextPending!!.index + 1,
                        snapshotBefore.totalCount,
                    )
                }
                DriveOnceResult.Terminal
            }

            TransferResult.InvalidNote -> {
                // SDK Task 9 behavior change (spec 2026-08-05-migration-engine-full-delegation-design.md
                // §5): a genuinely-unknown broadcast rejection is now reported to the engine via
                // report_broadcast_failure (recordTransferResult tag=4) instead of being terminally
                // failed (tag=2's old behavior for this exact case). mapSubmitResult's non-gRPC-Failure
                // `else` branch — the ONLY producer of TransferResult.InvalidNote in the SDK — is what
                // reaches here (via executeNextPendingTransfer, this driver's only path into
                // handleExecuted), and that call site always applies the tag=2 -> tag=4 override. So the
                // persisted migration state after this outcome is InProgress, with the transaction
                // withheld under Blocker::AwaitingReevaluation — NOT RequiresAttention(InvalidTransfer)
                // as the old comment here claimed. No user action is required: the transaction is
                // offered again once the wallet's scan reaches the rejecting node's observed tip and
                // advance_migration adjudicates it — surfaced on a later nextStep() call as
                // MigrationAdvanceStep.Reevaluate, which run()'s Reevaluate branch drives via a real
                // syncRun. So: withhold + re-arm, NOT terminal-fail + notify — notifying "plan invalid"
                // here would be a false alarm for what is very often a transient rejection. (If the
                // transfer genuinely cannot mine, advance_migration's own foreign-spend detection
                // eventually routes it to Replan -> replanRun, which DOES notify the user.)
                migrationLog(
                    "MigrationDriveOnce: broadcast rejected for an unknown reason — withheld pending " +
                        "reevaluation, re-arming."
                )
                DriveOnceResult.ReArmed(reArm(sdk, accountKeyId))
            }

            TransferResult.Expired -> {
                // State is now RequiresAttention(TransferExpired) — the anchor expired before the
                // broadcast could happen. Distinct copy from InvalidNote, same terminal handling.
                migrationLog("MigrationDriveOnce: transfer expired — user action required on next open.")
                migrationNotifier.notifyTransferExpired(accountKeyId)
                DriveOnceResult.Terminal
            }
        }

    /**
     * The engine wants [transferId] rebuilt (expired today; unprovable-anchor too once the engine
     * change request ships). A rebuild needs a fresh signature, so it is user-driven: surface the
     * attention notification and stop re-arming — the home banner and the app-open router route
     * the user into the invalid/reschedule screen, and recovery re-arms the chain afterwards.
     */
    private suspend fun rebuildRun(sdk: OrchardMigrationSdk, accountKeyId: String, transferId: Long) {
        val snapshot = sdk.snapshot()
        migrationLog("MigrationDriveOnce: engine requests Rebuild{$transferId} — user-driven reschedule required.")
        migrationNotifier.notifyRescheduleRequired(
            accountKeyId,
            (snapshot?.nextPending?.index?.plus(1)) ?: 1,
            snapshot?.totalCount ?: 0,
        )
    }

    /**
     * The engine says the WHOLE plan is dead (past the committed replan threshold — see
     * [MigrationAdvanceStep.Replan]'s doc), not just one transfer. Unlike [rebuildRun] there is no
     * single [transferId][MigrationAdvanceStep.Rebuild.transferId] to point at; the entire plan
     * needs a user-driven re-plan/reschedule. Same terminal handling as a rebuild: surface the
     * attention notification and stop re-arming — the home banner and the app-open router take it
     * from here.
     */
    private suspend fun replanRun(sdk: OrchardMigrationSdk, accountKeyId: String) {
        val snapshot = sdk.snapshot()
        migrationLog(
            "MigrationDriveOnce: engine requests Replan — the whole plan is dead, user-driven reschedule required."
        )
        migrationNotifier.notifyRescheduleRequired(
            accountKeyId,
            (snapshot?.nextPending?.index?.plus(1)) ?: 1,
            snapshot?.totalCount ?: 0,
        )
    }

    /** All transactions mined — nothing left to fold anywhere; just stop the chain. */
    private suspend fun completeRun(accountKeyId: String) {
        migrationLog("MigrationDriveOnce: migration complete — stopping the work chain. (account=$accountKeyId)")
    }

    /**
     * The late-dependency guard veto that used to synthesize [MigrationBlocker.UNPROVABLE_ANCHOR]
     * shipped its fix upstream (rc.6: `engine::prove_transfer` re-draws the boundary at prove time
     * instead) — the backend no longer emits blocker code 6 (see `migration.rs`'s
     * `BLOCKER_UNPROVABLE_ANCHOR` comment, RESERVED not reused). This is now effectively dead —
     * kept only for wire compatibility with the same reserved code, mirroring the SDK's own
     * `6 -> MigrationBlocker.UNPROVABLE_ANCHOR` mapping — not removed outright given how widely
     * [MigrationBlocker.UNPROVABLE_ANCHOR] is still threaded through the UI layer
     * (`MigrationProgressVM`, `MigrationHomeMessageSourceImpl`, `MigrationTransferStatus`).
     */
    private suspend fun surfaceUnprovableBlocker(
        sdk: OrchardMigrationSdk,
        accountKeyId: String,
        states: MigrationTransferStates?,
    ) {
        val stuck = states?.transfers?.firstOrNull { it.blocker == MigrationBlocker.UNPROVABLE_ANCHOR } ?: return
        val snapshot = sdk.snapshot()
        migrationLog(
            "MigrationDriveOnce: transfer ${stuck.id} blocked on an unprovable anchor — user-driven reschedule " +
                "required."
        )
        migrationNotifier.notifyRescheduleRequired(
            accountKeyId,
            (snapshot?.nextPending?.index?.plus(1)) ?: 1,
            snapshot?.totalCount ?: 0,
        )
    }

    /**
     * Every WorkManager (re)schedule funnels through here so [liveLoopActive] gates it in one
     * place. While the live driver drives this call, it will locally `delay(...)` on the returned
     * Duration itself — re-enqueuing the durable WorkManager job is redundant, and REPLACE-cancels
     * whatever WorkManager run might be in flight for no benefit (2026-08-06 REPLACE-race fix: this
     * produced the START/STOP-canceled churn seen in that diagnosis). Whatever job was already
     * pending before the live driver started is left untouched and still serves as the
     * crash-safety backstop if the live driver dies unexpectedly.
     */
    private fun scheduleUnlessLiveLoop(accountKeyId: String, delay: Duration) {
        if (!liveLoopActive) {
            MigrationScheduler(applicationContext).schedule(accountKeyId, delay)
        }
    }

    /**
     * The "when?" half of the loop (core sync call 2026-08-05 §2.4's end-state): one future run
     * at the engine's own next execution point ([MigrationPeek], from a fresh [OrchardMigrationSdk.nextStep]
     * call) folded with the app's privacy quiet-gap term — see [nextWake]. Falls back to a flat
     * cadence when the peek is unavailable. Returns the concrete delay it armed, so the caller
     * (`run`) can report [DriveOnceResult.ReArmed] with it.
     *
     * The peek is fetched fresh here (not threaded from the caller's own `nextStep()` read) so it
     * always reflects state as of THIS re-arm decision, per [MigrationPeek]'s "holds only as of
     * the call that returned it" doc.
     */
    private suspend fun reArm(
        sdk: OrchardMigrationSdk,
        accountKeyId: String,
        floor: Duration = Duration.ZERO,
    ): Duration {
        val states = sdk.getMigrationTransferStates()
        val est = sdk.estimatedChainTip()
        val peek = sdk.nextStep()?.next
        val delay =
            nextWake(
                states,
                est,
                sdk.estimatedSecondsPerBlock(),
                lastActivityEpochSeconds = lastNetworkActivity.get()?.epochSecond,
                privacyBufferSeconds = sdk.privacySyncBufferDuration().inWholeSeconds,
                nowEpochSeconds = nowEpochSeconds(),
                peek = peek,
                nu7ActivationHeight = sdk.nu7ActivationHeight,
            )
        val armed = maxOf(delay ?: migrationCadence(), floor)
        scheduleUnlessLiveLoop(accountKeyId, armed)
        // The full "why" of the chosen wake, so timing is diagnosable from logs alone: the
        // engine's own peek, the tip estimate, and the floor.
        migrationLog(
            "MigrationDriveOnce: re-armed in $armed " +
                "(peek=${peek?.let { "${it.height}/${it.kind}" }}, " +
                "estimatedTip=$est, floor=$floor" +
                if (delay == null) ", cadence fallback)" else ")"
        )
        return armed
    }

    companion object {
        /** Process-wide: worker and live driver share this one instance. */
        private val DRIVE_LOCK = Mutex()

        // Set at the top of run() (inside the DRIVE_LOCK critical section) and read by every
        // scheduleUnlessLiveLoop call in the same execution. Safe without its own lock: DRIVE_LOCK
        // already guarantees only one run() call is ever inside its try block at a time,
        // process-wide, and kotlinx.coroutines.sync.Mutex's lock/unlock pair gives the memory-
        // visibility guarantee a plain var needs here (2026-08-06 REPLACE-race fix).
        private var liveLoopActive = false

        /**
         * How long a caller that lost the lock race waits before trying again. Deliberately
         * larger than a "normal" step's duration: when the winner is a forced broadcast window
         * (up to ~13 minutes: privacy buffer + broadcast timeout), a short retry here would have
         * the worker hammering `MigrationScheduler.schedule()` (WorkManager REPLACE + heartbeat
         * write + alarm re-arm) every few seconds for the whole window.
         */
        private val SKIP_RETRY_DELAY = 30.seconds
    }
}

/** Live snapshot of this SDK's engine states — the worker's plan view (never cached). */
private suspend fun OrchardMigrationSdk.snapshot(): LiveMigrationSnapshot? =
    getMigrationTransferStates()?.let {
        val est = estimatedChainTip()
        it.toSnapshot(
            estimatedTip = if (est >= 0) est else it.tipHeight,
            secondsPerBlock = estimatedSecondsPerBlock(),
            nowEpochSeconds = nowEpochSeconds(),
            nu7ActivationHeight = nu7ActivationHeight,
        )
    }

private val STATUS_READ_TIMEOUT = 2.seconds
internal val SYNC_TIMEOUT = 3.minutes
internal val REARM_FLOOR = 60.seconds
private val BROADCAST_ATTEMPT_TIMEOUT = 3.minutes

/**
 * Re-arm delay for the preparation fast-track: back-to-back scheduling for ready prep batches
 * (WorkManager dispatch latency is the only real gap) and the short retry when a fast-tracked
 * prep only lost its window to a live sync overlap.
 */
internal val PREP_FAST_TRACK_REARM = 1.seconds

internal const val MIN_REARM_SECONDS = 60L

/** Returns the current wall-clock time as epoch seconds. Extracted for testability. */
internal fun nowEpochSeconds(): Long = Clock.System.now().epochSeconds

/**
 * Fallback-only cadence: 5 min on testnet, 60 min on mainnet. Used ONLY when neither the engine's
 * wake-up schedule nor live transfer states (or the tip estimate) are available — every regular
 * wake is computed from the engine's own schedule instead (see [nextWake]).
 *
 * Uses [BuildConfig.FLAVOR] because the SDK's network id is not cheaply reachable from a static
 * context without a full OrchardMigrationSdk instance.
 */
internal fun migrationCadence(): Duration =
    if (BuildConfig.FLAVOR.contains("testnet", ignoreCase = true)) 5.minutes else 60.minutes

// Same attempt count (3) as MigrationSendingVM.sendOnce()'s foreground retry loop — but not the
// same retry trigger: sendOnce() retries while polling for readiness (result == null) and stops
// on any non-null result; this retries only on a retryable NetworkError and stops on null. Each
// loop is correct for its own context (foreground polls for the transfer becoming ready;
// background rides out a flaky network) — they just happen to share the same attempt budget.
private const val MAX_BROADCAST_ATTEMPTS = 3
private const val BROADCAST_RETRY_DELAY_MS = 1500L

/**
 * Calls [attempt] up to [maxAttempts] times, retrying only while the result is an
 * [TransferAttemptOutcome.Executed] wrapping a retryable [TransferResult.NetworkError] — anything
 * else (NothingDue, AwaitingProof, a non-retryable error, success) short-circuits immediately.
 * Returns null only when [attempt] itself returns null (should not happen with the current SDK
 * contract, but guards against future changes). Top-level and `internal` (rather than a private
 * method on [MigrationDriveOnce]) specifically so it's unit-testable without Koin or WorkManager,
 * neither of which this codebase has test infrastructure for today.
 */
internal suspend fun executeWithRetries(
    maxAttempts: Int = MAX_BROADCAST_ATTEMPTS,
    retryDelayMs: Long = BROADCAST_RETRY_DELAY_MS,
    attempt: suspend () -> TransferAttemptOutcome,
): TransferAttemptOutcome? {
    var result: TransferAttemptOutcome? = null
    for (i in 0 until maxAttempts) {
        if (i > 0) delay(retryDelayMs)
        result = attempt()
        val current = result
        val shouldRetry =
            current is TransferAttemptOutcome.Executed &&
                current.result is TransferResult.NetworkError &&
                (current.result as TransferResult.NetworkError).retryable
        if (!shouldRetry) break
    }
    return result
}

/**
 * What a broadcast run should do before calling the SDK's executeNextPendingTransfer.
 *
 * - [BroadcastPreflight.DEFER] — the foreground synchronizer is actively syncing, OR the privacy
 *   quiet gap since the last network activity has not yet elapsed. Engine untouched.
 * - [BroadcastPreflight.BROADCAST] — all sources are quiet and the gap has elapsed.
 */
internal enum class BroadcastPreflight { BROADCAST, DEFER }

/**
 * Pure preflight decision for a broadcast run — takes pre-computed scalars so it is unit-testable
 * without Koin, WorkManager or a real SDK.
 *
 * [lastNetworkActivityEpochSeconds] is null when no activity has ever been stamped (first run);
 * in that case the gap check is skipped and BROADCAST is returned.
 */
internal fun decideBroadcastPreflight(
    synchronizerSyncing: Boolean,
    nowEpochSeconds: Long,
    lastNetworkActivityEpochSeconds: Long?,
    privacyBufferSeconds: Long,
    prepFastTrack: Boolean = false,
): BroadcastPreflight {
    // Preparation fast-track (security split, 2026-07-30): note-split preparations are fully
    // shielded IN-POOL transactions — amounts and spend links hidden, natural recent anchor with
    // the same anonymity set as all ordinary Orchard traffic. The sync/broadcast de-correlation
    // ceremony exists for CROSSINGS (public amount + tiny migration-anchor anonymity set), and
    // during the prep phase no crossing exists to correlate against. So when the next due pending
    // transaction is a preparation, skip the quiet-gap and active-sync defers entirely; the
    // per-execution sync-XOR-broadcast rule still holds (this run never syncs).
    if (prepFastTrack) return BroadcastPreflight.BROADCAST
    if (synchronizerSyncing) return BroadcastPreflight.DEFER
    if (lastNetworkActivityEpochSeconds != null &&
        nowEpochSeconds - lastNetworkActivityEpochSeconds < privacyBufferSeconds
    ) {
        return BroadcastPreflight.DEFER
    }
    return BroadcastPreflight.BROADCAST
}

/**
 * The earliest unsent transaction in schedule order (id as tiebreak) — the "what comes next"
 * display/pacing candidate when nothing is broadcastable yet.
 */
internal fun earliestUnsent(states: MigrationTransferStates?): MigrationTransferState? =
    states
        ?.transfers
        ?.filter { !it.isSent }
        ?.sortedWith(compareBy({ it.scheduledHeight }, { it.id }))
        ?.firstOrNull()

/**
 * True when the earliest unsent transaction is a PREPARATION that is already proved and due by
 * the estimated tip — the trigger for the preparation fast-track (see [decideBroadcastPreflight])
 * and for immediate re-chaining after a prep broadcast (no send spacing: the prep tree is one
 * logical unit; within a layer there is no one-at-a-time requirement).
 */
internal fun nextDueUnsentIsPreparation(states: MigrationTransferStates?, estimatedTip: Long): Boolean {
    val next = earliestUnsent(states) ?: return false
    return !next.isTransfer && next.isProved && next.scheduledHeight <= estimatedTip
}

/**
 * Whether the specific transaction the engine named (e.g. [MigrationAdvanceStep.Broadcast]'s own
 * `transferId`) is a preparation — the precise, id-keyed check for the §2.1 same-beat decision, as
 * opposed to [nextDueUnsentIsPreparation]'s schedule-order guess at what the engine will serve next.
 */
internal fun isPreparationTransfer(states: MigrationTransferStates?, transferId: Long): Boolean =
    states?.transfers?.firstOrNull { it.id == transferId }?.isTransfer == false

/**
 * Estimated-tip broadcast acceleration predicate: a proved, unsent, non-stuck transaction whose
 * scheduled height the estimated tip has already crossed. The engine's own `nextStep` cannot see
 * past the scanned tip; this is the app-side bridge that keeps a backgrounded wallet broadcasting
 * on time (the actual send still re-verifies through the engine).
 */
internal fun broadcastDueByEstimate(states: MigrationTransferStates, estimatedTip: Long): Boolean {
    if (estimatedTip < 0L) return false
    return states.transfers.any {
        !it.isSent &&
            it.isProved &&
            it.blocker != MigrationBlocker.UNPROVABLE_ANCHOR &&
            it.scheduledHeight <= estimatedTip
    }
}

/**
 * What a genuine engine `Waiting` verdict resolves to (nextStep is Broadcast-authoritative, so a
 * due broadcast never reaches this decision — see [MigrationDriveOnce] `waitingRun`).
 */
internal enum class WaitingDisposition { COMPLETION_SWEEP, SURFACE_UNPROVABLE, RE_ARM }

/**
 * `nextStep()`'s `STEP_COMPLETE` is ambiguous on its own: the engine's `next_step` reports
 * `Complete` the instant a migration is TERMINAL (state.rs's `is_terminal()` short-circuit, checked
 * before anything else) — which is true not only for a genuinely finished migration but also for
 * one this same driver just marked `Superseded` (Task 7's `markMigrationSuperseded`, fired from
 * `OrchardMigrationSdkImpl.nextStep()`'s `STEP_REPLAN` branch) or `Failed`. A Superseded migration
 * was already handled at the moment it superseded — `replanRun`'s `notifyRescheduleRequired` — so a
 * LATER call landing here must not re-surface it as done. [sdk]'s `getMigrationState()` resolves
 * the ambiguity: it reads the persisted status directly (Superseded maps to `ReadyToPropose`, not
 * `Complete` — see `derive_migration_state` in `migration.rs`), so [onActuallyComplete] — the
 * caller's `completeRun` effects — only runs for a genuinely complete migration. Top-level
 * (rather than a method on [MigrationDriveOnce]) so adding this disambiguation does not itself push
 * the class over its function-count budget.
 */
private suspend fun handleCompleteStep(
    sdk: OrchardMigrationSdk,
    accountKeyId: String,
    onActuallyComplete: suspend () -> Unit,
): DriveOnceResult {
    if (isMigrationActuallyComplete(sdk.getMigrationState())) {
        onActuallyComplete()
    } else {
        migrationLog(
            "MigrationDriveOnce: nextStep reported Complete but the migration state is not " +
                "actually Complete (superseded/failed) — not surfacing as complete. (account=$accountKeyId)"
        )
    }
    return DriveOnceResult.Terminal
}

/**
 * The Superseded-vs-Complete disambiguation predicate — see [handleCompleteStep]'s doc for why
 * the step alone is ambiguous. Only a genuine [MigrationState.Complete] counts — every other case
 * (notably [MigrationState.ReadyToPropose], what a Superseded migration maps to) means
 * `completeRun`'s effects must NOT fire.
 */
internal fun isMigrationActuallyComplete(migrationState: MigrationState): Boolean =
    migrationState is MigrationState.Complete

internal fun waitingDisposition(allSent: Boolean, hasUnprovableBlocker: Boolean): WaitingDisposition =
    when {
        allSent -> WaitingDisposition.COMPLETION_SWEEP
        hasUnprovableBlocker -> WaitingDisposition.SURFACE_UNPROVABLE
        else -> WaitingDisposition.RE_ARM
    }

/**
 * The engine-side "when?" projection (core sync call 2026-08-05 §1/§2.4's end-state): the
 * engine's own peek-ahead at the next execution point ([MigrationPeek], from
 * [OrchardMigrationSdk.nextStep]'s [MigrationAdvanceResult.next]), converted to a wall-clock
 * delay at the measured block rate, floored at [MIN_REARM_SECONDS] (WorkManager slack / hot-loop
 * guard). Returns `null` (cadence fallback) when the tip estimate or the peek is unavailable.
 *
 * This is now the SOLE source of the engine-side wake height — no home-grown merge of
 * [OrchardMigrationSdk.syncWakeupSchedule] and a due-height scan over
 * [OrchardMigrationSdk.getMigrationTransferStates]. The peek needs no unprovable-anchor
 * exclusion the way that scan used to: it is re-verified against the store's satisfiability
 * oracle on every call (see [MigrationPeek]'s "holds only as of the call that returned it" doc)
 * rather than accumulating stale entries the way the app's own wake-up bookkeeping could.
 *
 * Engine-only — does not know about the app's privacy quiet gap; see [nextWake], which folds this
 * in with the gap term and is what callers should use.
 */
internal fun computeEngineWakeDelay(
    est: Long,
    secondsPerBlock: Long,
    peek: MigrationPeek?,
    nu7ActivationHeight: Long? = null,
): Duration? {
    if (est < 0L || peek == null) return null
    return estimatedSecondsBetweenHeights(est, maxOf(peek.height, est), secondsPerBlock, nu7ActivationHeight)
        .coerceAtLeast(MIN_REARM_SECONDS)
        .seconds
}

/**
 * The single re-arm source of truth (spec §5): `min(engine peek, app privacy-gap expiry)`.
 * The gap is an app concept the clock-free engine cannot express — a proved, unsent transaction
 * that is already due by estimate is broadcast-ready, but if we are inside the post-sync/
 * post-broadcast quiet window, the earliest it can actually go out is `quietUntil`. Re-arming to
 * the engine peek alone would ignore that wait; re-arming to the gap alone would ignore a nearer
 * engine wake (e.g. a cheaper prove). Do NOT sync while only the gap is pending — that would
 * reset it and starve the due broadcast (spec §4).
 *
 * The gap term ([states]/[broadcastDueByEstimate]) is deliberately untouched by the peek-ahead
 * adoption — core sync call §3 rules privacy-timing/quiet-gap heuristics a SEPARATE, not-yet-
 * scoped follow-up, not something the engine's execution-point peek is meant to replace.
 */
internal fun nextWake(
    states: MigrationTransferStates?,
    est: Long,
    secondsPerBlock: Long,
    lastActivityEpochSeconds: Long?,
    privacyBufferSeconds: Long,
    nowEpochSeconds: Long,
    peek: MigrationPeek?,
    nu7ActivationHeight: Long? = null,
): Duration? {
    val broadcastReadyGapped = states != null && broadcastDueByEstimate(states, est)
    val gapDelay =
        if (broadcastReadyGapped && lastActivityEpochSeconds != null) {
            val quietUntil = lastActivityEpochSeconds + privacyBufferSeconds
            (quietUntil - nowEpochSeconds).coerceAtLeast(0L).seconds
        } else {
            null
        }
    val engineDelay = computeEngineWakeDelay(est, secondsPerBlock, peek, nu7ActivationHeight)
    return listOfNotNull(engineDelay, gapDelay).minOrNull()
}
