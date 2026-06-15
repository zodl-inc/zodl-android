package co.electriccoin.zcash.ui.screen.migration.scheduled

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.MigrationPlanRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class MigrationScheduledVM(
    private val migrationPlanRepository: MigrationPlanRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {

    private val loadLce = mutableLce<Unit>()

    val state: StateFlow<LceState<MigrationScheduledState>> =
        migrationPlanRepository.observe().map { plan ->
            val total = plan?.transfers?.sumOf { it.amountZatoshi } ?: 0L
            val count = plan?.totalCount ?: 0
            MigrationScheduledState(
                totalAmount = stringRes(Zatoshi(total)),
                transfersProgress = stringRes("0 of $count"),
                duration = stringRes("~24 hours"),
                onDone = ::onDone,
            )
        }.withLce(loadLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onDone() = navigationRouter.backToRoot()
}
