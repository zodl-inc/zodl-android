package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class RefreshVotingRoundsUseCase(
    private val votingApiProvider: VotingApiProvider,
    private val votingApiRepository: VotingApiRepository,
    private val logEndorsementFailure: (Exception) -> Unit = ::logFailedZodlEndorsements,
) {
    /**
     * Fetches the rounds and the Zodl endorsements concurrently, then stores the endorsements
     * before the rounds. The poll list shows only endorsed rounds on the default config, so rounds
     * published ahead of their endorsements read as "no polls" for as long as the endorsement
     * request takes (seconds over Tor): the empty-list sheet flashed up and went away again.
     */
    suspend operator fun invoke() {
        votingApiProvider.fetchServiceConfig()
        coroutineScope {
            val endorsedRoundIds =
                async {
                    try {
                        votingApiProvider.fetchZodlEndorsedRoundIds()
                    } catch (exception: Exception) {
                        if (exception is CancellationException) {
                            throw exception
                        }
                        logEndorsementFailure(exception)
                        emptySet()
                    }
                }
            val roundsResult = votingApiProvider.fetchAllRounds()
            votingApiRepository.storeZodlEndorsedRoundIds(endorsedRoundIds.await())
            votingApiRepository.storeRounds(roundsResult.rounds, roundsResult.sessionsByRoundId)
        }
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
