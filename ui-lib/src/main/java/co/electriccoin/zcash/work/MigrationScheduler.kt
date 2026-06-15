package co.electriccoin.zcash.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.BuildConfig
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class MigrationScheduler(private val context: Context) {
    fun schedule(delay: Duration) {
        Twig.debug { "MigrationScheduler: scheduling next migration transfer in $delay" }
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_ID,
            ExistingWorkPolicy.REPLACE,
            newWorkRequest(delay)
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_ID)
    }

    companion object {
        const val WORK_ID = "co.electriccoin.zcash.migration_transfer"

        fun newWorkRequest(delay: Duration): OneTimeWorkRequest {
            val constraints = if (BuildConfig.DEBUG) {
                Constraints.NONE
            } else {
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            }
            return OneTimeWorkRequestBuilder<MigrationWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delay.toJavaDuration())
                .build()
        }
    }
}
