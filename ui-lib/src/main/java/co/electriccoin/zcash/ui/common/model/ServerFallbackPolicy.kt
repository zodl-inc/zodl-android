package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint

/**
 * Automatic-mode server policy: OHTTP first, clearnet lightwalletd only on manual selection.
 *
 * Pure decision logic, shared by the runtime coordinator and its unit tests:
 * - While any OHTTP gateway is reachable it is THE server. Speed is never compared against the
 *   clearnet tier, so a healthy gateway is never demoted, and a wallet currently on a fallback
 *   server returns to the gateway as soon as it is reachable again.
 * - When no gateway is reachable, automatic mode STAYS on the gateway and reports the unavailability
 *   to the user (via UI banner), requiring manual clearnet server selection. Automatic fallback is
 *   disabled (product decision 2026-09-22).
 * - With nothing reachable at all the wallet stays where it is (the SDK keeps surfacing the
 *   connection error) instead of hopping to a server that just failed.
 *
 * Manual mode never consults this policy: an explicit choice is used as-is.
 */
object ServerFallbackPolicy {
    sealed interface DecisionResult {
        /** Stay on the gateway and report it is unavailable to the UI. */
        data object StayAndReportGatewayUnavailable : DecisionResult

        /** Use the given OHTTP gateway. */
        data class UseGateway(
            val endpoint: LightWalletEndpoint
        ) : DecisionResult

        /** Return to the OHTTP gateway from a clearnet fallback (manual selection recovery). */
        data class ReturnToGateway(
            val endpoint: LightWalletEndpoint
        ) : DecisionResult

        /** Stay on the current endpoint (no change). */
        data object Stay : DecisionResult
    }

    /**
     * @param current the endpoint the wallet is on right now
     * @param healthyGateways OHTTP gateways that just passed validation (empty = OHTTP unreachable)
     * @param rankedFallbacks clearnet servers that passed validation, fastest first (unused in automatic mode now)
     * @param isOhttpGateway how to recognize an OHTTP gateway endpoint
     * @return the decision: which endpoint to use or report unavailability
     */
    fun decide(
        current: LightWalletEndpoint,
        healthyGateways: List<LightWalletEndpoint>,
        @Suppress("UNUSED_PARAMETER") rankedFallbacks: List<LightWalletEndpoint>,
        isOhttpGateway: (LightWalletEndpoint) -> Boolean,
    ): DecisionResult {
        val gateway = healthyGateways.firstOrNull()
        val onGateway = isOhttpGateway(current)
        return when {
            // OHTTP reachable: stay if already on a gateway, otherwise return to it.
            gateway != null && onGateway -> DecisionResult.Stay

            gateway != null && !onGateway -> DecisionResult.ReturnToGateway(gateway)

            // OHTTP unreachable and on gateway: report unavailability (no auto-fallback).
            !onGateway && gateway == null -> DecisionResult.Stay

            onGateway && gateway == null -> DecisionResult.StayAndReportGatewayUnavailable

            // Nothing reachable: stay put.
            else -> DecisionResult.Stay
        }
    }

    /** Whether running on [endpoint] in Automatic mode means the OHTTP fallback is active. */
    fun isFallback(
        endpoint: LightWalletEndpoint,
        isOhttpGateway: (LightWalletEndpoint) -> Boolean,
    ): Boolean = !isOhttpGateway(endpoint)
}
