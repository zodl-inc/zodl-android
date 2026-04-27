package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetActiveVotingSessionUseCase(
    private val votingApiProvider: VotingApiProvider
) {
    suspend operator fun invoke(): VotingSession? =
        withContext(Dispatchers.IO) {
            votingApiProvider.fetchServiceConfig()
            votingApiProvider.fetchActiveVotingSession()
        }
}
