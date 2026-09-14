package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * OHTTP first, clearnet lightwalletd only as a fallback (E2E scenarios 1–9 of the fallback spec).
 */
class ServerFallbackPolicyTest {
    private val gateway = endpoint("ohttp-gateway.example")
    private val zecRocks = endpoint("zec.rocks")
    private val naZecRocks = endpoint("na.zec.rocks")
    private val stardust = endpoint("us.zec.stardust.rest")
    private val isGateway: (LightWalletEndpoint) -> Boolean =
        { LightWalletEndpointProvider.isOhttpGatewayHost(it.host) }

    @Test
    fun `scenario 1 - OHTTP available - stays on the gateway`() {
        assertNull(decide(current = gateway, gateways = listOf(gateway), fallbacks = emptyList()))
    }

    @Test
    fun `scenario 2 - OHTTP available and clearnet servers available - clearnet is never preferred`() {
        // Even if the clearnet tier was benchmarked (it should not be) and looks faster, OHTTP wins.
        assertNull(decide(current = gateway, gateways = listOf(gateway), fallbacks = listOf(naZecRocks, zecRocks)))
    }

    @Test
    fun `scenario 3 - OHTTP down - falls back to the best validated clearnet server`() {
        assertEquals(
            naZecRocks,
            decide(current = gateway, gateways = emptyList(), fallbacks = listOf(naZecRocks, zecRocks))
        )
    }

    @Test
    fun `scenario 4 - OHTTP back - returns to the gateway from a fallback`() {
        assertEquals(gateway, decide(current = naZecRocks, gateways = listOf(gateway), fallbacks = emptyList()))
    }

    @Test
    fun `scenario 4b - a working fallback does not become the new default once OHTTP is back`() {
        assertEquals(gateway, decide(current = naZecRocks, gateways = listOf(gateway), fallbacks = listOf(naZecRocks)))
    }

    @Test
    fun `scenario 5 - multiple clearnet servers - the ranking decides`() {
        assertEquals(
            stardust,
            decide(current = gateway, gateways = emptyList(), fallbacks = listOf(stardust, zecRocks, naZecRocks))
        )
    }

    @Test
    fun `scenario 6 - the selected fallback fails validation - the next ranked server is used`() {
        // na.zec.rocks (current) is absent from the ranking because it failed; zec.rocks is next.
        assertEquals(
            zecRocks,
            decide(current = naZecRocks, gateways = emptyList(), fallbacks = listOf(zecRocks, stardust))
        )
    }

    @Test
    fun `scenario 6b - staying on the fallback that is still the best avoids a useless rebuild`() {
        assertNull(decide(current = naZecRocks, gateways = emptyList(), fallbacks = listOf(naZecRocks, zecRocks)))
    }

    @Test
    fun `scenario 7 - everything down - no switch and no inconsistent state`() {
        assertNull(decide(current = gateway, gateways = emptyList(), fallbacks = emptyList()))
        assertNull(decide(current = naZecRocks, gateways = emptyList(), fallbacks = emptyList()))
    }

    @Test
    fun `host comparison is case-insensitive`() {
        assertNull(decide(current = endpoint("NA.zec.rocks"), gateways = emptyList(), fallbacks = listOf(naZecRocks)))
    }

    @Test
    fun `fallback flag reflects whether the endpoint is a gateway`() {
        assertEquals(false, ServerFallbackPolicy.isFallback(gateway, isGateway))
        assertEquals(true, ServerFallbackPolicy.isFallback(zecRocks, isGateway))
    }

    private fun decide(
        current: LightWalletEndpoint,
        gateways: List<LightWalletEndpoint>,
        fallbacks: List<LightWalletEndpoint>,
    ) = ServerFallbackPolicy.decide(current, gateways, fallbacks, isGateway)

    private fun endpoint(host: String) = LightWalletEndpoint(host = host, port = 443, isSecure = true)
}
