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
            decide(current = gateway, gateways = listOf(gateway))
        )
    }

    @Test
    fun `scenario 3 - OHTTP down on gateway - reports unavailability instead of automatic fallback`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.StayAndReportGatewayUnavailable,
            decide(current = gateway, gateways = emptyList())
        )
    }

    @Test
    fun `scenario 4 - OHTTP back - returns to the gateway from a clearnet fallback`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.ReturnToGateway(gateway),
            decide(current = naZecRocks, gateways = listOf(gateway))
        )
    }

    @Test
    fun `scenario 5 - manual clearnet with gateway still down - stays put`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.Stay,
            decide(current = stardust, gateways = emptyList())
        )
    }

    @Test
    fun `scenario 6 - everything down on gateway - reports unavailability`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.StayAndReportGatewayUnavailable,
            decide(current = gateway, gateways = emptyList())
        )
    }

    @Test
    fun `scenario 7 - everything down on clearnet fallback - stays put`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.Stay,
            decide(current = naZecRocks, gateways = emptyList())
        )
    }

    @Test
    fun `automatic wallet on clearnet with the gateway down - moves to the gateway and reports it`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.ReturnToGateway(gateway, isReachable = false),
            ServerFallbackPolicy.decide(naZecRocks, emptyList(), isGateway, configuredGateways = listOf(gateway))
        )
    }

    @Test
    fun `host comparison is case-insensitive`() {
        assertEquals(
            ServerFallbackPolicy.DecisionResult.Stay,
            decide(current = endpoint("NA.zec.rocks"), gateways = emptyList())
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
    ) = ServerFallbackPolicy.decide(current, gateways, isGateway)

    private fun endpoint(host: String) = LightWalletEndpoint(host = host, port = 443, isSecure = true)
}
