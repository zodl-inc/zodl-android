package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLatest
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafPage
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingRouteDecision
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.RoundsListResult
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiRepositoryImpl
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshVotingRoundsUseCaseTest {
    @Test
    fun refreshStoresZodlEndorsedRoundIds() =
        runBlocking {
            val repository = VotingApiRepositoryImpl()
            val useCase =
                RefreshVotingRoundsUseCase(
                    votingApiProvider = FakeVotingApiProvider(endorsedRoundIds = setOf(ROUND_ID)),
                    votingApiRepository = repository,
                    votingRouteProvider = mockk(relaxed = true)
                )

            useCase()

            assertEquals(setOf(ROUND_ID), repository.snapshot.value.zodlEndorsedRoundIds)
        }

    @Test
    fun refreshTreatsZodlEndorsedRoundFailureAsNonFatal() =
        runBlocking {
            val repository = VotingApiRepositoryImpl()
            val useCase =
                RefreshVotingRoundsUseCase(
                    votingApiProvider =
                        FakeVotingApiProvider(endorsedRoundFailure = IllegalStateException("unavailable")),
                    votingApiRepository = repository,
                    votingRouteProvider = mockk(relaxed = true),
                    logEndorsementFailure = {}
                )

            useCase()

            assertEquals(emptySet(), repository.snapshot.value.zodlEndorsedRoundIds)
        }

    @Test
    fun refreshNeverPublishesRoundsBeforeTheirEndorsements() =
        runBlocking {
            val repository = VotingApiRepositoryImpl()
            val endorsementsWhenRoundsStored = mutableListOf<Set<String>>()
            val recordingRepository =
                object : VotingApiRepository by repository {
                    override fun storeRounds(
                        rounds: List<VotingRound>,
                        sessionsByRoundId: Map<String, VotingSession>
                    ) {
                        endorsementsWhenRoundsStored += repository.snapshot.value.zodlEndorsedRoundIds
                        repository.storeRounds(rounds, sessionsByRoundId)
                    }
                }
            val useCase =
                RefreshVotingRoundsUseCase(
                    votingApiProvider = FakeVotingApiProvider(endorsedRoundIds = setOf(ROUND_ID)),
                    votingApiRepository = recordingRepository,
                    votingRouteProvider = mockk(relaxed = true)
                )

            useCase()

            assertEquals(listOf(setOf(ROUND_ID)), endorsementsWhenRoundsStored)
        }

    private class FakeVotingApiProvider(
        private val endorsedRoundIds: Set<String> = emptySet(),
        private val endorsedRoundFailure: Throwable? = null
    ) : VotingApiProvider {
        override suspend fun validateConfigSource(source: PinnedConfigSource, decision: VotingRouteDecision) = Unit

        override suspend fun invalidateConfigCache() = Unit

        override suspend fun fetchServiceConfig(decision: VotingRouteDecision): VotingServiceConfig =
            VotingServiceConfig.EMPTY

        override suspend fun fetchActiveVotingSession(decision: VotingRouteDecision): VotingSession? = null

        override suspend fun fetchAllRounds(decision: VotingRouteDecision): RoundsListResult = RoundsListResult.EMPTY

        override suspend fun fetchZodlEndorsedRoundIds(decision: VotingRouteDecision): Set<String> {
            endorsedRoundFailure?.let { throw it }
            return endorsedRoundIds
        }

        override suspend fun fetchTallyResults(
            roundIdHex: String,
            decision: VotingRouteDecision
        ): TallyResults = error("unused")

        override suspend fun delegateShares(
            shares: List<SharePayload>,
            decision: VotingRouteDecision
        ): List<DelegatedShareInfo> = error("unused")

        override suspend fun fetchCommitmentTreeLatest(
            roundIdHex: String,
            decision: VotingRouteDecision
        ): CommitmentTreeLatest = error("unused")

        override suspend fun fetchCommitmentTreeLeafPage(
            roundIdHex: String,
            fromHeight: Long,
            toHeight: Long,
            decision: VotingRouteDecision
        ): CommitmentTreeLeafPage = error("unused")
    }

    private companion object {
        const val ROUND_ID = "58d9319ac86933b81769a7c0972444fa39212ad3790646398de6ce6534de2225"
    }
}
