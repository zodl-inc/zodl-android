package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Automatic-mode endpoint: the OHTTP gateway is the default, always. The benchmarked fastest
 * clearnet server is never preferred over it. The only clearnet result is a bundled fallback that
 * was manually selected while the gateway was unreachable (no automatic fallback since 2026-09-22).
 */
class GetAutomaticEndpointUseCaseTest {
    private val gateway = endpoint("ohttp-gateway.example")
    private val fallbacks = listOf(endpoint("zec.rocks"), endpoint("na.zec.rocks"))

    private val useCase =
        GetAutomaticEndpointUseCase(
            lightWalletEndpointProvider =
                mockk<LightWalletEndpointProvider> {
                    every { getEndpoints() } returns listOf(gateway) + fallbacks
                    every { getDefaultEndpoint() } returns gateway
                    every { getFallbackEndpoints() } returns fallbacks
                },
            isOhttpFallbackActiveProvider = mockk<IsOhttpFallbackActiveProvider>(relaxed = true),
            getSelectedEndpoint = mockk<GetSelectedEndpointUseCase>(relaxed = true)
        )

    @Test
    fun defaultIsTheOhttpGatewayWhenNothingPersisted() {
        assertEquals(gateway, useCase(isFallbackActive = false, persisted = null))
    }

    @Test
    fun ohttpGatewayWinsOverAPersistedClearnetServerWhenNoFallbackIsActive() {
        // A clearnet server left over from an earlier session is not the automatic pick while
        // the gateway is available: OHTTP is the default.
        assertEquals(gateway, useCase(isFallbackActive = false, persisted = endpoint("na.zec.rocks")))
    }

    @Test
    fun keepsTheActiveBundledFallbackWhileTheGatewayIsUnreachable() {
        assertEquals(endpoint("na.zec.rocks"), useCase(isFallbackActive = true, persisted = endpoint("na.zec.rocks")))
    }

    @Test
    fun neverResolvesToACustomServerEvenWhenFallbackIsActive() {
        assertEquals(gateway, useCase(isFallbackActive = true, persisted = endpoint("custom.example.com")))
    }

    @Test
    fun fallbackFlagWithoutAClearnetEndpointStillYieldsTheGateway() {
        assertEquals(gateway, useCase(isFallbackActive = true, persisted = gateway))
    }

    private fun endpoint(host: String) =
        LightWalletEndpoint(
            host = host,
            port = 443,
            isSecure = true
        )
}
