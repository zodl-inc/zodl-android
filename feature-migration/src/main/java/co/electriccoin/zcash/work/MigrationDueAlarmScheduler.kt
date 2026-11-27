package co.electriccoin.zcash.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.common.model.accountIdOffset
import kotlin.time.Duration

/**
 * Arms an inexact-while-idle [AlarmManager] alarm alongside [MigrationScheduler]'s WorkManager job,
 * so [MigrationTransferDueReceiver] can surface a "transfer ready to send" notification (spec §6.4)
 * even when the app can't execute in the background at all. WorkManager jobs can be deferred
 * indefinitely by Doze/App-Standby when background execution is unavailable — this alarm is still
 * allowed to fire (with reduced precision) even while the device is idle, which is exactly the gap
 * this closes.
 *
 * Deliberately uses [AlarmManager.setAndAllowWhileIdle] rather than
 * `setExactAndAllowWhileIdle`/`setAlarmClock` — a transfer's due time is typically hours away, so
 * to-the-second precision isn't needed, and exact alarms require `SCHEDULE_EXACT_ALARM`/
 * `USE_EXACT_ALARM` plus a runtime permission check on API 31+ that this feature has no reason to
 * take on. No special permission is required for the inexact variant used here.
 *
 * Owned by [MigrationScheduler] (called from its `schedule()`/`cancel()`) rather than threaded
 * through every WorkManager call site separately — every place that currently arms or cancels the
 * background worker already goes through that one class.
 */
class MigrationDueAlarmScheduler(
    private val context: Context
) {
    fun schedule(accountKeyId: String, delay: Duration) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            migrationLog("MigrationDueAlarmScheduler: AlarmManager unavailable — skipping.")
            return
        }
        val triggerAtMillis = System.currentTimeMillis() + delay.inWholeMilliseconds
        migrationLog("MigrationDueAlarmScheduler: arming ready-to-send alarm for $accountKeyId in $delay")
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent(accountKeyId))
    }

    fun cancel(accountKeyId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(accountKeyId))
    }

    private fun pendingIntent(accountKeyId: String): PendingIntent {
        val intent =
            Intent(context, MigrationTransferDueReceiver::class.java).apply {
                putExtra(EXTRA_ACCOUNT_KEY_ID, accountKeyId)
            }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_BASE + accountIdOffset(accountKeyId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_ACCOUNT_KEY_ID = "co.electriccoin.zcash.migration.account_key_id"

        // Per-account request code = base + accountIdOffset(...) (offset range 0..0xFFFF). Hex base,
        // same self-documenting scheme as MigrationNotifier's request-code bases (0x10_0000/0x20_0000),
        // spaced ≥ 0x10000 from them so the ranges can't overlap even by accident. (A getBroadcast
        // PendingIntent is a separate namespace from the notifier's getActivity ones anyway, but
        // keeping one legible scheme avoids a future edit reintroducing an overlap.)
        private const val ALARM_REQUEST_CODE_BASE = 0x30_0000
    }
}
