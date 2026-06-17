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

    // Picks the endpoint for Automatic mode: prefer the freshly benchmarked fastest; otherwise keep the
    // persisted endpoint only while it is still bundled; otherwise fall back to the default. A persisted
    // custom (non-bundled) endpoint is intentionally dropped so Automatic never pins to a private server.
    operator fun invoke(fastest: LightWalletEndpoint?, persisted: LightWalletEndpoint?) =
        fastest
            ?: persisted?.takeIf { lightWalletEndpointProvider.getEndpoints().contains(it) }
            ?: lightWalletEndpointProvider.getDefaultEndpoint()
}
