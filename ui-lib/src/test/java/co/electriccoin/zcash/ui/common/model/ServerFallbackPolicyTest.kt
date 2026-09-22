package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OHTTP first, no automatic clearnet fallback (product decision 2026-09-22). Gateway unavailability
 * is reported to the UI; clearnet servers require manual selection.
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
        assertEquals(
            ServerFallbackPolicy.DecisionResult.Stay,
            decide(current = gateway, gateways = listOf(gateway), fallbacks = emptyList())
        )
    }

    @Test
    fun `scenario 2 - OHTTP available and clearnet servers available - clearnet is never preferred`() {
        // Even if the clearnet tier was benchmarked (it should not be) and looks faster, OHTTP wins.
        assertEquals(
            ServerFallbackPolicy.DecisionResult.Stay,
            decide(current = gateway, gateways = listOf(gateway), fallbacks = listOf(naZecRocks, zecRocks))
        )
    }

    @Test
    fun `scenario 3 - OHTTP down on gateway - reports unavailability instead of automatic fallback`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.StayAndReportGatewayUnavailable,
            decide(current = gateway, gateways = emptyList(), fallbacks = listOf(naZecRocks, zecRocks))
        )
    }

    @Test
    fun `scenario 4 - OHTTP back - returns to the gateway from a clearnet fallback`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.ReturnToGateway(gateway),
            decide(current = naZecRocks, gateways = listOf(gateway), fallbacks = emptyList())
        )
    }

    @Test
    fun `scenario 4b - a working fallback does not become the default once OHTTP is back`() {
        // Recovery from manual clearnet selection: gateway healthy → return to it.
        assertEquals(
            ServerFallbackPolicy.DecisionResult.ReturnToGateway(gateway),
            decide(current = naZecRocks, gateways = listOf(gateway), fallbacks = listOf(naZecRocks))
        )
    }

    @Test
    fun `scenario 5 - manual clearnet with gateway still down - stays put`() {
        // User manually selected stardust; gateway still unreachable → no change.
        assertEquals(
            ServerFallbackPolicy.DecisionResult.Stay,
            decide(current = stardust, gateways = emptyList(), fallbacks = listOf(stardust, zecRocks))
        )
    }

    @Test
    fun `scenario 6 - everything down on gateway - reports unavailability`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.StayAndReportGatewayUnavailable,
            decide(current = gateway, gateways = emptyList(), fallbacks = emptyList())
        )
    }

    @Test
    fun `scenario 7 - everything down on clearnet fallback - stays put`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.Stay,
            decide(current = naZecRocks, gateways = emptyList(), fallbacks = emptyList())
        )
    }

    @Test
    fun `decision never returns a tier-1 endpoint as UseGateway`() {
        // The only endpoints ever returned in UseGateway/ReturnToGateway are gateways.
        val result = decide(current = gateway, gateways = emptyList(), fallbacks = listOf(zecRocks))
        assertEquals(ServerFallbackPolicy.DecisionResult.StayAndReportGatewayUnavailable, result)
    }

    @Test
    fun `host comparison is case-insensitive`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.Stay,
            decide(current = endpoint("NA.zec.rocks"), gateways = emptyList(), fallbacks = listOf(naZecRocks))
        )
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
