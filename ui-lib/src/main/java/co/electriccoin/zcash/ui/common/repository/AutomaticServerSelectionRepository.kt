package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

interface AutomaticServerSelectionRepository {
    fun init()
}

class AutomaticServerSelectionRepositoryImpl(
    private val applicationStateProvider: ApplicationStateProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val walletRepository: WalletRepository,
) : AutomaticServerSelectionRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun init() {
        isServerSelectionAutomaticProvider
            .observe()
            .map { isServerSelectionAutomaticProvider.get() != false }
            .distinctUntilChanged()
            .flatMapLatest { isAutomatic ->
                if (isAutomatic) {
                    walletRepository.fastestEndpoints
                        .filter { !it.isLoading }
                        .mapNotNull { it.servers?.firstOrNull() }
                } else {
                    emptyFlow()
                }
            }.onEach { fastestServer ->
                walletRepository.updateWalletEndpoint(fastestServer)
            }.launchIn(scope)

        applicationStateProvider
            .observeOnForeground()
            .onEach {
                if (isServerSelectionAutomaticProvider.get() != false) {
                    walletRepository.refreshFastestServers()
                }
            }.launchIn(scope)
    }
}
