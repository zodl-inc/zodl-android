package co.electriccoin.zcash.ui.screen.connectledger.openapp

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LedgerOpenAppVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LedgerOpenAppState> =
        MutableStateFlow(
            LedgerOpenAppState(
                onBackClick = ::onBack,
                onContinueClick = ::onContinue,
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()

    private fun onContinue() = navigationRouter.forward(LedgerHandshakeArgs)
}
