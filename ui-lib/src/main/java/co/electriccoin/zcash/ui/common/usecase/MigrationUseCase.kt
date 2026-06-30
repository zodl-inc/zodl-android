package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.NoteSplitProposal
import co.electriccoin.zcash.ui.common.repository.MigrationRepository
import co.electriccoin.zcash.ui.common.repository.MigrationUiState
import kotlinx.coroutines.flow.StateFlow

class ObserveMigrationStateUseCase(
    private val migrationRepository: MigrationRepository
) {
    operator fun invoke(): StateFlow<MigrationUiState> = migrationRepository.migrationState
}

class RefreshMigrationUseCase(
    private val migrationRepository: MigrationRepository
) {
    operator fun invoke() = migrationRepository.refresh()
}

class ConfirmNoteSplitUseCase(
    private val migrationRepository: MigrationRepository
) {
    suspend operator fun invoke(proposal: NoteSplitProposal) =
        migrationRepository.confirmNoteSplit(proposal)
}

class ConfirmMigrationScheduleUseCase(
    private val migrationRepository: MigrationRepository
) {
    suspend operator fun invoke(schedule: MigrationSchedule) =
        migrationRepository.confirmSchedule(schedule)
}

class ExecuteNextMigrationTransferUseCase(
    private val migrationRepository: MigrationRepository
) {
    suspend operator fun invoke(useTor: Boolean) = migrationRepository.executeNextTransfer(useTor)
}

class RestartMigrationUseCase(
    private val migrationRepository: MigrationRepository
) {
    suspend operator fun invoke() = migrationRepository.restart()
}
