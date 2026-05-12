package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.RoundAuthStatus
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.ShareConfirmationResult
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundAuthenticationException
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.RoundsListResult
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingConfigSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class RefreshActiveVotingSessionUseCaseTest {
    @Test
    fun pinnedDifferentRoundSurvivesActiveEndpointAuthenticationFailure() = runBlocking {
        val repository = FakeVotingConfigRepository()
        val pinnedSession = makeSession(SELECTED_ROUND_ID)
        repository.setUserSelected(makeSnapshot(pinnedSession))

        val useCase = makeUseCase(
            votingConfigRepository = repository,
            votingApiProvider = FakeVotingApiProvider(
                activeFailure = VotingRoundAuthenticationException(
                    status = RoundAuthStatus.MISSING_ROUND,
                    roundIdHex = UNRELATED_ROUND_ID
                )
            )
        )

        useCase()

        assertFalse(repository.clearCalled)
        assertEquals(SELECTED_ROUND_ID, repository.userSelectedRoundId.value)
        assertEquals(pinnedSession, repository.currentConfig.value?.session)
    }

    @Test
    fun unpinnedActiveEndpointAuthenticationFailureStillClearsAndThrows() = runBlocking {
        val repository = FakeVotingConfigRepository()
        repository.store(makeSnapshot(makeSession(SELECTED_ROUND_ID)))
        val useCase = makeUseCase(
            votingConfigRepository = repository,
            votingApiProvider = FakeVotingApiProvider(
                activeFailure = VotingRoundAuthenticationException(
                    status = RoundAuthStatus.MISSING_ROUND,
                    roundIdHex = UNRELATED_ROUND_ID
                )
            )
        )

        assertFailsWith<VotingRoundAuthenticationException> {
            useCase()
        }

        assertTrue(repository.clearCalled)
        assertNull(repository.currentConfig.value)
        assertNull(repository.userSelectedRoundId.value)
    }

    @Test
    fun pinnedSameRoundAuthenticationFailureStillClearsAndThrows() = runBlocking {
        val repository = FakeVotingConfigRepository()
        repository.setUserSelected(makeSnapshot(makeSession(SELECTED_ROUND_ID)))
        val useCase = makeUseCase(
            votingConfigRepository = repository,
            votingApiProvider = FakeVotingApiProvider(
                activeFailure = VotingRoundAuthenticationException(
                    status = RoundAuthStatus.MISSING_ROUND,
                    roundIdHex = SELECTED_ROUND_ID
                )
            )
        )

        assertFailsWith<VotingRoundAuthenticationException> {
            useCase()
        }

        assertTrue(repository.clearCalled)
        assertNull(repository.currentConfig.value)
        assertNull(repository.userSelectedRoundId.value)
    }

    private fun makeUseCase(
        votingConfigRepository: VotingConfigRepository,
        votingApiProvider: VotingApiProvider
    ) = RefreshActiveVotingSessionUseCase(
        votingApiProvider = votingApiProvider,
        votingConfigRepository = votingConfigRepository,
        votingApiRepository = VotingApiRepositoryImpl()
    )

    private class FakeVotingConfigRepository : VotingConfigRepository {
        private val mutableCurrentConfig = MutableStateFlow<VotingConfigSnapshot?>(null)
        private val mutableUserSelectedRoundId = MutableStateFlow<String?>(null)

        var clearCalled = false
            private set

        override val currentConfig: StateFlow<VotingConfigSnapshot?> = mutableCurrentConfig.asStateFlow()

        override val userSelectedRoundId: StateFlow<String?> = mutableUserSelectedRoundId.asStateFlow()

        override suspend fun get(): VotingConfigSnapshot? = currentConfig.value

        override fun observe(): Flow<VotingConfigSnapshot?> = flowOf(currentConfig.value)

        override suspend fun store(snapshot: VotingConfigSnapshot) {
            mutableCurrentConfig.value = snapshot
        }

        override suspend fun storeUnlessPinnedToOther(
            snapshot: VotingConfigSnapshot,
            fetchedRoundId: String
        ): Boolean {
            val pinnedRoundId = userSelectedRoundId.value
            if (pinnedRoundId != null && !pinnedRoundId.equals(fetchedRoundId, ignoreCase = true)) {
                return false
            }
            mutableCurrentConfig.value = snapshot
            return true
        }

        override suspend fun setUserSelected(snapshot: VotingConfigSnapshot) {
            mutableUserSelectedRoundId.value = snapshot.session.voteRoundId.toLowerHex()
            mutableCurrentConfig.value = snapshot
        }

        override suspend fun clearUserSelection() {
            mutableUserSelectedRoundId.value = null
        }

        override suspend fun clear() {
            clearCalled = true
            mutableCurrentConfig.value = null
            mutableUserSelectedRoundId.value = null
        }
    }

    private class FakeVotingApiProvider(
        private val activeSession: VotingSession? = null,
        private val activeFailure: Throwable? = null
    ) : VotingApiProvider {
        override suspend fun validateConfigSource(source: PinnedConfigSource) = Unit

        override suspend fun invalidateConfigCache() = Unit

        override suspend fun fetchServiceConfig(): VotingServiceConfig = VotingServiceConfig.EMPTY

        override suspend fun fetchActiveVotingSession(): VotingSession? {
            activeFailure?.let { throw it }
            return activeSession
        }

        override suspend fun fetchAllRounds(): RoundsListResult = RoundsListResult.EMPTY

        override suspend fun fetchZodlEndorsedRoundIds(): Set<String> = emptySet()

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
        const val SELECTED_ROUND_ID = "5652e386ebe9984d04d0dcab36d4f3eff2cb3218fda0da9252e6d9794e4ef42b"
        const val UNRELATED_ROUND_ID = "48b9b3e5d9885d1ea1acb275278a3e7828397e86dbf49fe75d5a47a8b98f6b3a"

        fun makeSnapshot(session: VotingSession) =
            VotingConfigSnapshot(
                session = session,
                serviceConfig = VotingServiceConfig.EMPTY,
                source = VotingConfigSource.REMOTE
            )

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
                proposals = listOf(
                    Proposal(
                        id = 1,
                        title = "Proposal",
                        description = "Proposal description",
                        options = listOf(
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

        fun ByteArray.toLowerHex(): String =
            joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
