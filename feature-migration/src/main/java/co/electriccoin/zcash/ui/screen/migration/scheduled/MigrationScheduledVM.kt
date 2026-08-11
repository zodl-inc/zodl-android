package co.electriccoin.zcash.ui.screen.migration.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.TransferResult
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferFailureState
import co.electriccoin.zcash.ui.common.model.migration.formatMigrationDuration
import co.electriccoin.zcash.ui.common.model.migration.migrationFailureMessage
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.provider.IsBackgroundExecutionAvailableProvider
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.FinalizeMigrationScheduleUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationSnapshotUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import co.electriccoin.zcash.ui.design.R as DesignR

@Suppress("LongParameterList")
class MigrationScheduledVM(
    private val getMigrationSnapshot: GetMigrationSnapshotUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    private val isBackgroundExecutionAvailableProvider: IsBackgroundExecutionAvailableProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val pendingSchedule: PendingMigrationScheduleRepository,
    private val pendingKeystonePczts: PendingKeystoneMigrationPcztsRepository,
    private val finalizeMigrationSchedule: FinalizeMigrationScheduleUseCase,
    private val isMigrationTorEnabledStorageProvider: IsMigrationTorEnabledStorageProvider,
    private val synchronizerProvider: SynchronizerProvider,
) : ViewModel() {
    private val loadLce = mutableLce<LiveMigrationSnapshot?>()

    // True while a completed Keystone batch's accumulated signatures are still being applied,
    // stored, and finalized — the last leg of that flow (Tor submit, schedule storage) has no
    // other feedback surface once the QR scanning UI is gone, so this screen renders it instead.
    // Defaults true so the snapshot flow below never races the init{} check below it.
    val isFinalizing = MutableStateFlow(true)
    val failureSheet = MutableStateFlow<MigrationTransferFailureState?>(null)

    init {
        viewModelScope.launch { finalizeIfPendingKeystoneBatch() }
        // Fires exactly once — isFinalizing only ever transitions true -> false, never back.
        viewModelScope.launch {
            isFinalizing.first { !it }
            loadLce.execute { getMigrationSnapshot() }
        }
    }

    private suspend fun finalizeIfPendingKeystoneBatch() {
        try {
            val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
            val sched = pendingSchedule.get(accountKeyId)
            val pending = pendingKeystonePczts.get(accountKeyId)
            if (sched == null || pending == null) {
                // Hot-wallet (Zashi) path — FinalizeMigrationScheduleUseCase already ran before
                // navigating here, so the schedule is already committed. Nothing left to do.
                isFinalizing.value = false
                return
            }
            val sdk = getOrchardMigrationSdk()
            val splitSignedPczt = pending.accumulatedSplitSigned
            if (splitSignedPczt != null) {
                val useTor = isMigrationTorEnabledStorageProvider.get()
                val splitResult =
                    sdk.storeSignedNoteSplitPczt(
                        Pczt(splitSignedPczt),
                        NetworkPrivacyOptions(useTor = useTor),
                    )
                if (splitResult !is TransferResult.Success) {
                    failureSheet.update {
                        MigrationTransferFailureState(
                            message = migrationFailureMessage(splitResult),
                            onRetry = {
                                failureSheet.value = null
                                viewModelScope.launch { finalizeIfPendingKeystoneBatch() }
                            },
                            onDismiss = { failureSheet.value = null },
                        )
                    }
                    return
                }
                // Classify zip318_kind (PREPARATION) immediately — this broadcast's raw bytes are
                // already stored locally, so without this the normal enhancement queue would skip
                // it forever and the Activity row would stay "Sent" instead of "Note
                // split"/"Migrated". See MigrationDriveOnce.handleExecuted's identical call for
                // transfers.
                synchronizerProvider.getSynchronizerOrNull()?.enhanceTransaction(splitResult.txId)
            }
            // Kind-agnostic per-id signature application — extra PREPARATIONS of the note-split
            // tree go through the same call as the transfers.
            sdk.storeSignedSchedulePczts(
                (pending.accumulatedPrepSigned + pending.accumulatedTransferSigned)
                    .map { (id, bytes) -> id to Pczt(bytes) }
            )
            migrationLog(
                "MigrationScheduled: stored ${pending.accumulatedPrepSigned.size} signed prep + " +
                    "${pending.accumulatedTransferSigned.size} signed transfer PCZT(s) " +
                    "(split=${splitSignedPczt != null}) — finalizing the schedule"
            )
            // Mode doesn't affect this use case's behavior (only routes IMMEDIATE vs AUTOMATIC
            // before it), and MigrationScheduledArgs is only ever reached on the AUTOMATIC path.
            // startLiveDriverImmediately=false (MOB-1669): a large Keystone batch's whole
            // prove-ready note-split set would otherwise run through one blocking
            // finalizeReadyTransfers call in the live driver's very first iteration, right as
            // this Keystone ceremony finishes. The already-armed migrationScheduler job still
            // drives this plan forward — just not synchronously forced open here.
            finalizeMigrationSchedule(sched, MigrationMode.AUTOMATIC, startLiveDriverImmediately = false)
            pendingSchedule.clear()
            pendingKeystonePczts.clear()
            isFinalizing.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // An unguarded failure here (e.g. a transient "database is locked" from the migration
            // engine mutex) would otherwise crash the app right after the user completes a
            // physical Keystone signing ceremony, with the signed batch already applied. Surface
            // it as a retryable failure instead — isFinalizing deliberately stays true, so the
            // screen keeps showing its loading state underneath the sheet rather than racing the
            // snapshot flow against a schedule that was never finalized.
            migrationLog("MigrationScheduled: finalizeIfPendingKeystoneBatch failed: $e")
            failureSheet.update {
                MigrationTransferFailureState(
                    message = stringRes(DesignR.string.migrationScheduled_finalizeErrorMessage),
                    onRetry = {
                        failureSheet.value = null
                        viewModelScope.launch { finalizeIfPendingKeystoneBatch() }
                    },
                    onDismiss = { failureSheet.value = null },
                )
            }
        }
    }

    val state: StateFlow<LceState<MigrationScheduledState>> =
        loadLce.state
            .map { it.success }
            .map { snapshot ->
                // Transient null (still loading, or SDK not resolved yet) keeps the LCE loading
                // instead of rendering zeroed stats (review L3).
                if (snapshot == null) return@map null
                val total = snapshot.transfers.sumOf { it.amountZatoshi }
                val count = snapshot.totalCount
                val allScheduled =
                    (snapshot.transfers.map { it.scheduledAt } + snapshot.preparations.map { it.scheduledAt })
                val span =
                    (
                        (allScheduled.maxOrNull() ?: kotlin.time.Instant.DISTANT_PAST) -
                            (allScheduled.minOrNull() ?: kotlin.time.Instant.DISTANT_PAST)
                    ).inWholeSeconds
                val backgroundHint =
                    if (!isBackgroundExecutionAvailableProvider.isAvailable()) {
                        stringRes(DesignR.string.migrationScheduled_backgroundActivityHint)
                    } else {
                        null
                    }
                MigrationScheduledState(
                    totalAmount = stringRes(Zatoshi(total)),
                    transfersProgress = stringRes(DesignR.string.migrationScheduled_transfersProgressPending, count),
                    duration = stringRes(formatMigrationDuration(span)),
                    backgroundHint = backgroundHint,
                    onDone = ::onDone,
                )
            }.withLce(loadLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onDone() = navigationRouter.backToRoot()
}
