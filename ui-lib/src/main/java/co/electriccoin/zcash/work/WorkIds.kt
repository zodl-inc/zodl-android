package co.electriccoin.zcash.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import co.electriccoin.zcash.spackle.Twig

object WorkIds {
    const val WORK_ID_BACKGROUND_SYNC = "co.electriccoin.zcash.background_sync"

    /** Unique-work name for the wallet-global Lane A migration sync heartbeat. */

    fun enableBackgroundSynchronization(context: Context) {
        val workManager = WorkManager.getInstance(context)

        Twig.debug {
            "BG Sync: existing work details:" +
                " ${workManager.getWorkInfosForUniqueWork(WORK_ID_BACKGROUND_SYNC).get()}"
        }

        // Note: Re-enqueuing existing work is okay. Another approach would be to validate the existing work and
        // enqueue it if it is not planned yet or not in a valid state
        workManager.enqueueUniquePeriodicWork(
            WORK_ID_BACKGROUND_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.newWorkRequest()
        )

        Twig.debug {
            "BG Sync: newly enqueued work details:" +
                " ${workManager.getWorkInfosForUniqueWork(WORK_ID_BACKGROUND_SYNC).get()}"
        }
    }

    fun disableBackgroundSynchronization(context: Context) {
        val workManager = WorkManager.getInstance(context)

        workManager.cancelUniqueWork(WORK_ID_BACKGROUND_SYNC)
    }
}
