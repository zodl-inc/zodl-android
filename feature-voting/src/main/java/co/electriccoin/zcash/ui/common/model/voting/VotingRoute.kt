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
    val isCustomServer: Boolean,
    /**
     * The voting config source selected when the decision was taken, so the config fetch of the
     * operation uses the same selection as [isCustomServer] even if the user switches chains
     * mid-operation.
     */
    val configSource: VotingConfigSourceSnapshot = VotingConfigSourceSnapshot.Unset,
    /**
     * Whether this build has OHTTP on for the wallet's network (the app installed a configuration for it).
     * Without it, predefined servers are reached like custom ones (Tor or direct), as the SDK's own voting
     * route does: there is no gateway to ride.
     */
    val isOhttpAvailable: Boolean = true,
) {
    /** Tor off, a predefined server and OHTTP on: the request rides the OHTTP gateway and fails closed. */
    val usesOhttp: Boolean
        get() = !isTor && !isCustomServer && isOhttpAvailable
}

/** The chain-config selection captured with a [VotingRouteDecision]. */
sealed interface VotingConfigSourceSnapshot {
    /** No selection captured: the config fetch reads the current selection (decisions built by hand). */
    data object Unset : VotingConfigSourceSnapshot

    /** The bundled default config (no pinned source selected). */
    data object Default : VotingConfigSourceSnapshot

    /** The selected chain's pinned config source, exactly as stored. */
    data class Pinned(
        val source: String
    ) : VotingConfigSourceSnapshot
}

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
