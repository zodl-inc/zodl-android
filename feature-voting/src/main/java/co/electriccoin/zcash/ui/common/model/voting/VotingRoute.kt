package co.electriccoin.zcash.ui.common.model.voting

import cash.z.ecc.android.sdk.model.voting.VotingTorLease

/**
 * Immutable routing decision for one voting operation.
 *
 * Captures Tor preference and custom-server flag at acquisition time to ensure
 * ZIP #10 conformance: rule 1 (one decision per operation) and rule 4 (Tor-on
 * with no lease fails closed).
 */
data class VotingRouteDecision(
    val isTor: Boolean,
    val isCustomServer: Boolean
)

/**
 * Voting route with optional Tor lease.
 *
 * Invariant: decision.isTor == (torLease != null)
 */
class VotingRoute(
    val decision: VotingRouteDecision,
    val torLease: VotingTorLease?
) {
    /**
     * Release the Tor lease if present.
     */
    suspend fun release() {
        torLease?.release()
    }
}
