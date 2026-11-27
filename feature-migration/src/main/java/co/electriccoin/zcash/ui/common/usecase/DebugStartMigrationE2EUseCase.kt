package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferResult
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.ZashiSpendingKeyDataSource
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepository
import co.electriccoin.zcash.work.MigrationScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * DEBUG-ONLY end-to-end driver: resets any in-progress migration and immediately commits a fresh
 * AUTOMATIC plan — the exact `MigrationReviewVM.confirmAutomatic` path minus the UI and the
 * biometric gate — so an automated harness can start a full background migration from `adb`
 * without a human tapping through Review. Triggered from MainActivity by the
 * [EXTRA_START_MIGRATION] intent extra, which is honored only in debug builds.
 *
 * The reset half mirrors the Debug screen's "Migration restart" action (DebugVM), including all
 * the side-state that action clears; the commit half mirrors confirmAutomatic including the
 * split branch and the StalePlan re-propose retry.
 */
class DebugStartMigrationE2EUseCase(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val accountDataSource: AccountDataSource,
    private val pendingMigrationTorFailureStorageProvider: PendingMigrationTorFailureStorageProvider,
    private val restartMigrationScheduleRepository: RestartMigrationScheduleRepository,
    private val migrationNotifier: MigrationNotifier,
    private val finalizeMigrationSchedule: FinalizeMigrationScheduleUseCase,
    private val zashiSpendingKeyDataSource: ZashiSpendingKeyDataSource,
    private val context: Context,
) {
    suspend operator fun invoke() {
        migrationLog("E2E: start requested — waiting for the SDK")
        val sdk =
            waitForSdk() ?: run {
                migrationLog("E2E: SDK never became available — aborting")
                return
            }

        // ── Reset (mirror of DebugVM.onMigrationRestartClick) ──
        val accountKeyId =
            accountDataSource
                .getSelectedAccount()
                .sdkAccount.accountUuid
                .toStorageKeyId()
        sdk.clearMigration()
        MigrationScheduler(context).cancel(accountKeyId)
        pendingMigrationTorFailureStorageProvider.store(accountKeyId, false)
        restartMigrationScheduleRepository.consume(accountKeyId)
        migrationNotifier.cancel(accountKeyId)
        migrationLog("E2E: reset done — proposing a fresh AUTOMATIC plan")

        // ── Propose (retry: right after launch the wallet may still be syncing to spendability) ──
        var sched: MigrationSchedule? = null
        repeat(PROPOSE_ATTEMPTS) { attempt ->
            if (sched != null) return@repeat
            sched =
                runCatching { sdk.proposeMigrationTransfers() }
                    .onFailure {
                        migrationLog(
                            "E2E: propose attempt ${attempt + 1}/$PROPOSE_ATTEMPTS failed " +
                                "(${it.message}) — retrying in $PROPOSE_RETRY_DELAY"
                        )
                    }.getOrNull()
            if (sched == null) delay(PROPOSE_RETRY_DELAY)
        }
        val proposed =
            sched ?: run {
                migrationLog("E2E: propose never succeeded — aborting")
                return
            }

        // ── Commit (mirror of MigrationReviewVM.confirmAutomatic, biometrics skipped) ──
        val scheduleToSign =
            if (sdk.isNoteSplitNeeded()) {
                val proposal = sdk.prepareNoteSplit()
                val scheduleFromSplit = sdk.proposeMigrationTransfersFromSplit(proposal)
                val splitResult = sdk.submitNoteSplit(proposal, zashiSpendingKeyDataSource.getZashiSpendingKey())
                if (splitResult !is TransferResult.Success) {
                    migrationLog("E2E: note split failed ($splitResult) — aborting")
                    return
                }
                scheduleFromSplit
            } else {
                proposed
            }
        signAndFinalizeWithStaleRetry(sdk, scheduleToSign)
        migrationLog("E2E: plan committed — worker chain armed")
    }

    private suspend fun signAndFinalizeWithStaleRetry(sdk: OrchardMigrationSdk, schedule: MigrationSchedule) {
        // Same reasoning as MigrationReviewVM: StalePlan = planning-time note-index snapshot
        // drifted; BoundaryCheckpointMissing = the commit drew a boundary onto a grid height with
        // no retained checkpoint. Both are cured by a fresh propose+commit, never by retrying the
        // same schedule. The harness retries a bit harder than the interactive path (3 rounds).
        var toSign = schedule
        repeat(COMMIT_ATTEMPTS) { attempt ->
            try {
                sdk.signAndStoreMigrationSchedule(toSign, zashiSpendingKeyDataSource.getZashiSpendingKey())
                finalizeMigrationSchedule(toSign, MigrationMode.AUTOMATIC)
                return
            } catch (e: RuntimeException) {
                val retryable =
                    e.message?.contains("StalePlan") == true ||
                        e.message?.contains("BoundaryCheckpointMissing") == true
                if (!retryable || attempt == COMMIT_ATTEMPTS - 1) throw e
                migrationLog(
                    "E2E: commit attempt ${attempt + 1} failed retryably " +
                        "(${e.message?.take(ERROR_MESSAGE_PREVIEW_LENGTH)}) — re-proposing"
                )
                delay(COMMIT_RETRY_DELAY)
                toSign = sdk.proposeMigrationTransfers()
            }
        }
    }

    private suspend fun waitForSdk(): OrchardMigrationSdk? =
        withTimeoutOrNull(SDK_WAIT_DELAY * SDK_WAIT_ATTEMPTS) { getOrchardMigrationSdk() }

    companion object {
        /** Intent extra checked by MainActivity; honored only in debug builds. */
        const val EXTRA_START_MIGRATION = "co.electriccoin.zcash.debug.E2E_START_MIGRATION"

        private const val SDK_WAIT_ATTEMPTS = 36
        private val SDK_WAIT_DELAY = 5.seconds
        private const val PROPOSE_ATTEMPTS = 8
        private val PROPOSE_RETRY_DELAY = 15.seconds

        // Draws are geometric over a 16-bucket window; right after (re)install only the buckets
        // scanned since always-on retention activated have checkpoints, so several re-draw
        // rounds may be needed before all 9 transfers land on retained boundaries.
        private const val COMMIT_ATTEMPTS = 6
        private val COMMIT_RETRY_DELAY = 5.seconds
        private const val ERROR_MESSAGE_PREVIEW_LENGTH = 120
    }
}
