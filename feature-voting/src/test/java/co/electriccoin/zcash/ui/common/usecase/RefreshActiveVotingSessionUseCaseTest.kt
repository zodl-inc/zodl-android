package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLatest
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafPage
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingRouteDecision
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.RoundsListResult
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigSnapshot
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshActiveVotingSessionUseCaseTest {
    @Test
    fun refreshStoresServiceConfigAndRoundSessions() =
        runBlocking {
            val session = makeSession(SELECTED_ROUND_ID)
            val configRepository = FakeVotingConfigRepository()
            val apiRepository = VotingApiRepositoryImpl()
            val useCase =
                RefreshActiveVotingSessionUseCase(
                    votingApiProvider =
                        FakeVotingApiProvider(
                            roundsResult =
                                RoundsListResult(
                                    rounds = listOf(session.toVotingRound()),
                                    sessionsByRoundId = mapOf(SELECTED_ROUND_ID to session)
                                )
                        ),
                    votingConfigRepository = configRepository,
                    votingApiRepository = apiRepository,
                    votingRouteProvider = mockk(relaxed = true)
                )

            useCase()

            assertEquals(VotingServiceConfig.EMPTY, configRepository.currentConfig.value?.serviceConfig)
            assertEquals(listOf(session.toVotingRound()), apiRepository.snapshot.value.rounds)
            assertEquals(session, apiRepository.snapshot.value.sessionsByRoundId[SELECTED_ROUND_ID])
        }

    private class FakeVotingApiProvider(
        private val roundsResult: RoundsListResult
    ) : VotingApiProvider {
        override suspend fun validateConfigSource(source: PinnedConfigSource, decision: VotingRouteDecision) = Unit

        override suspend fun invalidateConfigCache() = Unit

        override suspend fun fetchServiceConfig(decision: VotingRouteDecision): VotingServiceConfig =
            VotingServiceConfig.EMPTY

        override suspend fun fetchActiveVotingSession(decision: VotingRouteDecision): VotingSession? = error("unused")

        override suspend fun fetchAllRounds(decision: VotingRouteDecision): RoundsListResult = roundsResult

        override suspend fun fetchZodlEndorsedRoundIds(decision: VotingRouteDecision): Set<String> = emptySet()

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

    private class FakeVotingConfigRepository : VotingConfigRepository {
        private val mutableCurrentConfig = MutableStateFlow<VotingConfigSnapshot?>(null)

        override val currentConfig = mutableCurrentConfig.asStateFlow()

        override suspend fun get(): VotingConfigSnapshot? = currentConfig.value

        override fun observe(): Flow<VotingConfigSnapshot?> = flowOf(currentConfig.value)

        override suspend fun store(snapshot: VotingConfigSnapshot) {
            mutableCurrentConfig.value = snapshot
        }

        override suspend fun clear() {
            mutableCurrentConfig.value = null
        }
    }

    private companion object {
        const val SELECTED_ROUND_ID = "5652e386ebe9984d04d0dcab36d4f3eff2cb3218fda0da9252e6d9794e4ef42b"

        fun makeSession(roundIdHex: String) =
            VotingSession(
                voteRoundId = roundIdHex.lowercaseHexToBytes(),
                snapshotHeight = 1L,
                snapshotBlockhash = ByteArray(32) { 0x01 },
                proposalsHash = ByteArray(32) { 0x02 },
                voteEndTime = Instant.ofEpochSecond(3),
                ceremonyStart = Instant.ofEpochSecond(2),
                eaPK = ByteArray(32) { 0x03 },
                vkZkp1 = ByteArray(32) { 0x04 },
                vkZkp2 = ByteArray(32) { 0x05 },
                vkZkp3 = ByteArray(32) { 0x06 },
                ncRoot = ByteArray(32) { 0x07 },
                nullifierIMTRoot = ByteArray(32) { 0x08 },
                creator = "creator",
                title = "Round",
                description = "Round description",
                discussionUrl = null,
                proposals =
                    listOf(
                        Proposal(
                            id = 1,
                            title = "Proposal",
                            description = "Proposal description",
                            options =
                                listOf(
                                    VoteOption(id = 0, label = "Support"),
                                    VoteOption(id = 1, label = "Oppose")
                                )
                        )
                    ),
                status = SessionStatus.ACTIVE,
                createdAtHeight = 1L
            )

        fun String.lowercaseHexToBytes(): ByteArray =
            chunked(2).map { chunk -> chunk.toInt(16).toByte() }.toByteArray()

        fun VotingSession.toVotingRound() =
            co.electriccoin.zcash.ui.common.model.voting.VotingRound(
                id = voteRoundId.joinToString(separator = "") { byte -> "%02x".format(byte) },
                title = title,
                description = description,
                discussionUrl = discussionUrl,
                createdAtHeight = createdAtHeight,
                snapshotHeight = snapshotHeight,
                snapshotDate = ceremonyStart,
                votingStart = ceremonyStart,
                votingEnd = voteEndTime,
                proposals = proposals,
                status = status
            )
    }
}
