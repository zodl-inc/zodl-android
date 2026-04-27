package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton holding in-memory voting session state shared across
 * ProposalListVM and ProposalDetailVM.
 *
 * Mirrors iOS VotingStore.State fields:
 *   draftVotes: [UInt32: VoteChoice]
 *   votingRound.proposals
 *
 * Toggling the same option deselects it (matches iOS castVote logic).
 */
interface VotingSessionRepository {
    /** Map of proposalId → selected optionIndex (in-memory drafts). */
    val draftVotes: StateFlow<Map<Int, Int>>

    /**
     * Map of roundId → votedProposalCount.
     * Mirrors iOS voteRecords: [String: VoteRecord].
     * In-memory only — persisted storage is a future improvement.
     */
    val voteRecords: StateFlow<Map<String, Int>>

    fun toggleDraft(proposalId: Int, optionIndex: Int)

    fun abstainUnanswered(proposals: List<Proposal>)

    /** Record that the user submitted votes for a round. */
    fun markRoundVoted(roundId: String, proposalCount: Int)

    /** Clear all drafts (keeps vote records). */
    fun clearSession()
}

class VotingSessionRepositoryImpl : VotingSessionRepository {
    private val _draftVotes = MutableStateFlow<Map<Int, Int>>(emptyMap())
    override val draftVotes: StateFlow<Map<Int, Int>> = _draftVotes.asStateFlow()

    private val _voteRecords = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val voteRecords: StateFlow<Map<String, Int>> = _voteRecords.asStateFlow()

    override fun toggleDraft(proposalId: Int, optionIndex: Int) {
        val current = _draftVotes.value
        _draftVotes.value =
            if (current[proposalId] == optionIndex) {
                current - proposalId
            } else {
                current + (proposalId to optionIndex)
            }
    }

    override fun abstainUnanswered(proposals: List<Proposal>) {
        val updated = _draftVotes.value.toMutableMap()
        for (proposal in proposals) {
            if (updated.containsKey(proposal.id)) continue
            val abstainIndex =
                if (proposal.options.any { it.label.lowercase().contains("abstain") }) {
                    proposal.options.first { it.label.lowercase().contains("abstain") }.id
                } else {
                    (proposal.options.maxOfOrNull(VoteOption::id) ?: 0) + 1
                }
            updated[proposal.id] = abstainIndex
        }
        _draftVotes.value = updated
    }

    override fun markRoundVoted(roundId: String, proposalCount: Int) {
        _voteRecords.value = _voteRecords.value + (roundId to proposalCount)
    }

    override fun clearSession() {
        _draftVotes.value = emptyMap()
    }
}
