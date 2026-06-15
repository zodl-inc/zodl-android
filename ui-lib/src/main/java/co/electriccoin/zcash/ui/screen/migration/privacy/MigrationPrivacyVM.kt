package co.electriccoin.zcash.ui.screen.migration.privacy

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class MigrationPrivacyVM(
    private val args: MigrationPrivacyArgs,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {

    private val lce = mutableLce<Unit>()
    private val useTor = MutableStateFlow(false)

    val state: StateFlow<LceState<MigrationPrivacyState>> =
        combine(useTor, lce.state) { tor, _ ->
            MigrationPrivacyState(
                mode = args.mode,
                useTor = tor,
                onTorToggle = { useTor.value = it },
                onConfirm = ::onConfirm,
                onSkip = ::onConfirm,
                onBack = ::onBack,
            )
        }.withLce(lce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onConfirm() = navigationRouter.forward(MigrationReviewArgs(args.mode))

    private fun onBack() = navigationRouter.back()
}
