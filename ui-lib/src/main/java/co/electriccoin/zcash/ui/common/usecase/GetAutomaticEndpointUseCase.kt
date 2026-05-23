package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository

class GetAutomaticEndpointUseCase(
    private val walletRepository: WalletRepository,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val getSelectedEndpoint: GetSelectedEndpointUseCase,
) {
    suspend operator fun invoke(): LightWalletEndpoint =
        this(
            fastest =
                walletRepository.fastestEndpoints.value.servers
                    ?.firstOrNull(),
            persisted = getSelectedEndpoint()
        )

    operator fun invoke(fastest: LightWalletEndpoint?, persisted: LightWalletEndpoint?) =
        fastest
            ?: persisted?.takeIf { lightWalletEndpointProvider.getEndpoints().contains(it) }
            ?: lightWalletEndpointProvider.getDefaultEndpoint()
}
