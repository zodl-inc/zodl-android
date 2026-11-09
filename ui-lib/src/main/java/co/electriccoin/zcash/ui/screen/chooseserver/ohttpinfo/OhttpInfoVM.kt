package co.electriccoin.zcash.ui.screen.chooseserver.ohttpinfo

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.stringRes
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
                title = stringRes(R.string.ohttpInfo_title),
                body = stringRes(R.string.ohttpInfo_body),
                gotItText = stringRes(R.string.ohttpInfo_gotIt),
                onGotIt = ::onBack,
                onBack = ::onBack,
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()
}
