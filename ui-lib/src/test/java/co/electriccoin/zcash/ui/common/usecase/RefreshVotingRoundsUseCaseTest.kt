package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.ShareConfirmationResult
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.RoundsListResult
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class RefreshVotingRoundsUseCaseTest {
    @Test
    fun refreshStoresZodlEndorsedRoundIds() = runBlocking {
        val repository = VotingApiRepositoryImpl()
        val useCase = RefreshVotingRoundsUseCase(
            votingApiProvider = FakeVotingApiProvider(endorsedRoundIds = setOf(ROUND_ID)),
            votingApiRepository = repository
        )

        useCase()

        assertEquals(setOf(ROUND_ID), repository.snapshot.value.zodlEndorsedRoundIds)
    }

    @Test
    fun refreshTreatsZodlEndorsedRoundFailureAsNonFatal() = runBlocking {
        val repository = VotingApiRepositoryImpl()
        val useCase = RefreshVotingRoundsUseCase(
            votingApiProvider = FakeVotingApiProvider(endorsedRoundFailure = IllegalStateException("unavailable")),
            votingApiRepository = repository,
            logEndorsementFailure = {}
        )

        useCase()

        assertEquals(emptySet(), repository.snapshot.value.zodlEndorsedRoundIds)
    }

    private class FakeVotingApiProvider(
        private val endorsedRoundIds: Set<String> = emptySet(),
        private val endorsedRoundFailure: Throwable? = null
    ) : VotingApiProvider {
        override suspend fun validateConfigSource(source: PinnedConfigSource) = Unit

        override suspend fun invalidateConfigCache() = Unit

        override suspend fun fetchServiceConfig(): VotingServiceConfig = VotingServiceConfig.EMPTY

        override suspend fun fetchActiveVotingSession(): VotingSession? = null

        override suspend fun fetchAllRounds(): RoundsListResult = RoundsListResult.EMPTY

        override suspend fun fetchZodlEndorsedRoundIds(): Set<String> {
            endorsedRoundFailure?.let { throw it }
            return endorsedRoundIds
        }

        override suspend fun submitDelegation(registration: DelegationRegistration): TxResult =
            error("unused")

        override suspend fun submitVoteCommitment(
            bundle: VoteCommitmentBundle,
            signature: CastVoteSignature
        ): TxResult = error("unused")

        override suspend fun fetchTallyResults(roundIdHex: String): TallyResults =
            error("unused")

        override suspend fun delegateShares(
            shares: List<SharePayload>,
            roundIdHex: String
        ): List<DelegatedShareInfo> = error("unused")

        override suspend fun fetchShareStatus(
            helperBaseUrl: String,
            roundIdHex: String,
            nullifierHex: String
        ): ShareConfirmationResult = error("unused")

        override suspend fun resubmitShare(
            payload: SharePayload,
            roundIdHex: String,
            candidateUrls: List<String>,
            excludeUrls: List<String>
        ): List<String> = error("unused")

        override suspend fun fetchTxConfirmation(txHash: String): TxConfirmation? =
            error("unused")
    }

    private companion object {
        const val ROUND_ID = "58d9319ac86933b81769a7c0972444fa39212ad3790646398de6ce6534de2225"
    }
}
