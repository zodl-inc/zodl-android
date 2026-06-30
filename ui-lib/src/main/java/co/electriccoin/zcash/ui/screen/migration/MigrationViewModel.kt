package co.electriccoin.zcash.ui.screen.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.NoteSplitProposal
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.repository.MigrationUiState
import co.electriccoin.zcash.ui.common.usecase.ConfirmMigrationScheduleUseCase
import co.electriccoin.zcash.ui.common.usecase.ConfirmNoteSplitUseCase
import co.electriccoin.zcash.ui.common.usecase.ExecuteNextMigrationTransferUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveMigrationStateUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshMigrationUseCase
import co.electriccoin.zcash.ui.common.usecase.RestartMigrationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MigrationScreenState(
    val migrationState: MigrationUiState = MigrationUiState.Loading,
    val torEnabled: Boolean = true, // ON by default per design doc §9.3
)

class MigrationViewModel(
    private val observeMigrationState: ObserveMigrationStateUseCase,
    private val refreshMigration: RefreshMigrationUseCase,
    private val confirmNoteSplit: ConfirmNoteSplitUseCase,
    private val confirmSchedule: ConfirmMigrationScheduleUseCase,
    private val executeNextTransfer: ExecuteNextMigrationTransferUseCase,
    private val restartMigration: RestartMigrationUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MigrationScreenState())
    val uiState: StateFlow<MigrationScreenState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeMigrationState().collect { state ->
                _uiState.update { current -> current.copy(migrationState = state) }
                // When we enter InProgress, auto-advance: attempt next transfer.
                if (state is MigrationUiState.InProgress) {
                    executeNextIfReady()
                }
            }
        }
    }

    fun onResume() {
        refreshMigration()
    }

    fun onTorToggled(enabled: Boolean) {
        _uiState.update { current -> current.copy(torEnabled = enabled) }
    }

    fun onNoteSplitConfirm(proposal: NoteSplitProposal) {
        viewModelScope.launch { confirmNoteSplit(proposal) }
    }

    fun onScheduleConfirm(schedule: MigrationSchedule) {
        viewModelScope.launch { confirmSchedule(schedule) }
    }

    fun onExecuteNextTransfer() {
        executeNextIfReady()
    }

    fun onRestart() {
        viewModelScope.launch { restartMigration() }
    }

    private fun executeNextIfReady() {
        viewModelScope.launch {
            runCatching {
                executeNextTransfer(_uiState.value.torEnabled)
            }.onFailure { e ->
                Twig.error(e) { "MigrationViewModel.executeNextTransfer failed" }
            }
        }
    }
}
