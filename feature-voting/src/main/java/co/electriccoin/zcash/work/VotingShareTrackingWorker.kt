package co.electriccoin.zcash.work

import android.content.Context
import androidx.annotation.Keep
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.electriccoin.zcash.ui.common.usecase.TrackVotingSharesUseCase
import co.electriccoin.zcash.ui.common.usecase.VotingShareTrackingResult
import co.electriccoin.zcash.voting.VOTING_ENABLED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Keep
class VotingShareTrackingWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters),
    KoinComponent {
    private val trackVotingShares: TrackVotingSharesUseCase by inject()

    override suspend fun doWork(): Result {
        val roundId = inputData.getString(VotingShareTrackingScheduler.INPUT_ROUND_ID)
        return when {
            !VOTING_ENABLED -> Result.success()
            roundId == null -> Result.failure()
            else -> trackCancellably(roundId)
        }
    }

    /**
     * `CoroutineWorker.onStopped()` is `final` on the androidx.work version this app resolves
     * (2.11.1 -- confirmed via `javap` against the resolved `work-runtime-2.11.1` artifact, which
     * shows `public final void onStopped()`), so [TrackVotingSharesUseCase.cancel] cannot be wired
     * from an overridden `onStopped()` the way this task was originally planned. WorkManager's
     * stop signal instead cancels this `doWork()` call's own coroutine `Job` internally, which is
     * not sufficient on its own either: [TrackVotingSharesUseCase.invoke]'s session `run()` call
     * bridges into a blocking native call, and a suspend function's `CancellationException` only
     * surfaces at its *next* suspension point -- i.e. only after that blocking call has already
     * returned on its own, which is the exact indefinite hang this task exists to fix.
     *
     * [isStopped] (public, stable, and exactly the documented escape hatch for this scenario now
     * that `onStopped()` is sealed off) is polled from a watcher coroutine racing alongside the
     * actual [track] call; the moment it flips, [TrackVotingSharesUseCase.cancel] is called
     * directly, reaching the native session within one poll interval instead of leaving it
     * running for the rest of the WorkManager execution-time-limit window.
     *
     * The watcher deliberately runs on its own [CoroutineScope] backed by a [SupervisorJob] --
     * NOT as a structural child of this `doWork()` call's own coroutine `Job` (confirmed via
     * `javap` on `ListenableWorker.class`: `stop(int)` flips the `isStopped`-backing flag via a
     * plain `AtomicInteger` CAS, wholly independent of whatever separately cancels the `Job`
     * behind the `ListenableFuture` `startWork()` returns). A plain `launch` child of that same
     * Job would spend nearly all of its time parked in [delay], a cancellable suspension point;
     * if the surrounding Job's cancellation reaches it while asleep there, `delay` throws
     * `CancellationException` immediately and unwinds the loop WITHOUT ever reaching the
     * `isStopped` check below, so [TrackVotingSharesUseCase.cancel] would silently never run --
     * reintroducing the exact indefinite-hang failure mode this task exists to fix, in precisely
     * the real-stop-signal scenario that matters most. An independent scope's own cancellation is
     * driven solely by the explicit `watcherScope.cancel()` in the `finally` block below, so it is
     * immune to that race.
     */
    private suspend fun trackCancellably(roundId: String): Result {
        val watcherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        watcherScope.launch {
            while (isActive) {
                if (isStopped) {
                    trackVotingShares.cancel(roundId)
                    break
                }
                delay(STOP_POLL_INTERVAL_MILLIS)
            }
        }
        return try {
            track(roundId)
        } finally {
            watcherScope.cancel()
        }
    }

    private suspend fun track(roundId: String): Result =
        runCatching {
            trackVotingShares(roundId)
        }.fold(
            onSuccess = { outcome ->
                when (outcome) {
                    VotingShareTrackingResult.Completed -> {
                        Result.success()
                    }

                    is VotingShareTrackingResult.Pending -> {
                        VotingShareTrackingScheduler(applicationContext)
                            .schedule(roundId, outcome.delayMillis)
                        Result.success()
                    }
                }
            },
            onFailure = {
                Result.retry()
            }
        )

    private companion object {
        const val STOP_POLL_INTERVAL_MILLIS = 500L
    }
}
