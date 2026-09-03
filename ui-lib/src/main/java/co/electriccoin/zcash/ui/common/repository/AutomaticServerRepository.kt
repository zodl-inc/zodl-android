package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.resolveIsServerSelectionAutomatic
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.rawWalletBalances
import co.electriccoin.zcash.ui.common.provider.retainWhileWalletExists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

interface AutomaticServerRepository {
    val isServerAutomatic: Flow<Boolean>

    suspend fun isServerAutomatic(): Boolean

    fun init()
}

class AutomaticServerRepositoryImpl(
    private val walletRepository: WalletRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val applicationStateProvider: ApplicationStateProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
) : AutomaticServerRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val isAppInTransactionState: Boolean
        get() =
            zashiProposalRepository.transactionProposal.value != null ||
                zashiProposalRepository.submitState.value != null ||
                keystoneProposalRepository.transactionProposal.value != null ||
                keystoneProposalRepository.submitState.value != null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val walletBalances =
        synchronizerProvider
            .rawWalletBalances()
            .retainWhileWalletExists(persistableWalletProvider)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val isServerAutomatic: Flow<Boolean> =
        isServerSelectionAutomaticProvider
            .observe()
            .distinctUntilChanged()
            .flatMapLatest { isAutomatic ->
                if (isAutomatic != null) {
                    flowOf(isAutomatic)
                } else {
                    persistableWalletProvider.persistableWallet
                        .mapNotNull { it?.endpoint }
                        .map { endpoint ->
                            resolveIsServerSelectionAutomatic(
                                isAutomaticPreference = null,
                                currentEndpoint = endpoint,
                                knownEndpoints = lightWalletEndpointProvider.getEndpoints()
                            )
                        }
                }
            }.distinctUntilChanged()

    override suspend fun isServerAutomatic(): Boolean {
        val preference = isServerSelectionAutomaticProvider.get()
        // Only the wallet read is guarded; resolving a custom endpoint is needed solely when the
        // preference was never written. getEndpoints() is an in-memory list, so it stays unguarded.
        val currentEndpoint =
            if (preference == null) persistableWalletProvider.getPersistableWallet()?.endpoint else null
        return resolveIsServerSelectionAutomatic(
            isAutomaticPreference = preference,
            currentEndpoint = currentEndpoint,
            knownEndpoints = lightWalletEndpointProvider.getEndpoints()
        )
    }

    /**
     * Subscribes to the automatic-server candidate pipeline and the app-foreground signal.
     *
     * The candidate flow is intentionally left un-deduplicated: [WalletRepository.updateWalletEndpoint]
     * already no-ops when the new endpoint matches the persisted one, so a redundant candidate here
     * is cheap, whereas deduplicating upstream would permanently suppress a candidate that was
     * skipped while [isAppInTransactionState] and never resurface it on a later emission. A
     * different endpoint rebuilds the Synchronizer; the candidate waits for the first local balance
     * snapshot, and UI consumers retain the previous state through the replacement.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun init() {
        isServerAutomatic
            .flatMapLatest { isAutomatic ->
                if (isAutomatic) {
                    combine(
                        walletRepository.fastestEndpoints,
                        walletBalances,
                        ::resolveAutomaticServerCandidate,
                    ).filterNotNull()
                } else {
                    emptyFlow()
                }
            }.onEach { fastestServer ->
                if (!isAppInTransactionState) {
                    walletRepository.updateWalletEndpoint(fastestServer)
                }
            }.launchIn(scope)

        applicationStateProvider
            .observeOnForeground()
            .onEach {
                if (isServerAutomatic() && !isAppInTransactionState) {
                    walletRepository.refreshFastestServers()
                }
            }.launchIn(scope)
    }
}

internal fun resolveAutomaticServerCandidate(
    fastestEndpoints: FastestServersState,
    localBalances: Map<*, *>?,
) =
    fastestEndpoints.servers
        ?.firstOrNull()
        ?.takeIf {
            !fastestEndpoints.isLoading &&
                localBalances != null
        }
