package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingConfigSource

/**
 * Fetches and stores the resolved voting service config, without also fetching and storing the
 * round list (MOB-1808). [RefreshActiveVotingSessionUseCase] does both — needed as-is by
 * [co.electriccoin.zcash.voting.VotingHomeHooksImpl]'s pending-Keystone-request recovery check,
 * which genuinely wants the round list populated as a side effect. But
 * `VoteCoinholderPollingVM.refreshVotingDataInternal()` calls `RefreshVotingRoundsUseCase` (which
 * already fetches config + the full round list + endorsed round ids) immediately before this —
 * using `RefreshActiveVotingSessionUseCase` there too, just to surface a [VotingConfigException]
 * the config fetch might throw, was re-fetching the entire round list a second time for no reason
 * every single auto-refresh tick (confirmed live: two `/shielded-vote/v1/rounds` requests per
 * cycle, ~1.3s apart). This use case is that same config-fetch-and-store step on its own.
 */
class RefreshVotingServiceConfigUseCase(
    private val votingApiProvider: VotingApiProvider,
    private val votingConfigRepository: VotingConfigRepository,
) {
    suspend operator fun invoke() {
        val serviceConfig = votingApiProvider.fetchServiceConfig()
        votingConfigRepository.store(
            VotingConfigSnapshot(
                serviceConfig = serviceConfig,
                source = VotingConfigSource.REMOTE
            )
        )
    }
}
