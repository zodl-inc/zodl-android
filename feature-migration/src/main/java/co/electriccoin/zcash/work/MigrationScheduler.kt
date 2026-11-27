package co.electriccoin.zcash.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import co.electriccoin.zcash.migration.BuildConfig
import co.electriccoin.zcash.migration.migrationLog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * No iOS-style margin/cadence-reset constants (first +30 min, then +6.5 h) are ported here on
 * purpose: unlike iOS's BGProcessingTask, [androidx.work.WorkManager.enqueueUniqueWork] with
 * [androidx.work.OneTimeWorkRequest.Builder.setInitialDelay] is driven directly and
 * deterministically from the SDK's authoritative `scheduledAt`/height-derived time — there's no
 * OS-level "best-effort earliest begin" uncertainty to buffer against. The real Android-side
 * timing risk is Doze/App-Standby deferral, and that's already handled by the overdue-recovery
 * self-healing path (`CheckMigrationRecoveryUseCase` + `hasOverdueTransfers()`), not by adding
 * scheduling margins here — except for the specific case where the app can't run in the
 * background at all (see [MigrationDueAlarmScheduler]), where an inexact-while-idle `AlarmManager`
 * alarm is armed/cancelled alongside the WorkManager job below purely to surface a "ready to send"
 * notification (spec §6.4), never to run the transfer itself.
 */
class MigrationScheduler(
    private val context: Context
) {
    // Armed/cancelled alongside the WorkManager job below rather than threaded through every call
    // site separately — see MigrationDueAlarmScheduler's kdoc for why this needs its own
    // AlarmManager alarm instead of relying on the WorkManager job itself.
    private val migrationDueAlarmScheduler = MigrationDueAlarmScheduler(context)

    fun schedule(accountKeyId: String, delay: Duration) {
        migrationLog("MigrationScheduler: scheduling next migration transfer for $accountKeyId in $delay")
        WorkManager.getInstance(context).enqueueUniqueWork(
            workId(accountKeyId),
            ExistingWorkPolicy.REPLACE,
            newWorkRequest(accountKeyId, delay)
        )
        // Dead-man's switch: record when this run is EXPECTED, and arm the fallback alarm a late
        // margin past it — the alarm firing means the worker had its chance and did not run
        // (Doze/OEM kill/background restriction); MigrationTransferDueReceiver then raises the
        // step-due notification whose tap re-kicks the worker through the app.
        MigrationWorkerHeartbeat.stampScheduled(
            context,
            accountKeyId,
            System.currentTimeMillis() + delay.inWholeMilliseconds,
        )
        migrationDueAlarmScheduler.schedule(accountKeyId, delay + WORKER_LATE_MARGIN)
    }

    fun cancel(accountKeyId: String) {
        migrationLog("MigrationScheduler: cancelling the migration work chain for $accountKeyId")
        WorkManager.getInstance(context).cancelUniqueWork(workId(accountKeyId))
        migrationDueAlarmScheduler.cancel(accountKeyId)
        MigrationWorkerHeartbeat.clear(context, accountKeyId)
    }

    companion object {
        /**
         * How long past the expected worker run the fallback alarm waits before concluding the
         * worker is dead — absorbs ordinary WorkManager dispatch slack without false alarms.
         */
        val WORKER_LATE_MARGIN = 2.minutes

        const val WORK_ID_PREFIX = "co.electriccoin.zcash.migration_transfer"
        const val KEY_ACCOUNT_KEY_ID = "co.electriccoin.zcash.migration.work_account_key_id"

        fun workId(accountKeyId: String): String = "${WORK_ID_PREFIX}_$accountKeyId"

        fun newWorkRequest(accountKeyId: String, delay: Duration): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<MigrationWorker>()
                .setConstraints(workConstraints())
                .setInitialDelay(delay.toJavaDuration())
                .setInputData(workDataOf(KEY_ACCOUNT_KEY_ID to accountKeyId))
                .build()

        private fun workConstraints(): Constraints =
            if (BuildConfig.DEBUG) {
                Constraints.NONE
            } else {
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            }
    }
}
