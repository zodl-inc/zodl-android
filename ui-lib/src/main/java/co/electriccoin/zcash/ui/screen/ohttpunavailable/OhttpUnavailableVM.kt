package co.electriccoin.zcash.ui.screen.ohttpunavailable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.SetTorEnabledUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OhttpUnavailableVM(
    isTorEnabled: IsTorEnabledUseCase,
    private val setTorEnabled: SetTorEnabledUseCase,
    private val persistServerSelection: PersistServerSelectionUseCase,
    private val navigationRouter: NavigationRouter,
    private val automaticServerRepository: AutomaticServerRepository,
) : ViewModel() {
    val state: StateFlow<OhttpUnavailableState?> =
        isTorEnabled
            .observe()
            .map { torEnabled ->
                OhttpUnavailableState(
                    isTorEnabled = torEnabled,
                    onTorToggle = ::onTorToggle,
                    onKeepWaiting = ::onBack,
                    onSwitchToPublic = ::onSwitchToPublic,
                    onBack = ::onBack,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    init {
        // "Switch to public servers" rebuilds the Synchronizer, which must not happen mid-transaction. The sheet
        // only opens outside a transaction, so one that starts while it is open closes it; the wallet keeps
        // retrying Private on its own and the switch stays available in Choose Server.
        viewModelScope.launch {
            automaticServerRepository.isTransactionInProgress.first { it }
            closeIfOnTop()
        }
    }

    /** The send flow can be pushed above the sheet, and popping then would cancel that flow instead. */
    private fun closeIfOnTop() =
        navigationRouter.custom { current ->
            if (current?.destination?.hasRoute(OhttpUnavailableArgs::class) == true) {
                NavigationCommand.Back
            } else {
                null
            }
        }

    /** The wallet keeps trying the private connection in the background; closing the sheet is all there is to do. */
    private fun onBack() = navigationRouter.back()

    private fun onTorToggle(enabled: Boolean) = viewModelScope.launch { setTorEnabled(enabled) }

    /** Marked as outage-induced, so the wallet returns to Private by itself once the relay answers. */
    private fun onSwitchToPublic() =
        viewModelScope.launch {
            persistServerSelection.persistPublic(afterPrivateUnavailable = true)
            navigationRouter.back()
        }
}
