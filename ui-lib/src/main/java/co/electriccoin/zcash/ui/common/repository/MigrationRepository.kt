package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationProgress
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.NoteSplitProposal
import cash.z.ecc.android.sdk.TransferResult
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MigrationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

// UI-layer migration states — wraps SDK sealed types for Compose consumption.
sealed interface MigrationUiState {
    data object Loading : MigrationUiState

    data object NotStarted : MigrationUiState

    data class AwaitingNoteSplitConfirm(
        val proposal: NoteSplitProposal
    ) : MigrationUiState

    data object AwaitingSplitConfirmation : MigrationUiState

    data class ProposalReview(
        val schedule: MigrationSchedule
    ) : MigrationUiState

    data class InProgress(
        val progress: MigrationProgress
    ) : MigrationUiState

    data class RequiresAttention(
        val reason: AttentionReason
    ) : MigrationUiState

    data object Complete : MigrationUiState

    data class Error(
        val message: String
    ) : MigrationUiState
}

interface MigrationRepository {
    val migrationState: StateFlow<MigrationUiState>

    fun refresh()

    suspend fun confirmNoteSplit(proposal: NoteSplitProposal)

    suspend fun confirmSchedule(schedule: MigrationSchedule)

    suspend fun executeNextTransfer(useTor: Boolean)

    suspend fun restart()
}

private object DenominationPlanDefaults {
    const val PREP_FEE_ZATOSHI = 10_000L
    const val MIGRATION_FEE_ZATOSHI = 10_000L
    const val MINIMUM_OUTPUT_ZATOSHI = 50_000L
}

class MigrationRepositoryImpl(
    private val migrationDataSource: MigrationDataSource,
    private val accountDataSource: AccountDataSource,
) : MigrationRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Non-reentrant: executeNextTransfer must never overlap confirmSchedule or itself.
    private val executionMutex = Mutex()

    private val _migrationState = MutableStateFlow<MigrationUiState>(MigrationUiState.Loading)
    override val migrationState: StateFlow<MigrationUiState> = _migrationState.asStateFlow()

    // Real SDK call (cash.z.ecc.android.sdk.Synchronizer.planOrchardDenominationSplit, PR #2008).
    // Everything beyond this — proposal building/broadcast/status derivation — is still blocked
    // on the unstable nu6.3 Ironwood crate surface; see ironwood-migration-execution.md.
    override fun refresh() {
        scope.launch {
            runCatching {
                val orchardAvailable =
                    accountDataSource
                        .getSelectedAccount()
                        .unified.balance.available.value

                if (orchardAvailable <= 0L) {
                    _migrationState.value = MigrationUiState.NotStarted
                    return@runCatching
                }

                val plan =
                    migrationDataSource.planOrchardDenominationSplit(
                        totalInputZatoshi = orchardAvailable,
                        prepFeeZatoshi = DenominationPlanDefaults.PREP_FEE_ZATOSHI,
                        migrationFeeZatoshi = DenominationPlanDefaults.MIGRATION_FEE_ZATOSHI,
                        minimumOutputZatoshi = DenominationPlanDefaults.MINIMUM_OUTPUT_ZATOSHI
                    )

                _migrationState.value =
                    if (plan.migrationOutputs.isEmpty()) {
                        MigrationUiState.NotStarted
                    } else {
                        MigrationUiState.AwaitingNoteSplitConfirm(
                            NoteSplitProposal(
                                outputNotes = plan.migrationOutputs,
                                fee = plan.prepFeeZatoshi
                            )
                        )
                    }
            }.onFailure { e ->
                Twig.error(e) { "MigrationRepository.refresh failed" }
                _migrationState.value = MigrationUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    override suspend fun confirmNoteSplit(proposal: NoteSplitProposal): Unit =
        withContext(Dispatchers.IO) {
            runCatching {
                _migrationState.value = MigrationUiState.AwaitingSplitConfirmation
                when (val result = migrationDataSource.submitNoteSplit(proposal)) {
                    is TransferResult.Success -> {
                        refresh()
                    }

                    is TransferResult.NetworkError -> {
                        _migrationState.value = MigrationUiState.Error("Network error — try again")
                    }

                    is TransferResult.InvalidNote -> {
                        _migrationState.value = MigrationUiState.Error("Note split failed: invalid note")
                    }

                    is TransferResult.Expired -> {
                        _migrationState.value = MigrationUiState.Error("Note split expired — please retry")
                    }
                }
            }.onFailure { e ->
                Twig.error(e) { "MigrationRepository.confirmNoteSplit failed" }
                _migrationState.value = MigrationUiState.Error(e.message ?: "Unknown error")
            }
        }

    override suspend fun confirmSchedule(schedule: MigrationSchedule) =
        withContext(Dispatchers.IO) {
            if (executionMutex.tryLock()) {
                try {
                    runCatching {
                        // SDK signs internally — no USK handling needed here.
                        migrationDataSource.signAndStoreMigrationSchedule(schedule)
                        // State will move to InProgress; refresh to pick it up.
                        refresh()
                    }.onFailure { e ->
                        Twig.error(e) { "MigrationRepository.confirmSchedule failed" }
                        _migrationState.value = MigrationUiState.Error(e.message ?: "Unknown error")
                    }
                } finally {
                    executionMutex.unlock()
                }
            }
        }

    override suspend fun executeNextTransfer(useTor: Boolean) =
        withContext(Dispatchers.IO) {
            if (executionMutex.tryLock()) {
                try {
                    runCatching {
                        // Sync gate: SDK may need a sync pass before spending change from the
                        // previous transfer. Exit and let the sync cycle handle it.
                        if (migrationDataSource.isSyncRequiredBeforeNextTransfer()) {
                            _migrationState.value =
                                MigrationUiState.RequiresAttention(
                                    AttentionReason.SyncRequiredBeforeNext
                                )
                            return@runCatching
                        }
                        val options = NetworkPrivacyOptions(useTor = useTor)
                        when (val result = migrationDataSource.executeNextPendingTransfer(options)) {
                            is TransferResult.Success -> {
                                refresh()
                            }

                            is TransferResult.NetworkError -> {
                                Unit
                            }

                            // transient; leave state, retry next open
                            is TransferResult.InvalidNote -> {
                                // TODO [MOB-IRONWOOD]: TransferResult.InvalidNote carries no transferId,
                                // but AttentionReason.InvalidTransfer requires one. Flag to SDK team —
                                // either InvalidNote needs a transferId field, or the app must track
                                // "current transfer id" itself before calling executeNextPendingTransfer.
                                _migrationState.value =
                                    MigrationUiState.RequiresAttention(
                                        AttentionReason.InvalidTransfer(transferId = "unknown")
                                    )
                            }

                            is TransferResult.Expired -> {
                                _migrationState.value =
                                    MigrationUiState.RequiresAttention(
                                        AttentionReason.TransferExpired
                                    )
                            }

                            null -> {
                                Unit
                            } // no transfer due at current height
                        }
                    }.onFailure { e ->
                        Twig.error(e) { "MigrationRepository.executeNextTransfer failed" }
                    }
                } finally {
                    executionMutex.unlock()
                }
            }
        }

    override suspend fun restart(): Unit =
        withContext(Dispatchers.IO) {
            runCatching {
                val newSchedule = migrationDataSource.restartCurrentMigrationStep()
                _migrationState.value = MigrationUiState.ProposalReview(newSchedule)
            }.onFailure { e ->
                Twig.error(e) { "MigrationRepository.restart failed" }
                _migrationState.value = MigrationUiState.Error(e.message ?: "Unknown error")
            }
        }
}
