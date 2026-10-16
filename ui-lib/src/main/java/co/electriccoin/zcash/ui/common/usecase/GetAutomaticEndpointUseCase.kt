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

    /**
     * Automatic mode means the OHTTP gateway; clearnet is never preferred by speed. Resolves to a
     * clearnet server only while the OHTTP fallback is active and [persisted] is a bundled fallback.
     */
    operator fun invoke(isFallbackActive: Boolean, persisted: LightWalletEndpoint?): LightWalletEndpoint =
        persisted
            ?.takeIf { isFallbackActive && lightWalletEndpointProvider.getFallbackEndpoints().contains(it) }
            ?: lightWalletEndpointProvider.getDefaultEndpoint()
}
