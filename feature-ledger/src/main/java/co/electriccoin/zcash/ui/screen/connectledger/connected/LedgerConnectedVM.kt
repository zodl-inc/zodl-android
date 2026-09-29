package co.electriccoin.zcash.ui.screen.connectledger.connected

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The end of enrollment: the only way on is Close, which unwinds to the wallet root. Back is
 * consumed by the screen, so there is nothing else to handle here.
 */
class LedgerConnectedVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LedgerConnectedState> =
        MutableStateFlow(
            LedgerConnectedState(onClose = ::onClose)
        ).asStateFlow()

    private fun onClose() = navigationRouter.backToRoot()
}
