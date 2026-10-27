package co.electriccoin.zcash.ui.screen.chooseserver.ohttpinfo

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Purely informational: both "Got it" and the sheet's back handle just dismiss it. */
class OhttpInfoVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<OhttpInfoState?> =
        MutableStateFlow(
            OhttpInfoState(
                onGotIt = ::onBack,
                onBack = ::onBack,
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()
}
