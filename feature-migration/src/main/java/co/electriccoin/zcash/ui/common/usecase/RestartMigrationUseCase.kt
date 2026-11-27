package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.repository.MigrationTransferStateRepository
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepository
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepository
import co.electriccoin.zcash.work.MigrationLiveDriver
import co.electriccoin.zcash.work.MigrationScheduler

/**
 * Resets the selected account's stuck migration for the user-facing "Restart Migration" flow.
 * Clears the persisted run and every piece of app-side state that references it, so the home
 * banner returns to a clean "Migrate now" and the user re-enters the normal migration flow. This
 * is the production promotion of the former debug-only restart orchestration (see
 * `MigrationDebugActionsImpl.restartMigration()`, which now delegates here); idempotent and safe
 * when nothing is scheduled.
 */
class RestartMigrationUseCase(
    private val accountDataSource: AccountDataSource,
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val migrationScheduler: MigrationScheduler,
    private val pendingMigrationTorFailureStorageProvider: PendingMigrationTorFailureStorageProvider,
    private val restartMigrationScheduleRepository: RestartMigrationScheduleRepository,
    private val pendingKeystoneMigrationPcztsRepository: PendingKeystoneMigrationPcztsRepository,
    private val migrationNotifier: MigrationNotifier,
    private val migrationLiveDriver: MigrationLiveDriver,
    private val migrationTransferStateRepository: MigrationTransferStateRepository,
) {
    suspend operator fun invoke() {
        val accountKeyId =
            accountDataSource
                .getSelectedAccount()
                .sdkAccount.accountUuid
                .toStorageKeyId()
        // clearMigration() (SDK, 2026-08-07) now delegates to zcash_client_sqlite's real
        // cancel_migration primitive (PR #2926) instead of the old manual status-only swap to
        // Failed — the account genuinely reads back NotStarted afterward, not RequiresAttention.
        getOrchardMigrationSdk().clearMigration()
        // Cancel the self-rechaining background worker chain for a run that no longer exists.
        // Cascades to the dead-man's-switch alarm (MigrationDueAlarmScheduler) and the worker
        // heartbeat too — see MigrationScheduler.cancel()'s own kdoc.
        migrationScheduler.cancel(accountKeyId)
        // Stop the in-process live driver loop if one is still sleeping mid-re-arm from before
        // this reset — otherwise it would wake on its own schedule, act on the just-cleared plan,
        // and republish a readout describing it. Run BEFORE the cache clear below on the theory
        // that it helps in the common case, NOT as a correctness guarantee: stop() is cooperative
        // cancellation, so a loop already past its last suspension check inside publishFreshReadout
        // (between its final SDK read and the synchronous repository.publish() call) can still land
        // that publish after both stop() and clear() return — there is no happens-before between
        // the two threads that this ordering establishes. It reliably closes the dominant case (the
        // loop asleep in delay()/delayWithPeriodicRefresh(), where cancellation bites immediately at
        // the suspension point), which is where a restarted account's driver actually sits most of
        // the time. A real guarantee against the narrow synchronous-publish race would need stop()
        // to suspend and cancelAndJoin() before clear() runs — not done here (Fable review,
        // 2026-08-07: flagged as a non-blocking follow-up, not required for this fix).
        migrationLiveDriver.stop(accountKeyId)
        // The home banner (and Progress, if open) would otherwise keep showing the deleted run's
        // last-published readout until something else happens to republish — see
        // MigrationTransferStateRepository.clear()'s own kdoc for why this alone is enough to
        // trigger an immediate fresh read for any active observer.
        migrationTransferStateRepository.clear(accountKeyId)
        // A leftover Tor-failure flag would keep routing launches into the Sending recovery screen.
        pendingMigrationTorFailureStorageProvider.store(accountKeyId, false)
        // An unconsumed restart schedule would otherwise be reused in place of a fresh proposal.
        restartMigrationScheduleRepository.consume(accountKeyId)
        // A pending Keystone sign/scan hand-off points at the deleted run.
        pendingKeystoneMigrationPcztsRepository.clear()
        // Dismiss any migration notification whose tap routes into the deleted run.
        migrationNotifier.cancel(accountKeyId)
    }
}
