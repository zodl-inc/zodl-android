package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.resolveIsServerSelectionAutomatic
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

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
    private val persistableWalletProvider: PersistableWalletProvider,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val synchronizerProvider: SynchronizerProvider,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun init() {
        applicationStateProvider
            .observeOnForeground()
            .mapLatest { evaluateServerSwitch() }
            .filterNotNull()
            .onEach { applyServerSwitch(it) }
            .launchIn(scope)
    }

    @Suppress("ReturnCount")
    internal suspend fun evaluateServerSwitch(): LightWalletEndpoint? {
        if (!isServerAutomatic() || isAppInTransactionState) return null
        val current = persistableWalletProvider.getPersistableWallet()?.endpoint ?: return null
        val candidates = lightWalletEndpointProvider.getEndpoints()
        if (candidates.isEmpty()) return null
        val synchronizer = synchronizerProvider.getSynchronizerOrNull() ?: return null
        return synchronizer.evaluateServerSwitch(
            current = current,
            candidates = candidates,
            fetchThreshold = 5.seconds,
            blocksToFetch = 1
        )
    }

    private suspend fun applyServerSwitch(candidate: LightWalletEndpoint) {
        if (!isServerAutomatic() || isAppInTransactionState) return
        if (candidate !in lightWalletEndpointProvider.getEndpoints()) return
        Twig.info { "Automatic server: switching to ${candidate.host}:${candidate.port}" }
        walletRepository.updateWalletEndpoint(candidate)
    }
}
