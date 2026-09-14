package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider

class GetAutomaticEndpointUseCase(
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val isOhttpFallbackActiveProvider: IsOhttpFallbackActiveProvider,
    private val getSelectedEndpoint: GetSelectedEndpointUseCase,
) {
    suspend operator fun invoke(): LightWalletEndpoint =
        this(
            isFallbackActive = isOhttpFallbackActiveProvider.get(),
            persisted = getSelectedEndpoint()
        )

    // The endpoint Automatic mode stands for: the OHTTP gateway. The benchmarked "fastest server"
    // is no longer the automatic pick — clearnet servers are never preferred over OHTTP by speed.
    // The only time Automatic resolves to a clearnet server is while the OHTTP fallback is active
    // (the gateway was unreachable) and the persisted endpoint is one of the bundled fallbacks;
    // OhttpFallbackRepository moves the wallet back to the gateway once it is reachable again.
    operator fun invoke(isFallbackActive: Boolean, persisted: LightWalletEndpoint?): LightWalletEndpoint =
        persisted
            ?.takeIf { isFallbackActive && lightWalletEndpointProvider.getFallbackEndpoints().contains(it) }
            ?: lightWalletEndpointProvider.getDefaultEndpoint()
}
