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
import co.electriccoin.zcash.ui.common.model.accountIdOffset
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier.Companion.CHANNEL_ID
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier.Companion.LEGACY_DEFAULT_IMPORTANCE_CHANNEL_ID

/**
 * Emits the system notifications that drive the background (app-closed) portion of an Orchard→
 * Ironwood migration and let the user tap back into it. There is one instance per app process
 * (see [ZcashApplication]); every method is keyed by an account so a Zodl and a Keystone account
 * migrating in parallel never overwrite each other's notifications.
 *
 * All copy is resolved from string resources at build time via [Context.getString] (notifications
 * need a concrete String, not a Compose `StringResource`), so it is localized like the rest of the
 * UI.
 *
 * @param context an application-scoped [Context] used both to build the [PendingIntent]s and to
 *   post/cancel notifications; must outlive individual notifications, hence application scope.
 */
class MigrationNotifier(
    private val context: Context
) {
    // NOTE (reviewer: "an intent for a given activity should be built ONLY by a factory in that
    // activity's companion object"): the two intents below target MainActivity, which lives in
    // ui-lib. feature-migration depends on ui-lib (not vice versa), and the extra keys
    // (EXTRA_OPEN_MIGRATION / EXTRA_RUN_STEP / EXTRA_ACCOUNT_KEY_ID) plus the per-account
    // PendingIntent request codes are migration concerns. Moving construction into
    // MainActivity.Companion would force ui-lib to reference feature-migration constants — a
    // circular dependency — so the factory stays here by necessity. See the deferred-factory note
    // in the review report.

    /**
     * Tap target that simply opens the migration UI for [accountKeyId] on the correct account. Uses
     * a per-account request code so re-issuing it updates (rather than duplicates) the existing
     * [PendingIntent].
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account; carried as an
     *   Intent extra (String because Intents can't hold an `AccountUuid`) and used to derive the
     *   per-account request code.
     */
    private fun mainActivityIntent(accountKeyId: String): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_MIGRATION, true)
                putExtra(EXTRA_ACCOUNT_KEY_ID, accountKeyId)
            }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_MIGRATION_BASE + accountIdOffset(accountKeyId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // Distinct request code AND a distinct intent extra from mainActivityIntent()'s
    // EXTRA_OPEN_MIGRATION: the step-due tap must RE-KICK the worker (handleIntent schedules an
    // immediate run) besides opening Progress — background execution needs no UI, the app open
    // exists only to give the OS a live process to run the worker in.
    private fun runStepIntent(accountKeyId: String): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_RUN_STEP, true)
                putExtra(EXTRA_ACCOUNT_KEY_ID, accountKeyId)
            }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_TRANSFER_READY_BASE + accountIdOffset(accountKeyId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stepDueNotificationId(accountKeyId: String): Int =
        NOTIFICATION_ID_STEP_DUE_BASE + accountIdOffset(accountKeyId)

    private fun progressNotificationId(accountKeyId: String): Int =
        NOTIFICATION_ID_PROGRESS_BASE + accountIdOffset(accountKeyId)

    /**
     * Registers the notification channel every migration notification posts to. Must be called once
     * before any `notify*` method (done at app start). The channel name/description are user-visible
     * in the system Settings app, so they come from localized resources.
     *
     * [CHANNEL_ID] is a NEW id, not a re-registration of the old "migration_channel" at a higher
     * importance: Android does not let an app raise an EXISTING channel's importance by re-creating
     * it with the same id — [NotificationManager.createNotificationChannel] silently no-ops on the
     * importance field for a channel that already exists on the device. That is the actual reason
     * every migration notification never showed a heads-up popup, including the ones already setting
     * [NotificationCompat.PRIORITY_HIGH] at the per-notification level: channel importance, not
     * per-notification priority, is what gates heads-up on API 26+ (this app's entire supported
     * range, minSdk 27) — the per-notification priority calls are effectively dead weight there
     * (2026-08-06 Fable review). [LEGACY_DEFAULT_IMPORTANCE_CHANNEL_ID] is deleted so an upgrading
     * install doesn't leave a dead, empty "Migration" entry behind in Settings > App notifications.
     */
    fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.migration_notification_channelName),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.migration_notification_channelDescription)
            }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        manager.deleteNotificationChannel(LEGACY_DEFAULT_IMPORTANCE_CHANNEL_ID)
    }

    /**
     * Progress of the note-split (preparation) phase — splits are internal plumbing, so they never
     * announce crossing counts ("Transfer 0 of 11 complete" read as zero progress); they announce
     * their own.
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account.
     * @param completedSplits number of note-splits already done.
     * @param totalSplits total note-splits planned; when `<= 0` a generic "preparing" copy is shown
     *   instead of a count.
     */
    fun notifyNoteSplitProgress(accountKeyId: String, completedSplits: Int, totalSplits: Int) {
        val contentText =
            if (totalSplits > 0) {
                context.getString(
                    R.string.migration_notification_noteSplitProgress,
                    completedSplits,
                    totalSplits
                )
            } else {
                context.getString(R.string.migration_notification_noteSplitPreparing)
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_title))
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainActivityIntent(accountKeyId))
                .setAutoCancel(true)
                // The prep fast-track chains ready split broadcasts back-to-back (PREP_FAST_TRACK_REARM,
                // ~1s apart) — without this, now that the channel actually heads-ups (see createChannel's
                // kdoc), a fast batch would pop up a fresh heads-up for every single split update on the
                // same notification id. onlyAlertOnce only suppresses the ALERT (sound/vibration/heads-up)
                // for an update to an ALREADY-SHOWING notification; the content still updates, and a
                // genuinely new notification (nothing showing yet) still alerts normally.
                .setOnlyAlertOnce(true)
                .build()

        NotificationManagerCompat.from(context).notify(progressNotificationId(accountKeyId), notification)
    }

    /**
     * Announces that a migration transfer just landed.
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account.
     * @param completed number of transfers completed so far.
     * @param total total transfers in the plan.
     */
    fun notifyTransferComplete(accountKeyId: String, completed: Int, total: Int) {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_title))
                .setContentText(
                    context.getString(R.string.migration_notification_transferComplete, completed, total)
                ).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainActivityIntent(accountKeyId))
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(progressNotificationId(accountKeyId), notification)
    }

    /**
     * Strict-order escalation: the plan's head transfer stayed unprovable across a completed sync
     * — with strict ordering everything behind it is blocked, so the user must reschedule (the
     * app-open recovery routes to the invalid/reschedule screen).
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account.
     * @param transferIndex 1-based position of the blocked transfer; when `<= 0` (together with
     *   [total]) a generic "blocked" copy is shown instead of a count.
     * @param total total transfers in the plan; see [transferIndex].
     */
    fun notifyRescheduleRequired(accountKeyId: String, transferIndex: Int, total: Int) {
        val contentText =
            if (total > 0 && transferIndex > 0) {
                context.getString(
                    R.string.migration_notification_rescheduleWithCounts,
                    transferIndex,
                    total
                )
            } else {
                context.getString(R.string.migration_notification_rescheduleGeneric)
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_title))
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainActivityIntent(accountKeyId))
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(progressNotificationId(accountKeyId), notification)
    }

    /**
     * Prompts the user to hardware-sign / confirm the next transfer (Keystone flow).
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account.
     * @param transferIndex 1-based position of the transfer awaiting confirmation; when `<= 0`
     *   (together with [total]) a generic "a transfer is ready" copy is shown instead of a count.
     * @param total total transfers in the plan; see [transferIndex].
     */
    fun notifyManualConfirmationRequired(accountKeyId: String, transferIndex: Int, total: Int) {
        // F7: render real "Transfer X of Y" counts when the caller has them; fall back to generic
        // copy when they're unknown (total <= 0 or index <= 0) instead of the meaningless
        // "Transfer 0 of 0" the escalation call site used to pass.
        val contentText =
            if (total > 0 && transferIndex > 0) {
                context.getString(
                    R.string.migration_notification_manualConfirmationWithCounts,
                    transferIndex,
                    total
                )
            } else {
                context.getString(R.string.migration_notification_manualConfirmationGeneric)
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_actionRequiredTitle))
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainActivityIntent(accountKeyId))
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(progressNotificationId(accountKeyId), notification)
    }

    /**
     * A scheduled transfer couldn't be broadcast over Tor; the user must open the app to resolve.
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account.
     */
    fun notifyMigrationTorFailure(accountKeyId: String) {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_torFailureTitle))
                .setContentText(context.getString(R.string.migration_notification_torFailureText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainActivityIntent(accountKeyId))
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(progressNotificationId(accountKeyId), notification)
    }

    /**
     * Dead-man's-switch fallback (design 2026-07-30): the worker missed its expected run — a
     * migration STEP (prove or broadcast; everything is pre-signed, no user review exists) is due
     * and nothing is executing it. Tapping opens the app, which silently re-kicks the worker; the
     * worker's own next run start cancels this via [cancelStepDue].
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account; also selects
     *   the per-account notification id so [cancelStepDue] can dismiss exactly this one.
     */
    fun notifyMigrationStepDue(accountKeyId: String) {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_title))
                .setContentText(context.getString(R.string.migration_notification_stepDueText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(runStepIntent(accountKeyId))
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(stepDueNotificationId(accountKeyId), notification)
    }

    /**
     * The worker ran — the step-due fallback (if showing) is obsolete.
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the account whose step-due
     *   notification (posted by [notifyMigrationStepDue]) should be dismissed.
     */
    fun cancelStepDue(accountKeyId: String) {
        NotificationManagerCompat.from(context).cancel(stepDueNotificationId(accountKeyId))
    }

    // Spec §6.2 (Migration Plan Update) — notes were spent outside the migration flow, invalidating
    // the plan. Kept distinct from notifyTransferExpired() below (spec §6.3) even though both
    // currently deliver through the same TransferResult.InvalidNote/Expired branch in
    // MigrationWorker — the two causes read differently to the user, matching the distinct
    // Transfer Invalid screen copy (see MigrationAttentionKind).

    /**
     * The migration plan was invalidated (notes spent outside the flow); the user must reopen the
     * app to review/rebuild it.
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account.
     */
    fun notifyMigrationPlanInvalid(accountKeyId: String) {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_title))
                .setContentText(context.getString(R.string.migration_notification_planInvalidText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainActivityIntent(accountKeyId))
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(progressNotificationId(accountKeyId), notification)
    }

    // Spec §6.3 (Transfer(s) Expired) — one or more transfers expired without executing (the app
    // wasn't opened in time to broadcast them before their anchor expired).

    /**
     * One or more transfers expired unexecuted (app wasn't opened before their anchor expired); the
     * user must reopen the app to continue.
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrating account.
     */
    fun notifyTransferExpired(accountKeyId: String) {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_title))
                .setContentText(context.getString(R.string.migration_notification_transferExpiredText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainActivityIntent(accountKeyId))
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(progressNotificationId(accountKeyId), notification)
    }

    /**
     * Dismisses whatever migration notification is currently showing for [accountKeyId]. All
     * notify* methods above share the single per-account [progressNotificationId], so one cancel
     * covers them all — used when the migration itself is discarded (debug "Migration restart"),
     * where a leftover "ready to send"/"Tor failure" notification would tap into a migration that
     * no longer exists.
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the account whose progress
     *   notification should be dismissed.
     */
    fun cancel(accountKeyId: String) {
        NotificationManagerCompat.from(context).cancel(progressNotificationId(accountKeyId))
    }

    /**
     * Terminal success notification: the whole migration finished.
     *
     * @param accountKeyId storage-key id ([toStorageKeyId]) of the migrated account.
     */
    fun notifyMigrationComplete(accountKeyId: String) {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_circle)
                .setContentTitle(context.getString(R.string.migration_notification_completeTitle))
                .setContentText(context.getString(R.string.migration_notification_completeText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainActivityIntent(accountKeyId))
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(progressNotificationId(accountKeyId), notification)
    }

    companion object {
        const val CHANNEL_ID = "migration_channel_high"

        // The original channel, registered at IMPORTANCE_DEFAULT (no heads-up on API 26+) —
        // superseded by CHANNEL_ID; createChannel() deletes it on every app start so it doesn't
        // linger as a dead entry in Settings > App notifications on upgrading installs.
        private const val LEGACY_DEFAULT_IMPORTANCE_CHANNEL_ID = "migration_channel"

        const val EXTRA_OPEN_MIGRATION = "co.electriccoin.zcash.migration.open_progress"
        const val EXTRA_RUN_STEP = "co.electriccoin.zcash.migration.run_step"

        /**
         * The storage-key id ([co.electriccoin.zcash.ui.common.model.toStorageKeyId]) of the
         * account this notification belongs to. `handleIntent` selects that account before
         * navigating, so tapping a Keystone account's migration notification while the Zodl
         * account is selected lands on the RIGHT account's migration screens.
         */
        const val EXTRA_ACCOUNT_KEY_ID = "co.electriccoin.zcash.migration.account_key_id"

        // Notification-id namespace (NotificationManager ids). Independent of the PendingIntent
        // request-code namespace below — sharing the same numeric base value across the two namespaces
        // does not collide. Per-account via `+ accountIdOffset(...)` (range 0..0xFFFF).
        private const val NOTIFICATION_ID_PROGRESS_BASE = 0x10_0000
        private const val NOTIFICATION_ID_STEP_DUE_BASE = 0x40_0000

        // PendingIntent request-code namespace. The two bases are spaced 0x10_0000 apart — far more than
        // accountIdOffset's 0..0xFFFF range — so per-account request-code ranges can never overlap.
        private const val REQUEST_CODE_MIGRATION_BASE = 0x10_0000
        private const val REQUEST_CODE_TRANSFER_READY_BASE = 0x20_0000
    }
}
