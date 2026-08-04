package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.MigrationSchedule
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.model.migration.MigrationKeystoneRound
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.migration.estimatedSecondsBetweenHeights
import co.electriccoin.zcash.ui.common.model.migration.toMigrationPlan
import co.electriccoin.zcash.ui.common.repository.MigrationPlanRepository
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledArgs
import co.electriccoin.zcash.work.MigrationScheduler
import co.electriccoin.zcash.work.MigrationSyncScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Persists a signed [MigrationSchedule] and schedules the background worker for its first
 * transfer, then navigates to [MigrationScheduledArgs]. Shared by both the hot-wallet confirm path
 * (MigrationReviewVM) and the post-Keystone-scan path (MigrationKeystoneScanVM) so the scheduling
 * logic isn't duplicated.
 *
 * Background delivery is scheduled unconditionally, regardless of whether the user granted the
 * Battery-optimization-exemption permission — declining it only makes background execution less
 * reliable (may be deferred by Doze), it does not disable it. Whatever the OS/system still prevents
 * is caught by [MigrationWorker][co.electriccoin.zcash.work.MigrationWorker]'s own retry-on-not-ready
 * behavior and by on-launch reconciliation
 * ([CheckMigrationRecoveryUseCase][co.electriccoin.zcash.ui.common.usecase.CheckMigrationRecoveryUseCase]),
 * not by a separate notify-only delivery mode.
 */
