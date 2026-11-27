package co.electriccoin.zcash.ui.screen.migration.howitworks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationPrivacyOrReviewDestinationUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MigrationHowItWorksVM(
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    private val getMigrationPrivacyOrReviewDestination: GetMigrationPrivacyOrReviewDestinationUseCase,
) : ViewModel() {
    private val lce = mutableLce<Unit>()

    val state: StateFlow<LceState<MigrationHowItWorksState>> =
        flowOf(
            MigrationHowItWorksState(
                onContinue = ::onContinue,
                onBack = ::onBack,
            )
        ).withLce(lce, errorStateMapper::mapToState)
            .stateIn(this)

    private var onContinueJob: Job? = null

    private fun onContinue() {
        if (onContinueJob?.isActive == true) return
        onContinueJob =
            viewModelScope.launch {
                navigationRouter.forward(getMigrationPrivacyOrReviewDestination(MigrationMode.AUTOMATIC))
            }
    }

    private fun onBack() = navigationRouter.back()
}
