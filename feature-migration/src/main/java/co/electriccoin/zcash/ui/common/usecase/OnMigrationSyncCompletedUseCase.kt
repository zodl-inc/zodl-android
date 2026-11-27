package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.common.provider.LastNetworkActivityStorageProvider
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.work.MigrationScheduler
import kotlin.time.Duration.Companion.seconds

/**
 * Called on every foreground Status.SYNCED transition while a migration plan is active.
 * Finalizes any transfers whose funding note became witnessed since the last sync, then
 * checks for invalidations — if any, notifies the user and cancels the background worker
 * chain. Always stamps the last-network-activity timestamp so the worker's privacy-buffer gap
 * calculation is accurate — even on invalidation, the sync itself still happened.
 */
class OnMigrationSyncCompletedUseCase(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val lastNetworkActivity: LastNetworkActivityStorageProvider,
    private val migrationNotifier: MigrationNotifier,
    private val migrationScheduler: MigrationScheduler,
    private val context: Context,
) {
    suspend operator fun invoke(accountKeyId: String) {
        val sdk = getOrchardMigrationSdk(accountKeyId) ?: return
        val proved = sdk.finalizeReadyTransfers()
        val invalidated = sdk.reconcileInvalidations()
        migrationLog("ForegroundHook: SYNCED — proved=$proved, invalidated=$invalidated (account=$accountKeyId)")
        if (invalidated) {
            migrationNotifier.notifyMigrationPlanInvalid(accountKeyId)
            migrationScheduler.cancel(accountKeyId)
            lastNetworkActivity.stampNow()
            return
        }
        // Worker revival: this hook fires exactly when the wallet is provably up and synced — the
        // one moment a dead self-rechaining worker (process killed mid-run, app update consumed
        // the restored job before the SDK was ready) can be safely re-armed. The worker has NO
        // other reviver besides the app-open recovery pass (which can race a cold start; this
        // cannot): its re-arm only ever happens at the end of its own run, and its due alarms
        // don't survive a reinstall/update, so a package update mid-plan otherwise silently kills
        // all future runs (observed live: the 9th transfer proved and due, with no job left
        // anywhere to send it).
        // getMigrationStateUnreconciled(), not getMigrationState(): reconcileInvalidations() just
        // above already ran the real reconcile pass for this SYNCED transition (2026-08-07 read/
        // write-separation design — only rows 10-11's mutations, not this gate, need to reconcile),
        // so re-deriving state here needs no second mark-mined write-back of its own.
        if (sdk.getMigrationStateUnreconciled() is cash.z.ecc.android.sdk.MigrationState.InProgress) {
            if (!isMigrationWorkerActiveInWorkManager(context, accountKeyId)) {
                migrationLog("ForegroundHook: migration worker absent with a live plan — re-arming.")
                migrationScheduler.schedule(accountKeyId, 60.seconds)
            }
        }
        lastNetworkActivity.stampNow()
    }
}
