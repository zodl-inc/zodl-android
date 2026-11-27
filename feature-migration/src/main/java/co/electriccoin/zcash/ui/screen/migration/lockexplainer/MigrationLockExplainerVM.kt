package co.electriccoin.zcash.ui.screen.migration.lockexplainer

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// This bottom sheet is purely informational: it's opened from the "?" on the Migration Complete
// screen and only explains what locking does. The actual locking is performed directly by that
// screen's "Lock balance" button (MigrationCompleteVM.onLockBalance → LockOrchardBalanceUseCase),
// so both "Got it" and the sheet's back handle just dismiss it — this VM never locks anything.
class MigrationLockExplainerVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<MigrationLockExplainerState?> =
        MutableStateFlow(
            MigrationLockExplainerState(
                onGotIt = ::onBack,
                onBack = ::onBack,
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()
}
