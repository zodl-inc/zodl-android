package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.abstainOptionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VotingEligibility {
    UNKNOWN,
    WALLET_SYNCING,
    ELIGIBLE,
    INELIGIBLE
}

data class VotingSessionScope(
    val accountUuid: String,
    val roundId: String
)

data class VotingSessionStoreState(
    val draftVotesByScope: Map<VotingSessionScope, Map<Int, Int>> = emptyMap(),
    val submittedRoundsByScope: Map<VotingSessionScope, Int> = emptyMap(),
    val eligibility: VotingEligibility = VotingEligibility.UNKNOWN
) {
    fun draftVotesFor(
        accountUuid: String,
        roundId: String
    ): Map<Int, Int> = draftVotesByScope[VotingSessionScope(accountUuid, roundId)].orEmpty()

    fun submittedProposalCount(
        accountUuid: String,
        roundId: String
    ): Int? = submittedRoundsByScope[VotingSessionScope(accountUuid, roundId)]
}

interface VotingSessionStore {
    val state: StateFlow<VotingSessionStoreState>

    fun restoreDraftVotes(
        accountUuid: String,
        roundId: String,
        draftVotes: Map<Int, Int>
    )

    fun setEligibility(eligibility: VotingEligibility)

    fun toggleDraftVote(
        accountUuid: String,
        roundId: String,
        proposalId: Int,
        optionId: Int
    )

    fun abstainUnanswered(
        accountUuid: String,
        roundId: String,
        proposals: List<Proposal>
    )

    fun clearDraftVote(
        accountUuid: String,
        roundId: String,
        proposalId: Int
    )

    fun markRoundSubmitted(
        accountUuid: String,
        roundId: String,
        proposalCount: Int
    )

    fun clearDraftVotes(
        accountUuid: String,
        roundId: String
    )

    fun clear()
}

class VotingSessionStoreImpl : VotingSessionStore {
    private val mutableState = MutableStateFlow(VotingSessionStoreState())

    override val state: StateFlow<VotingSessionStoreState> = mutableState.asStateFlow()

    override fun restoreDraftVotes(
        accountUuid: String,
        roundId: String,
        draftVotes: Map<Int, Int>
    ) {
        val scope = VotingSessionScope(accountUuid, roundId)
        mutableState.update { current ->
            current.copy(
                draftVotesByScope = current.draftVotesByScope + (scope to draftVotes.toMap())
            )
        }
    }

    override fun setEligibility(eligibility: VotingEligibility) {
        mutableState.update { current -> current.copy(eligibility = eligibility) }
    }

    override fun toggleDraftVote(
        accountUuid: String,
        roundId: String,
        proposalId: Int,
        optionId: Int
    ) {
        val scope = VotingSessionScope(accountUuid, roundId)
        mutableState.update { current ->
            val updatedDrafts = current.draftVotesFor(accountUuid, roundId).toMutableMap()
            if (updatedDrafts[proposalId] == optionId) {
                updatedDrafts.remove(proposalId)
            } else {
                updatedDrafts[proposalId] = optionId
            }

            current.copy(
                draftVotesByScope = current.draftVotesByScope + (scope to updatedDrafts)
            )
        }
    }

    override fun abstainUnanswered(
        accountUuid: String,
        roundId: String,
        proposals: List<Proposal>
    ) {
        val scope = VotingSessionScope(accountUuid, roundId)
        mutableState.update { current ->
            val updatedDrafts = current.draftVotesFor(accountUuid, roundId).toMutableMap()

            proposals.forEach { proposal ->
                if (updatedDrafts.containsKey(proposal.id)) {
                    return@forEach
                }

                updatedDrafts[proposal.id] = proposal.abstainOptionId()
            }

            current.copy(
                draftVotesByScope = current.draftVotesByScope + (scope to updatedDrafts)
            )
        }
    }

    override fun clearDraftVote(
        accountUuid: String,
        roundId: String,
        proposalId: Int
    ) {
        val scope = VotingSessionScope(accountUuid, roundId)
        mutableState.update { current ->
            val updatedDrafts = current.draftVotesFor(accountUuid, roundId) - proposalId

            current.copy(
                draftVotesByScope = current.draftVotesByScope + (scope to updatedDrafts)
            )
        }
    }

    override fun markRoundSubmitted(
        accountUuid: String,
        roundId: String,
        proposalCount: Int
    ) {
        val scope = VotingSessionScope(accountUuid, roundId)
        mutableState.update { current ->
            current.copy(
                submittedRoundsByScope = current.submittedRoundsByScope + (scope to proposalCount)
            )
        }
    }

    override fun clearDraftVotes(
        accountUuid: String,
        roundId: String
    ) {
        val scope = VotingSessionScope(accountUuid, roundId)
        mutableState.update { current ->
            current.copy(draftVotesByScope = current.draftVotesByScope - scope)
        }
    }

    override fun clear() {
        mutableState.value = VotingSessionStoreState()
    }
}
