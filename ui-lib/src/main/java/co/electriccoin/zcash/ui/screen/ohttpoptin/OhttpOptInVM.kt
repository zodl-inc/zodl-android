package co.electriccoin.zcash.ui.screen.ohttpoptin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.OptOutOfOhttpUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OhttpOptInVM(
    private val persistServerSelection: PersistServerSelectionUseCase,
    private val optOutOfOhttp: OptOutOfOhttpUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<OhttpOptInState> =
        MutableStateFlow(
            OhttpOptInState(
                onTurnOn = ::onTurnOn,
                onNotNow = ::onNotNow,
                onBack = ::onBack,
            )
        )

    private fun onTurnOn() =
        viewModelScope.launch {
            persistServerSelection.persistPrivate()
            navigationRouter.back()
        }

    // The only opt-out: the home widget is gone for good. Closing the screen (X / back) keeps it.
    private fun onNotNow() =
        viewModelScope.launch {
            optOutOfOhttp()
            navigationRouter.back()
        }

    private fun onBack() = navigationRouter.back()
}
