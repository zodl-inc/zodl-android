package co.electriccoin.zcash.work

import android.content.Context
import androidx.annotation.Keep
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.MigrationSdkLookup
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The single migration execution worker — the app-side half of the engine-driven loop
 * (spec: 2026-07-30-engine-state-machine-adoption-design.md).
 *
 * A thin WorkManager wrapper around [MigrationDriveOnce] — the shared "what now, do it, re-arm"
 * decision-and-execution logic lives there so a future live-process driver can call the exact same
 * logic without ever executing concurrently with this worker (both share [MigrationDriveOnce]'s
 * process-wide lock). This class only supplies WorkManager-specific plumbing: resolving the
 * account/SDK, translating [DriveOnceResult] into [Result], and the kill-switch/not-ready retry
 * handling around SDK lookup.
 */
@Keep
class MigrationWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters),
    KoinComponent {
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase by inject()
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase by inject()
    private val migrationNotifier: MigrationNotifier by inject()
    private val migrationDriveOnce: MigrationDriveOnce by inject()

    override suspend fun doWork(): Result {
        val accountKeyId =
            inputData.getString(MigrationScheduler.KEY_ACCOUNT_KEY_ID)
                ?: getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId().also {
                    migrationLog(
                        "Worker: no accountKeyId in inputData — falling back to selected account $it " +
                            "(pre-upgrade job)"
                    )
                }

        migrationNotifier.cancelStepDue(accountKeyId)

        val sdk =
            when (val lookup = getOrchardMigrationSdk.lookup(accountKeyId)) {
                is MigrationSdkLookup.Ready -> {
                    lookup.sdk
                }

                MigrationSdkLookup.NotReady -> {
                    // A not-yet-initialized wallet right after an app update/reboot must not
                    // silently consume (and thereby kill) the self-rechaining loop — retry until
                    // the SDK is reachable. Stamp the heartbeat here too so the dead-man's-switch
                    // (MigrationTransferDueReceiver) sees the worker as alive-but-waiting instead
                    // of mistaking this in-flight WorkManager backoff for a missed run.
                    migrationLog("Worker: SDK not ready — retrying via WorkManager backoff.")
                    MigrationWorkerHeartbeat.stampRun(applicationContext, accountKeyId)
                    return Result.retry()
                }

                MigrationSdkLookup.Gone -> {
                    // Kill switch: the wallet was deleted or this (Keystone) account disconnected.
                    // Retrying would zombie-loop forever for an owner that no longer exists.
                    migrationLog("Worker: account/wallet gone — cancelling the migration work chain.")
                    MigrationScheduler(applicationContext).cancel(accountKeyId)
                    migrationNotifier.cancel(accountKeyId)
                    return Result.success()
                }
            }

        when (val result = migrationDriveOnce.run(sdk, accountKeyId)) {
            is DriveOnceResult.ReArmed -> {
                // Already re-armed the WorkManager chain inside run() — nothing more to do.
            }

            is DriveOnceResult.LockBusy -> {
                // No step ran, so nothing re-armed the chain — this call must, or a lost lock
                // race silently kills the durable background path.
                MigrationScheduler(applicationContext).schedule(accountKeyId, result.retryDelay)
            }

            DriveOnceResult.Terminal -> {
                // Complete, or a terminal-until-user-acts state — the chain intentionally ends
                // here, matching today's behavior exactly.
            }
        }
        return Result.success()
    }
}
