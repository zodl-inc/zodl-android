package co.electriccoin.zcash.ui.screen.migration.notification

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyArgs
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class MigrationNotificationVM(
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {

    private val lce = mutableLce<Unit>()

    val state: StateFlow<LceState<MigrationNotificationState>> =
        flowOf(
            MigrationNotificationState(
                onAllow = ::onAllow,
                onSkip = ::onSkip,
                onAutoSkip = ::onAutoSkip,
                onBack = ::onBack,
            )
        ).withLce(lce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onAllow() = navigationRouter.forward(MigrationPrivacyArgs(MigrationMode.AUTOMATIC))

    private fun onSkip() = navigationRouter.forward(MigrationPrivacyArgs(MigrationMode.AUTOMATIC))

    // Used when this screen skips itself without ever being shown (permission already granted) —
    // replace instead of forward so it doesn't linger in the back stack and bounce the user
    // straight back here when they press back from a later screen.
    private fun onAutoSkip() = navigationRouter.replace(MigrationPrivacyArgs(MigrationMode.AUTOMATIC))

    private fun onBack() = navigationRouter.back()
}
