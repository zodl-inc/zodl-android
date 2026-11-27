package co.electriccoin.zcash.ui.screen.migration.restart

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.component.destructive
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationSnapshotUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.RestartMigrationUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import co.electriccoin.zcash.ui.design.R as DesignR

/**
 * The user-facing "Restart Migration" flow: shows the current migrated/remaining summary loaded
 * from the engine (mirroring [co.electriccoin.zcash.ui.screen.migration.invalid.MigrationTransferInvalidVM]
 * and [co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupVM]'s summary-loading shape),
 * then gates the actual [RestartMigrationUseCase] behind a destructive confirmation sheet since it
 * discards the in-flight plan.
 */
class MigrationRestartVM(
    private val restartMigration: RestartMigrationUseCase,
    private val getMigrationSnapshot: GetMigrationSnapshotUseCase,
    private val getOrchardBalance: GetOrchardBalanceUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private data class Summary(
        val completed: Int,
        val total: Int,
        val remaining: Zatoshi,
    )

    private val loadLce = mutableLce<Unit>()
    private val restartLce = mutableLce<Unit>()
    private val summaryFlow = MutableStateFlow<Summary?>(null)
    private val confirmationDialogFlow = MutableStateFlow<ZashiConfirmationState?>(null)

    init {
        loadLce.execute {
            val snapshot = getMigrationSnapshot()
            summaryFlow.value =
                Summary(
                    completed = snapshot?.completedCount ?: 0,
                    total = snapshot?.totalCount ?: 0,
                    remaining = getOrchardBalance(),
                )
        }
    }

    val state: StateFlow<LceState<MigrationRestartState>> =
        combine(summaryFlow, confirmationDialogFlow) { summary, dialog ->
            summary?.let { createState(it, dialog) }
        }.withLce(groupLce(loadLce, restartLce), errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(
        summary: Summary,
        dialog: ZashiConfirmationState?,
    ) = MigrationRestartState(
        onBack = navigationRouter::back,
        body = stringRes(DesignR.string.restartMigration_body),
        migratedLabel = stringRes(DesignR.string.restartMigration_summaryMigratedLabel),
        migratedValue =
            stringRes(DesignR.string.restartMigration_summaryMigratedValue, summary.completed, summary.total),
        remainingLabel = stringRes(DesignR.string.restartMigration_summaryRemainingLabel),
        remainingValue = stringRes(summary.remaining),
        warning = stringRes(DesignR.string.restartMigration_warning),
        support = stringRes(DesignR.string.restartMigration_support),
        nextButton =
            ButtonState(
                text = stringRes(DesignR.string.restartMigration_next),
                onClick = { onNextClicked(summary) },
            ),
        confirmationDialog = dialog,
    )

    private fun onNextClicked(summary: Summary) {
        confirmationDialogFlow.value =
            ZashiConfirmationState.destructive(
                title = stringRes(DesignR.string.restartMigration_confirmTitle),
                message =
                    stringRes(
                        DesignR.string.restartMigration_confirmMessage,
                        stringRes(summary.remaining),
                        summary.completed,
                    ),
                primaryText = stringRes(DesignR.string.restartMigration_confirmPrimary),
                onPrimary = ::onConfirmRestart,
                onBack = ::onDismissConfirmation,
            )
    }

    private fun onDismissConfirmation() {
        confirmationDialogFlow.value = null
    }

    private fun onConfirmRestart() {
        confirmationDialogFlow.value = null
        restartLce.execute {
            restartMigration()
            navigationRouter.back()
        }
    }
}
