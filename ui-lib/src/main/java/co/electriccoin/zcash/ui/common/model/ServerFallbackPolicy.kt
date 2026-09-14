package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint

/**
 * Automatic-mode server policy: OHTTP first, clearnet lightwalletd only as a fallback.
 *
 * Pure decision logic, shared by the runtime coordinator and its unit tests:
 * - While any OHTTP gateway is reachable it is THE server. Speed is never compared against the
 *   clearnet tier, so a healthy gateway is never demoted, and a wallet currently on a fallback
 *   server returns to the gateway as soon as it is reachable again.
 * - Only when no gateway is reachable does the best validated clearnet server take over. A server
 *   that failed validation is simply absent from [rankedFallbacks], so the next one is chosen.
 * - With nothing reachable at all the wallet stays where it is (the SDK keeps surfacing the
 *   connection error) instead of hopping to a server that just failed.
 *
 * Manual mode never consults this policy: an explicit choice is used as-is.
 */
object ServerFallbackPolicy {
    /**
     * @param current the endpoint the wallet is on right now
     * @param healthyGateways OHTTP gateways that just passed validation (empty = OHTTP unreachable)
     * @param rankedFallbacks clearnet servers that passed validation, fastest first
     * @param isOhttpGateway how to recognize an OHTTP gateway endpoint
     * @return the endpoint to move to, or null to stay on [current]
     */
    fun decide(
        current: LightWalletEndpoint,
        healthyGateways: List<LightWalletEndpoint>,
        rankedFallbacks: List<LightWalletEndpoint>,
        isOhttpGateway: (LightWalletEndpoint) -> Boolean,
    ): LightWalletEndpoint? {
        val gateway = healthyGateways.firstOrNull()
        val best = rankedFallbacks.firstOrNull()
        return when {
            // OHTTP reachable: stay if already on a gateway, otherwise go back to it.
            gateway != null -> gateway.takeUnless { isOhttpGateway(current) }

            // OHTTP unreachable: best validated clearnet server, unless we are already on it.
            best != null -> best.takeUnless { it.isSameServer(current) }

            // Nothing reachable: stay put.
            else -> null
        }
    }

    /** Whether running on [endpoint] in Automatic mode means the OHTTP fallback is active. */
    fun isFallback(
        endpoint: LightWalletEndpoint,
        isOhttpGateway: (LightWalletEndpoint) -> Boolean,
    ): Boolean = !isOhttpGateway(endpoint)

    private fun LightWalletEndpoint.isSameServer(other: LightWalletEndpoint): Boolean =
        host.equals(other.host, ignoreCase = true) && port == other.port
}
