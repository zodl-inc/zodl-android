package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VotingApiSnapshot(
    val rounds: List<VotingRound> = emptyList(),
    /**
     * Authenticated [VotingSession]s keyed by lower-cased round id, populated by
     * `/rounds`. Lets the polls-list VM hydrate a [VotingConfigSnapshot] for the
     * user's tapped round without re-hitting `/rounds/active`.
     */
    val sessionsByRoundId: Map<String, VotingSession> = emptyMap(),
    val zodlEndorsedRoundIds: Set<String> = emptySet(),
    val tallyResultsByRoundId: Map<String, TallyResults> = emptyMap(),
    val transactionConfirmations: Map<String, Boolean> = emptyMap()
)

interface VotingApiRepository {
    val snapshot: StateFlow<VotingApiSnapshot>

    fun storeRounds(
        rounds: List<VotingRound>,
        sessionsByRoundId: Map<String, VotingSession> = emptyMap()
    )

    fun storeZodlEndorsedRoundIds(roundIds: Set<String>)

    fun upsertRound(round: VotingRound)

    fun storeTallyResults(
        roundId: String,
        results: TallyResults
    )

    fun rememberTransactionConfirmation(
        txHash: String,
        isConfirmed: Boolean
    )

    fun clear()
}

class VotingApiRepositoryImpl : VotingApiRepository {
    private val mutableSnapshot = MutableStateFlow(VotingApiSnapshot())

    override val snapshot: StateFlow<VotingApiSnapshot> = mutableSnapshot.asStateFlow()

    override fun storeRounds(
        rounds: List<VotingRound>,
        sessionsByRoundId: Map<String, VotingSession>
    ) {
        mutableSnapshot.update { current ->
            current.copy(
                rounds = rounds,
                sessionsByRoundId = if (sessionsByRoundId.isEmpty()) {
                    current.sessionsByRoundId
                } else {
                    sessionsByRoundId
                }
            )
        }
    }

    override fun storeZodlEndorsedRoundIds(roundIds: Set<String>) {
        mutableSnapshot.update { current -> current.copy(zodlEndorsedRoundIds = roundIds) }
    }

    override fun upsertRound(round: VotingRound) {
        mutableSnapshot.update { current ->
            val existingIndex = current.rounds.indexOfFirst { existing -> existing.id == round.id }
            val updatedRounds = current.rounds.toMutableList()

            if (existingIndex >= 0) {
                updatedRounds[existingIndex] = round
            } else {
                updatedRounds += round
            }

            current.copy(rounds = updatedRounds)
        }
    }

    override fun storeTallyResults(
        roundId: String,
        results: TallyResults
    ) {
        mutableSnapshot.update { current ->
            current.copy(
                tallyResultsByRoundId = current.tallyResultsByRoundId + (roundId to results)
            )
        }
    }

    override fun rememberTransactionConfirmation(
        txHash: String,
        isConfirmed: Boolean
    ) {
        mutableSnapshot.update { current ->
            current.copy(
                transactionConfirmations = current.transactionConfirmations + (txHash to isConfirmed)
            )
        }
    }

    override fun clear() {
        mutableSnapshot.value = VotingApiSnapshot()
    }
}
