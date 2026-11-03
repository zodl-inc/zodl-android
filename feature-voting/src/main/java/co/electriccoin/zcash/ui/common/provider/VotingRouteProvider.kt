package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.VotingConfigSourceSnapshot
import co.electriccoin.zcash.ui.common.model.voting.VotingRoute
import co.electriccoin.zcash.ui.common.model.voting.VotingRouteDecision
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState

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
     * @param isCustomServer when provided, uses this authoritative flag; when omitted,
     * reads the current chain config selection
     */
    suspend fun decision(isCustomServer: Boolean? = null): VotingRouteDecision
}

class VotingRouteProviderImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val httpClientProvider: HttpClientProvider,
    private val votingChainConfigRepository: VotingChainConfigRepository
) : VotingRouteProvider {
    override suspend fun acquire(): VotingRoute {
        val isTor = httpClientProvider.isTorEnabled()
        val chainConfig = votingChainConfigRepository.get()

        val decision = VotingRouteDecision(isTor, !chainConfig.isOnDefaultConfig, chainConfig.sourceSnapshot())
        val torLease =
            if (isTor) {
                synchronizerProvider.getSynchronizer().acquireVotingTorLease()
            } else {
                null
            }

        return VotingRoute(decision, torLease)
    }

    override suspend fun decision(isCustomServer: Boolean?): VotingRouteDecision {
        val isTor = httpClientProvider.isTorEnabled()
        val chainConfig = votingChainConfigRepository.get()
        val customFlag = isCustomServer ?: !chainConfig.isOnDefaultConfig
        return VotingRouteDecision(isTor, customFlag, chainConfig.sourceSnapshot())
    }
}

private fun VotingChainConfigState.sourceSnapshot(): VotingConfigSourceSnapshot =
    selectedPinnedSource?.let(VotingConfigSourceSnapshot::Pinned) ?: VotingConfigSourceSnapshot.Default
