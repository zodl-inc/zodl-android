package co.electriccoin.zcash.ui.screen.ohttpunavailable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.SetTorEnabledUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the OHTTP unavailable sheet.
 *
 * Watches for transaction starts and automatically closes the sheet when one begins, since switching
 * to public servers rebuilds the Synchronizer and must not happen mid-transaction. The wallet
 * continues retrying the private connection in the background, and the switch remains available in
 * the server selection screen.
 */
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
                    title = stringRes(R.string.ohttpUnavailableSheet_title),
                    body = stringRes(R.string.ohttpUnavailableSheet_body),
                    keepWaitingText = stringRes(R.string.ohttpUnavailableSheet_keepWaiting),
                    switchToPublicText = stringRes(R.string.ohttpUnavailableSheet_switchToPublic),
                    torTitle = stringRes(R.string.ohttpUnavailableSheet_torTitle),
                    torBody = stringRes(R.string.ohttpUnavailableSheet_torBody),
                    isTorEnabled = torEnabled,
                    onTorToggle = ::onTorToggle,
                    onKeepWaiting = ::onBack,
                    onSwitchToPublic = ::onSwitchToPublic,
                    onBack = ::onBack,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    init {
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

    /**
     * Marked as outage-induced, so the wallet returns to Private by itself once the relay answers. The
     * transaction check runs again under the server-selection lock, since a send can start while the
     * sheet is open; a refused switch leaves the sheet open.
     */
    private fun onSwitchToPublic() =
        viewModelScope.launch {
            val inTransaction = { automaticServerRepository.isTransactionInProgress.first() }
            if (inTransaction()) return@launch
            if (persistServerSelection.persistPublic(afterPrivateUnavailable = true, abortIf = inTransaction)) {
                navigationRouter.back()
            }
        }
}
