package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GetSelectedEndpointUseCase(
    private val persistableWalletProvider: PersistableWalletProvider
) {
    fun observe() =
        persistableWalletProvider.persistableWallet
            .map { it?.endpoint }
            .distinctUntilChanged()

    suspend operator fun invoke() = persistableWalletProvider.getPersistableWallet()?.endpoint
}
