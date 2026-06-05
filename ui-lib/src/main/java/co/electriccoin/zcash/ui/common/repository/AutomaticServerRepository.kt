package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

interface AutomaticServerRepository {
    val isServerAutomatic: Flow<Boolean>

    fun init()
}

class AutomaticServerRepositoryImpl(
    private val walletRepository: WalletRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val applicationStateProvider: ApplicationStateProvider,
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
    override val isServerAutomatic: Flow<Boolean> =
        isServerSelectionAutomaticProvider
            .observe()
            .distinctUntilChanged()
            .flatMapLatest { isAutomatic ->
                isAutomatic?.let { flowOf(it) }
                    ?: persistableWalletProvider.persistableWallet
                        .mapNotNull { it?.endpoint }
                        .map {
                            !lightWalletEndpointProvider.getEndpoints().contains(it)
                        }.map { isCustomEndpoint ->
                            !isCustomEndpoint
                        }
            }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun init() {
        isServerAutomatic
            .flatMapLatest { isAutomatic ->
                if (isAutomatic) {
                    walletRepository.fastestEndpoints
                        .filter { !it.isLoading }
                        .mapNotNull { it.servers?.firstOrNull() }
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
                if (isServerSelectionAutomaticProvider.get() != false && !isAppInTransactionState) {
                    walletRepository.refreshFastestServers()
                }
            }.launchIn(scope)
    }
}
