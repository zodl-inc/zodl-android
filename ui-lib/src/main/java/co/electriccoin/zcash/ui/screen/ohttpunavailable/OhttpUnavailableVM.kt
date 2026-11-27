package co.electriccoin.zcash.ui.screen.ohttpunavailable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.SetTorEnabledUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.Job
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
    private val observeFastestServers: ObserveFastestServersUseCase,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
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
            // Closing it for a send is a dismissal: only a later failing user action shows it again.
            automaticServerRepository.acknowledgeOhttpUnavailable()
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

    /**
     * The wallet keeps trying the private connection in the background; closing the sheet is all there is to
     * do. Closing it, by "Keep waiting" or by leaving it, acknowledges the outage: only a later user action that
     * fails for the same reason shows it again.
     */
    private fun onBack() {
        automaticServerRepository.acknowledgeOhttpUnavailable()
        navigationRouter.back()
    }

    /** The last Tor toggle; [onSwitchToPublic] waits for it, and toggles run one after the other. */
    private var torToggle: Job? = null

    /**
     * Stores the Tor choice. A "Switch to public servers" tapped right after waits until it is stored, so the
     * public synchronizer it builds is made with the Tor setting the user just chose, not the previous one.
     */
    private fun onTorToggle(enabled: Boolean) {
        val previous = torToggle
        torToggle =
            viewModelScope.launch {
                previous?.join()
                setTorEnabled(enabled)
            }
    }

    /**
     * Marked as outage-induced, so the wallet returns to Private by itself once the relay answers.
     * Uses the fastest public server only from a finished result that already exists, never a new
     * benchmark (ZIP #10 rule 3). The sheet shows in Private mode, where the fastest-servers
     * benchmark measures only the gateway, so this is normally null and the default public server
     * is used; the automatic switch ranks the public servers later, once Public is saved. The
     * transaction check runs again under the server-selection lock, since a send can start while
     * the sheet is open; a refused switch leaves the sheet open.
     */
    private fun onSwitchToPublic() =
        viewModelScope.launch {
            torToggle?.join()
            val inTransaction: suspend () -> Boolean = { automaticServerRepository.isTransactionInProgress.first() }
            if (inTransaction()) return@launch
            val fastest = fastestPublicServer(observeFastestServers().value)
            if (persistServerSelection.persistPublic(
                    afterPrivateUnavailable = true,
                    fastest = fastest,
                    abortIf = inTransaction
                )
            ) {
                navigationRouter.back()
            }
        }

    /**
     * Returns the fastest non-gateway server from the given [FastestServersState], or null if the
     * state is still loading or has no public servers.
     */
    private fun fastestPublicServer(state: FastestServersState): LightWalletEndpoint? =
        state.servers
            ?.takeIf { !state.isLoading }
            ?.firstOrNull { !lightWalletEndpointProvider.isOhttpGateway(it) }
}
