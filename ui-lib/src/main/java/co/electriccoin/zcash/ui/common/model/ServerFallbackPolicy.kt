package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint

/**
 * Automatic-mode server policy: OHTTP first, clearnet lightwalletd only on manual selection.
 *
 * Pure decision logic, shared by the runtime coordinator and its unit tests:
 * - While any OHTTP gateway is reachable it is THE server. Speed is never compared against the
 *   clearnet tier, so a healthy gateway is never demoted.
 * - Automatic mode selects only the gateway tier: a wallet in Automatic mode found on a clearnet
 *   server (e.g. one upgraded from before OHTTP) is moved to a gateway whether or not one answers
 *   right now; when none answers, the outage is then confirmed and reported like any other.
 * - When no gateway is reachable, automatic mode STAYS on the gateway and reports the unavailability
 *   to the user (via UI banner), requiring manual clearnet server selection. Automatic fallback is
 *   disabled (product decision 2026-09-22).
 *
 * Manual mode never consults this policy: an explicit choice is used as-is.
 */
object ServerFallbackPolicy {
    sealed interface DecisionResult {
        /** Stay on the gateway and report it is unavailable to the UI. */
        data object StayAndReportGatewayUnavailable : DecisionResult

        /** Move from a clearnet server to the OHTTP gateway; [isReachable] false = it did not answer this check. */
        data class ReturnToGateway(
            val endpoint: LightWalletEndpoint,
            val isReachable: Boolean = true,
        ) : DecisionResult

        /** Stay on the current endpoint (no change). */
        data object Stay : DecisionResult
    }

    /**
     * @param current the endpoint the wallet is on right now
     * @param healthyGateways OHTTP gateways that just passed validation (empty = OHTTP unreachable)
     * @param isOhttpGateway how to recognize an OHTTP gateway endpoint
     * @param configuredGateways every bundled gateway, the target when none of them answers
     * @return the decision: which endpoint to use or report unavailability
     */
    fun decide(
        current: LightWalletEndpoint,
        healthyGateways: List<LightWalletEndpoint>,
        isOhttpGateway: (LightWalletEndpoint) -> Boolean,
        configuredGateways: List<LightWalletEndpoint> = emptyList(),
    ): DecisionResult {
        val gateway = healthyGateways.firstOrNull()
        val onGateway = isOhttpGateway(current)
        val unreachableTarget = configuredGateways.firstOrNull()
        // A current gateway that did not answer gives way to one that did. With one gateway per network
        // (LightWalletEndpointProvider) this second rule cannot fire today: `healthyGateways` is then
        // either empty or the current gateway. It activates once a second gateway of the same network is
        // configured and probed on the health tick.
        return when {
            gateway != null && !onGateway -> DecisionResult.ReturnToGateway(gateway)
            gateway != null && current !in healthyGateways -> DecisionResult.ReturnToGateway(gateway)
            gateway == null && onGateway -> DecisionResult.StayAndReportGatewayUnavailable
            gateway == null && unreachableTarget != null -> DecisionResult.ReturnToGateway(unreachableTarget, false)
            else -> DecisionResult.Stay
        }
    }

    /** Whether running on [endpoint] in Automatic mode means the OHTTP fallback is active. */
    fun isFallback(
        endpoint: LightWalletEndpoint,
        isOhttpGateway: (LightWalletEndpoint) -> Boolean,
    ): Boolean = !isOhttpGateway(endpoint)
}
