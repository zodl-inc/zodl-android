package co.electriccoin.zcash.ui.screen.connectledger.turnon

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LedgerTurnOnVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LedgerTurnOnState> =
        MutableStateFlow(
            LedgerTurnOnState(
                onBackClick = ::onBack,
                onContinueClick = ::onContinue,
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()

    private fun onContinue() = navigationRouter.forward(LedgerDeviceScanArgs)
}
