package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class GetServerSelectionUseCase(
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<ServerSelection> =
        combine(
            persistableWalletProvider.persistableWallet.map { it?.endpoint }.distinctUntilChanged(),
            isServerSelectionAutomaticProvider.observe()
        ) { endpoint, isAutomatic ->
            getServerSelection(isAutomatic, endpoint)
        }

    suspend operator fun invoke(): ServerSelection =
        getServerSelection(
            isServerSelectionAutomaticProvider.get(),
            persistableWalletProvider.getPersistableWallet()?.endpoint
        )

    @Suppress("UnusedPrivateProperty")
    private fun getServerSelection(
        isAutomatic: Boolean?,
        walletEndpoint: LightWalletEndpoint?
    ): ServerSelection =
        if (isAutomatic != false || walletEndpoint == null) {
            ServerSelection.Automatic
        } else {
            ServerSelection.Manual(endpoint = walletEndpoint)
        }
}
