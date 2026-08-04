package co.electriccoin.zcash.work

import android.content.Context
import androidx.annotation.Keep
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.electriccoin.zcash.ui.common.usecase.TrackVotingSharesUseCase
import co.electriccoin.zcash.ui.common.usecase.VotingShareTrackingResult
import co.electriccoin.zcash.ui.screen.more.VOTING_ENABLED
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
            else -> track(roundId)
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
}
