package co.electriccoin.zcash.ui.common.provider

import android.app.Application
import co.electriccoin.lightwallet.client.LightWalletClient
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The gateway hosts come from the SDK ([LightWalletClient.ohttpGatewayHost]), stubbed here with example hosts so
 * the result does not depend on the `ZODL_OHTTP_*` configuration the SDK under test was built with.
 */
class LightWalletEndpointProviderTest {
    @AfterTest
    fun tearDown() = unmockkObject(LightWalletClient.Companion)

    @Test
    fun theGatewayOfTheSdkIsTheDefaultAndFirstEndpoint() {
        sdkGateways(mainnet = MAINNET_GATEWAY, testnet = TESTNET_GATEWAY)

        listOf(false to MAINNET_GATEWAY, true to TESTNET_GATEWAY).forEach { (isTestnet, host) ->
            val provider = provider(isTestnet)
            val gateway = LightWalletEndpoint(host = host, port = 443, isSecure = true)

            assertEquals(gateway, provider.getOhttpEndpoint())
            assertEquals(gateway, provider.getDefaultEndpoint())
            assertTrue(provider.hasOhttpGateway())
            assertEquals(listOf(gateway) + provider.getFallbackEndpoints(), provider.getEndpoints())
            assertTrue(provider.isOhttpGateway(gateway))
        }
        assertEquals(listOf(MAINNET_GATEWAY, TESTNET_GATEWAY), LightWalletEndpointProvider.gatewayHosts)
        assertTrue(LightWalletEndpointProvider.isTestnetGatewayHost(TESTNET_GATEWAY))
        assertFalse(LightWalletEndpointProvider.isTestnetGatewayHost(MAINNET_GATEWAY))
    }

    @Test
    fun onlyTheExactGatewayHostsArePrivate() {
        sdkGateways(mainnet = MAINNET_GATEWAY, testnet = TESTNET_GATEWAY)

        assertTrue(LightWalletEndpointProvider.isOhttpGatewayHost(MAINNET_GATEWAY))
        assertTrue(LightWalletEndpointProvider.isOhttpGatewayHost(TESTNET_GATEWAY))

        assertFalse(LightWalletEndpointProvider.isOhttpGatewayHost("gateway-eu.example"))
        assertFalse(LightWalletEndpointProvider.isOhttpGatewayHost("prefix.$MAINNET_GATEWAY"))
        assertFalse(LightWalletEndpointProvider.isOhttpGatewayHost("$MAINNET_GATEWAY.example.org"))
        assertFalse(LightWalletEndpointProvider.isOhttpGatewayHost("zec.rocks"))
    }

    /** An SDK built without OHTTP: no gateway is listed, and the default is the first clearnet server. */
    @Test
    fun withoutAnSdkGatewayOnlyClearnetServersAreListed() {
        sdkGateways(mainnet = null, testnet = null)

        listOf(false, true).forEach { isTestnet ->
            val provider = provider(isTestnet)

            assertNull(provider.getOhttpEndpoint())
            assertFalse(provider.hasOhttpGateway())
            assertEquals(provider.getFallbackEndpoints(), provider.getEndpoints())
            assertEquals(provider.getFallbackEndpoints().first(), provider.getDefaultEndpoint())
            assertTrue(provider.getEndpoints().none(provider::isOhttpGateway))
        }
        assertEquals(emptyList(), LightWalletEndpointProvider.gatewayHosts)
    }

    private fun sdkGateways(
        mainnet: String?,
        testnet: String?
    ) {
        mockkObject(LightWalletClient.Companion)
        every { LightWalletClient.ohttpGatewayHost(false) } returns mainnet
        every { LightWalletClient.ohttpGatewayHost(true) } returns testnet
        every { LightWalletClient.isOhttpGateway(any()) } answers {
            firstArg<LightWalletEndpoint>().host.let { it == mainnet || it == testnet }
        }
    }

    private fun provider(isTestnet: Boolean) =
        LightWalletEndpointProvider(
            mockk<Application>(relaxed = true) {
                every { resources.getBoolean(any()) } returns isTestnet
            }
        )

    private companion object {
        const val MAINNET_GATEWAY = "gateway.example"
        const val TESTNET_GATEWAY = "gateway-testnet.example"
    }
}
