package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ext.toHex
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingConfigSource

class RefreshActiveVotingSessionUseCase(
    private val votingApiProvider: VotingApiProvider,
    private val votingConfigRepository: VotingConfigRepository,
    private val votingApiRepository: VotingApiRepository,
) {
    suspend operator fun invoke() {
        val serviceConfig = votingApiProvider.fetchServiceConfig()
        votingConfigRepository.store(
            VotingConfigSnapshot(
                serviceConfig = serviceConfig,
                source = VotingConfigSource.REMOTE
            )
        )

        val roundsResult = votingApiProvider.fetchAllRounds()
        votingApiRepository.storeRounds(roundsResult.rounds, roundsResult.sessionsByRoundId)
    }
}

private fun VotingSession.toVotingRound() =
    co.electriccoin.zcash.ui.common.model.voting.VotingRound(
        id = voteRoundId.joinToString(separator = "") { byte -> "%02x".format(byte) },
        title = title,
        description = description,
        discussionUrl = discussionUrl,
        createdAtHeight = createdAtHeight,
        snapshotHeight = snapshotHeight,
        snapshotDate = ceremonyStart.takeIf { it.epochSecond > 0 } ?: voteEndTime,
        votingStart = ceremonyStart,
        votingEnd = voteEndTime,
        proposals = proposals,
        status = status
    )
