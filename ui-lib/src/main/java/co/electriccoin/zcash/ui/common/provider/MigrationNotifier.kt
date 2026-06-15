package co.electriccoin.zcash.ui.common.provider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.R

class MigrationNotifier(private val context: Context) {

    private fun mainActivityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_MIGRATION, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_MIGRATION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ironwood Migration",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for Orchard to Ironwood migration progress"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun notifyTransferComplete(completed: Int, total: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_circle)
            .setContentTitle("Ironwood Migration")
            .setContentText("Transfer $completed of $total complete")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(mainActivityIntent())
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    fun notifyManualConfirmationRequired(transferIndex: Int, total: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_circle)
            .setContentTitle("Migration: Action Required")
            .setContentText("Transfer $transferIndex of $total is ready. Tap to confirm.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainActivityIntent())
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    fun notifyMigrationComplete() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_circle)
            .setContentTitle("Ironwood Migration Complete")
            .setContentText("All your funds have been migrated to Ironwood.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(mainActivityIntent())
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    companion object {
        const val CHANNEL_ID = "migration_channel"
        const val EXTRA_OPEN_MIGRATION = "co.electriccoin.zcash.migration.open_progress"
        private const val NOTIFICATION_ID_PROGRESS = 9001
        private const val REQUEST_CODE_MIGRATION = 9001
    }
}
