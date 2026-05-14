package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import kotlinx.coroutines.CancellationException

class RefreshVotingRoundsUseCase(
    private val votingApiProvider: VotingApiProvider,
    private val votingApiRepository: VotingApiRepository,
    private val logEndorsementFailure: (Exception) -> Unit = ::logFailedZodlEndorsements,
) {
    suspend operator fun invoke() {
        votingApiProvider.fetchServiceConfig()
        val roundsResult = votingApiProvider.fetchAllRounds()
        votingApiRepository.storeRounds(roundsResult.rounds, roundsResult.sessionsByRoundId)
        val endorsedRoundIds =
            try {
                votingApiProvider.fetchZodlEndorsedRoundIds()
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }
                logEndorsementFailure(exception)
                emptySet()
            }
        votingApiRepository.storeZodlEndorsedRoundIds(endorsedRoundIds)
    }
}

private fun logFailedZodlEndorsements(exception: Exception) {
    Log.w(
        TAG,
        "Failed to fetch zodl endorsements; continuing without endorsement decorations",
        exception
    )
}

private const val TAG = "RefreshVotingRoundsUseCase"