class FinalizeMigrationScheduleUseCase(
    private val migrationPlanRepository: MigrationPlanRepository,
    private val migrationScheduler: MigrationScheduler,
    private val migrationSyncScheduler: MigrationSyncScheduler,
    private val navigationRouter: NavigationRouter,
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val synchronizerProvider: SynchronizerProvider,
) {
    suspend operator fun invoke(sched: MigrationSchedule, mode: MigrationMode) {
        // Measured block rate — the 75s constant grossly overestimates on the bursty testnet,
        // scheduling Lane B far past the real due heights.
        val secondsPerBlock = getOrchardMigrationSdk()?.estimatedSecondsPerBlock() ?: 75L
        persistPlan(sched, mode, secondsPerBlock)
        val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
        logCommittedBoundaries()
        val tipHeight = sched.transfers.minOfOrNull { it.anchorHeight }
            ?: (sched.preparations.minOfOrNull { it.broadcastHeight } ?: 0L)
        migrationScheduler.schedule(accountKeyId, delayUntilFirstStep(sched, secondsPerBlock, tipHeight))
        // Lane A first arm is a short flat delay: the schedule object carries no anchor
        // boundaries, so the worker's FIRST run reads the freshly committed engine states and
        // computes the precise boundary-driven wake itself (see MigrationSyncWorker).
        migrationSyncScheduler.schedule(accountKeyId, 60.seconds)
        // The engine's anchor-retention floor is session-scoped and was computed BEFORE this plan
        // existed — restart the sync session now, before the chain crosses the plan's first
        // boundary, or that boundary's checkpoint is never created and its transfer can never be
        // proved (see Synchronizer.restartSyncSession; observed live as a permanent
        // AnchorNotFound on the plan's first bucket).
        val restarted = synchronizerProvider.getSynchronizerOrNull()?.restartSyncSession() ?: false
        Twig.debug {
            "MIGRATION_DIAG FinalizeMigrationSchedule: sync-session restart for anchor retention — restarted=$restarted"
        }
        navigationRouter.forward(MigrationScheduledArgs)
    }

    /**
     * Write-ahead persistence of the app-side plan, called BEFORE the irreversible SDK commit
     * (`submitNoteSplit`/`signAndStoreMigrationSchedule`) in MigrationReviewVM — not just at the end
     * via [invoke].
     *
     * `OrchardMigrationSdk.getMigrationState()` is the source of truth for "has this migration
     * committed", but the app-side plan is what the home banner and progress screen actually read.
     * Persisting the plan before the commit means a crash in the window between that commit and
     * [invoke]'s worker-schedule/navigation leaves a *recoverable* state — `InProgress` + a saved
     * plan, which re-entry resumes to the progress screen — rather than a plan-less `InProgress` the
     * app mistakes for a fresh start and tries to re-commit (which re-finalizes the already-broadcast
     * split and fails). A commit that never actually happens (SDK still `NotStarted`) leaves a stale
     * plan, reconciled away by
     * [CheckMigrationRecoveryUseCase][co.electriccoin.zcash.ui.common.usecase.CheckMigrationRecoveryUseCase].
     */
    suspend fun persistPlan(sched: MigrationSchedule, mode: MigrationMode, secondsPerBlock: Long = 75L) {
        // Stateless preview, computed fresh here rather than threaded through from Review — see
        // MigrationKeystoneRound's kdoc. Never persisted as a running campaign counter: "current" is
        // always 1 ("this round, from here"), "total" is whatever the estimate says right now.
        val account = getSelectedWalletAccount()
        val keystoneRound = if (account is KeystoneAccount) {
            getOrchardMigrationSdk()?.estimateMigrationRunCount()?.takeIf { it > 1 }?.let { MigrationKeystoneRound(current = 1, total = it) }
        } else {
            null
        }
        migrationPlanRepository.save(sched.toMigrationPlan(mode, keystoneRound, secondsPerBlock))
    }

    /**
     * Post-commit discoverability log (Issue 1): re-surface the engine's REAL per-transfer anchor
     * boundaries from the Kotlin side, right after the commit, so `grep MIGRATION_DIAG` shows the
     * true committed anchors without having to read the Rust `committedPlan:` dump.
     *
     * The propose-time app log (MigrationReviewVM.logProposedPlan) deliberately carries no
     * `boundary=` — anchor boundaries are drawn only at COMMIT (commit_preparation), so a proposal
     * has none. This reads them back from [OrchardMigrationSdk.getMigrationTransferStates], which
     * surfaces `anchorBoundaryHeight` per transfer (null for preparations — they anchor to the
     * natural tip, not a drawn grid boundary). Correlates by the stable transfer id (never array
     * index — ZIP 318 shuffles the two orderings apart). Best-effort: a failed/absent read logs a
     * single warning and is otherwise silent, never blocking the commit's scheduling/navigation.
     */
    private suspend fun logCommittedBoundaries() {
        val states = runCatching { getOrchardMigrationSdk()?.getMigrationTransferStates() }.getOrNull()
        if (states == null) {
            Twig.debug { "MIGRATION_DIAG committedPlan(app): no live transfer states available post-commit" }
            return
        }
        Twig.debug {
            buildString {
                appendLine(
                    "MIGRATION_DIAG committedPlan(app): ${states.transfers.size} transaction(s), scannedTip=${states.tipHeight}"
                )
                states.transfers
                    .sortedBy { it.scheduledHeight }
                    .forEach { t ->
                        appendLine(
                            "MIGRATION_DIAG committedPlan(app): id=${t.id} " +
                                "kind=${if (t.isTransfer) "Transfer" else "Preparation"} " +
                                "scheduled=${t.scheduledHeight} " +
                                "boundary=${t.anchorBoundaryHeight ?: "natural"} " +
                                "proved=${t.isProved} sent=${t.isSent}"
                        )
                    }
            }.trimEnd()
        }
    }

    // The first step (preparation or transfer) is never "ready now" (same anchor/proposal round
    // trip as any other step, per proposeMigrationTransfers()) — the very first WorkManager job
    // must wait for it just like every job scheduled after it, not fire immediately.
    //
    // broadcastHeight/nextExecutableAfterHeight/anchorHeight/expiryHeight are block heights, not
    // timestamps — see estimatedSecondsBetweenHeights for why they must never be used directly as
    // (or against) epoch seconds (this previously made every transfer look ~56 years overdue on a
    // live device).
    //
    // [tipHeight] is the commit-tip baseline (= the min anchorHeight across transfers, which the
    // SDK always draws from the same committed chain tip). Preparations and transfers share this
    // one origin, so the delay to a preparation's broadcastHeight and the delay to a transfer's
    // nextExecutableAfterHeight are comparable block-count deltas from the same point.
    internal fun delayUntilFirstStep(sched: MigrationSchedule, secondsPerBlock: Long, tipHeight: Long): Duration {
        val earliest = (sched.preparations.map { it.broadcastHeight } +
            sched.transfers.map { it.nextExecutableAfterHeight }).minOrNull()
            ?: return 0.seconds
        val remaining = estimatedSecondsBetweenHeights(tipHeight, earliest, secondsPerBlock)
        return if (remaining <= 0) 0.seconds else remaining.seconds
    }
}
