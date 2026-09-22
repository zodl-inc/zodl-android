package co.electriccoin.zcash.ui.screen.connectledger.connect

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LedgerConnectVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LedgerConnectState> =
        MutableStateFlow(
            LedgerConnectState(
                onBackClick = ::onBack,
                onContinueClick = ::onContinue,
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()

    private fun onContinue() = navigationRouter.forward(LedgerDeviceScanArgs)
}
