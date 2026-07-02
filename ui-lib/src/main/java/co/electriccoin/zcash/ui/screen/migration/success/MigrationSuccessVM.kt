package co.electriccoin.zcash.ui.screen.migration.success

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewTransactionDetailAfterSuccessfulProposalUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class MigrationSuccessVM(
    private val args: MigrationSuccessArgs,
    private val navigationRouter: NavigationRouter,
    private val viewTransactionDetail: ViewTransactionDetailAfterSuccessfulProposalUseCase,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {

    private val closeLce = mutableLce<Unit>()

    val state: StateFlow<LceState<MigrationSuccessState>> =
        flowOf(
            MigrationSuccessState(
                onViewTransaction = args.txId?.let { txId -> { viewTransactionDetail(txId) } },
                onClose = ::onClose,
            )
        ).withLce(closeLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onClose() = navigationRouter.backToRoot()
}
