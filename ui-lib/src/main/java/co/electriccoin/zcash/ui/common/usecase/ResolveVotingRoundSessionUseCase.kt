package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ext.toHex
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingConfigSource

data class VotingRoundSessionContext(
    val session: VotingSession,
    val serviceConfig: VotingServiceConfig
)

class ResolveVotingRoundSessionUseCase(
    private val votingApiProvider: VotingApiProvider,
    private val votingApiRepository: VotingApiRepository,
    private val votingConfigRepository: VotingConfigRepository
) {
    suspend operator fun invoke(roundId: String): VotingRoundSessionContext {
        val normalizedRoundId = roundId.lowercase()
        val serviceConfig = votingApiProvider.fetchServiceConfig()
        votingConfigRepository.store(
            VotingConfigSnapshot(
                serviceConfig = serviceConfig,
                source = VotingConfigSource.REMOTE
            )
        )

        val roundsResult = votingApiProvider.fetchAllRounds()
        votingApiRepository.storeRounds(roundsResult.rounds, roundsResult.sessionsByRoundId)

        val session = roundsResult.sessionsByRoundId[normalizedRoundId]
            ?: roundsResult.sessionsByRoundId.values.firstOrNull { session ->
                session.voteRoundId.toHex().equals(normalizedRoundId, ignoreCase = true)
            }
            ?: error("Voting round $roundId is not present in authenticated rounds")

        return VotingRoundSessionContext(
            session = session,
            serviceConfig = serviceConfig
        )
    }
}
