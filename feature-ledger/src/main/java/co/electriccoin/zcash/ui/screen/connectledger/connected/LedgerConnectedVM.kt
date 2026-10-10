package co.electriccoin.zcash.ui.screen.connectledger.connected

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The end of enrollment: Close and system back both unwind to the wallet root.
 */
class LedgerConnectedVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LedgerConnectedState> =
        MutableStateFlow(
            LedgerConnectedState(onBack = ::onBack)
        ).asStateFlow()

    private fun onBack() = navigationRouter.backToRoot()
}
