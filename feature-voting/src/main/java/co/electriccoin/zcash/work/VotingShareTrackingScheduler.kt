package co.electriccoin.zcash.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class VotingShareTrackingScheduler(
    private val context: Context
) {
    fun schedule(
        roundId: String,
        delayMillis: Long = DEFAULT_DELAY_MILLIS
    ) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            workId(roundId),
            ExistingWorkPolicy.REPLACE,
            newWorkRequest(roundId, delayMillis)
        )
    }

    companion object {
        internal const val INPUT_ROUND_ID = "round_id"
        private const val DEFAULT_DELAY_MILLIS = 15_000L

        private fun workId(roundId: String) =
            "co.electriccoin.zcash.voting_share_tracking.${roundId.lowercase()}"

        private fun newWorkRequest(
            roundId: String,
            delayMillis: Long
        ): OneTimeWorkRequest {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()

            return OneTimeWorkRequestBuilder<VotingShareTrackingWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(INPUT_ROUND_ID to roundId))
                .setInitialDelay(delayMillis.coerceAtLeast(0L).milliseconds.toJavaDuration())
                .build()
        }
    }
}
