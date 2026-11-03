package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ext.toHex
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingConfigSource

class RefreshActiveVotingSessionUseCase(
    private val votingApiProvider: VotingApiProvider,
    private val votingConfigRepository: VotingConfigRepository,
    private val votingApiRepository: VotingApiRepository,
    private val votingRouteProvider: co.electriccoin.zcash.ui.common.provider.VotingRouteProvider,
) {
    suspend operator fun invoke() {
        val decision = votingRouteProvider.decision()
        val serviceConfig = votingApiProvider.fetchServiceConfig(decision)
        votingConfigRepository.store(
            VotingConfigSnapshot(
                serviceConfig = serviceConfig,
                source = VotingConfigSource.REMOTE
            )
        )

        val roundsResult = votingApiProvider.fetchAllRounds(decision)
        votingApiRepository.storeRounds(roundsResult.rounds, roundsResult.sessionsByRoundId)
    }
}
