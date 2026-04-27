package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetAllVotingRoundsUseCase(
    private val votingApiProvider: VotingApiProvider
) {
    suspend operator fun invoke(): List<VotingRound> =
        withContext(Dispatchers.IO) {
            votingApiProvider.fetchServiceConfig()
            votingApiProvider.fetchAllRounds()
        }
}
