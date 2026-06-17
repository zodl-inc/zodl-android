package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Automatic-mode endpoint selection (MOB-1144): prefer the freshly benchmarked fastest server; else
 * keep the persisted endpoint only while it is still a bundled one; else fall back to the default.
 * A persisted custom server must never be reused in Automatic mode.
 */
class GetAutomaticEndpointUseCaseTest {
    private val default = endpoint("zec.rocks")
    private val known = listOf(default, endpoint("na.zec.rocks"))

    private val useCase =
        GetAutomaticEndpointUseCase(
            walletRepository = mockk<WalletRepository>(relaxed = true),
            lightWalletEndpointProvider =
                mockk<LightWalletEndpointProvider> {
                    every { getEndpoints() } returns known
                    every { getDefaultEndpoint() } returns default
                },
            getSelectedEndpoint = mockk<GetSelectedEndpointUseCase>(relaxed = true)
        )

    @Test
    fun prefersFastestWhenAvailable() {
        assertEquals(
            endpoint("eu.zec.rocks"),
            useCase(fastest = endpoint("eu.zec.rocks"), persisted = endpoint("na.zec.rocks"))
        )
    }

    @Test
    fun keepsPersistedWhenItIsABundledServer() {
        assertEquals(endpoint("na.zec.rocks"), useCase(fastest = null, persisted = endpoint("na.zec.rocks")))
    }

    @Test
    fun dropsPersistedCustomServerToDefault() {
        assertEquals(default, useCase(fastest = null, persisted = endpoint("custom.example.com")))
    }

    @Test
    fun fallsBackToDefaultWhenNothingPersisted() {
        assertEquals(default, useCase(fastest = null, persisted = null))
    }

    private fun endpoint(host: String) =
        LightWalletEndpoint(
            host = host,
            port = 443,
            isSecure = true
        )
}
