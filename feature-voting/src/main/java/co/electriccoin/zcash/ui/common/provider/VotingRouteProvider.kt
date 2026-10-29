package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.VotingRoute
import co.electriccoin.zcash.ui.common.model.voting.VotingRouteDecision
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository

/**
 * Provides immutable voting routes for operations.
 *
 * Acquires route at operation start to ensure ZIP #10 conformance:
 * - Rule 1: one decision per operation (nothing re-reads preferences after acquisition)
 * - Rule 4: Tor-on with no lease fails closed (TorUnavailableException propagates)
 */
interface VotingRouteProvider {
    /**
     * Acquire a voting route for one operation.
     *
     * Reads Tor preference and custom-server flag once, acquires Tor lease if needed.
     * The caller owns the returned route and must release it in a finally block.
     *
     * @throws cash.z.ecc.android.sdk.exception.TorUnavailableException when Tor is enabled
     * but unavailable (fails closed rather than silently downgrading to clearnet)
     */
    suspend fun acquire(): VotingRoute

    /**
     * Get current route decision without acquiring a Tor lease.
     *
     * For browsing operations that don't need a session (refresh rounds, view results).
     *
     * @param forceCustomServer when true, treats the decision as custom server regardless
     * of the current chain config selection (used when validating a custom URL that's
     * being added but not yet selected)
     */
    suspend fun decision(forceCustomServer: Boolean = false): VotingRouteDecision
}

class VotingRouteProviderImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val httpClientProvider: HttpClientProvider,
    private val votingChainConfigRepository: VotingChainConfigRepository
) : VotingRouteProvider {
    override suspend fun acquire(): VotingRoute {
        val isTor = httpClientProvider.isTorEnabled()
        val isCustomServer = !votingChainConfigRepository.get().isOnDefaultConfig

        val decision = VotingRouteDecision(isTor, isCustomServer)
        val torLease =
            if (isTor) {
                synchronizerProvider.getSynchronizer().acquireVotingTorLease()
            } else {
                null
            }

        return VotingRoute(decision, torLease)
    }

    override suspend fun decision(forceCustomServer: Boolean): VotingRouteDecision {
        val isTor = httpClientProvider.isTorEnabled()
        val isCustomServer = forceCustomServer || !votingChainConfigRepository.get().isOnDefaultConfig
        return VotingRouteDecision(isTor, isCustomServer)
    }
}
