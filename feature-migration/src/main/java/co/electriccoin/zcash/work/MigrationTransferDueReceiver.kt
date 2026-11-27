package co.electriccoin.zcash.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The worker's DEAD-MAN'S SWITCH (design with the product owner, 2026-07-30): an inexact-while-idle alarm
 * armed by [MigrationScheduler] a late margin PAST the worker's expected run. By construction,
 * firing means the OS had its chance to run the worker and didn't (Doze, App-Standby, OEM
 * killers, user-disabled background) — so this compares the [MigrationWorkerHeartbeat] stamps
 * and, if the worker really did not run, raises the generic STEP-DUE fallback notification
 * (prove and broadcast alike — everything is pre-signed, there is nothing to review). Tapping it
 * opens the app, which re-kicks the worker; the worker's own next run start cancels the
 * notification again.
 *
 * Deliberately does the absolute minimum: two SharedPreferences reads — no network, no wallet DB,
 * no Synchronizer — so it can run under the exact conditions that killed the worker.
 */
class MigrationTransferDueReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val migrationNotifier: MigrationNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val accountKeyId = intent.getStringExtra(MigrationDueAlarmScheduler.EXTRA_ACCOUNT_KEY_ID)
        if (accountKeyId == null) {
            migrationLog("MigrationStepDueReceiver: no accountKeyId extra (pre-upgrade alarm) — no-op.")
            return
        }

        val expectedRunAt = MigrationWorkerHeartbeat.nextWakeAt(context, accountKeyId)
        val lastRunAt = MigrationWorkerHeartbeat.lastRunAt(context, accountKeyId)
        if (expectedRunAt == null) {
            migrationLog("MigrationStepDueReceiver: no expected run recorded — no-op.")
            return
        }
        if (lastRunAt != null && lastRunAt >= expectedRunAt - RUN_TOLERANCE_MILLIS) {
            migrationLog(
                "MigrationStepDueReceiver: worker ran on time (lastRunAt=$lastRunAt, expected=$expectedRunAt) — no-op."
            )
            return
        }
        migrationLog(
            "MigrationStepDueReceiver: worker MISSED its run (expected=$expectedRunAt, " +
                "lastRunAt=$lastRunAt) — raising the step-due fallback notification."
        )
        migrationNotifier.notifyMigrationStepDue(accountKeyId)
    }

    private companion object {
        /**
         * A run that started slightly BEFORE the recorded expected time (WorkManager dispatching
         * early, or the re-arm overwriting nextWakeAt just before this alarm fired) still counts
         * as "the worker is alive".
         */
        const val RUN_TOLERANCE_MILLIS = 30_000L
    }
}
