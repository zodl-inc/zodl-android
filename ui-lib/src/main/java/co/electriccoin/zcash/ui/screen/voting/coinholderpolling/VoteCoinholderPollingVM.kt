package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus.ACTIVE
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus.TALLYING
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.VotingSessionRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteCoinholderPollingVM(
    private val getAllRounds: GetAllVotingRoundsUseCase,
    private val votingSession: VotingSessionRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val roundsLce = mutableLce<List<VotingRound>>()

    init {
        roundsLce.execute { getAllRounds() }
    }

    val state: StateFlow<LceState<VoteCoinholderPollingState>> =
        combine(
            roundsLce.state.map { it.success },
            votingSession.voteRecords,
        ) { rounds, voteRecords ->
            rounds?.let { createState(it, voteRecords) }
        }.withLce(groupLce(roundsLce)) {
            errorStateMapper.mapToState(
                error = it,
                title = stringRes(R.string.vote_error_unable_to_load_polls_title),
                message = stringRes(R.string.vote_error_unable_to_load_polls_message),
                primaryStyle = ButtonStyle.PRIMARY,
            )
        }.stateIn(this)

    private fun createState(
        rounds: List<VotingRound>,
        voteRecords: Map<String, Int>,
    ): VoteCoinholderPollingState {
        // Active (ACTIVE/TALLYING) first, then past — newest first within each group.
        val (activeSrc, pastSrc) = rounds.reversed().partition { it.status == ACTIVE || it.status == TALLYING }
        return VoteCoinholderPollingState(
            items = (activeSrc + pastSrc).map { buildCard(it, voteRecords[it.id]) },
            onBack = ::onBack,
        )
    }

    private fun buildCard(round: VotingRound, votedProposalCount: Int?): VotePollCardState {
        val status =
            when {
                votedProposalCount != null -> VotePollCardStatus.VOTED

                round.status == ACTIVE -> VotePollCardStatus.ACTIVE

                // TALLYING rounds show as CLOSED card (voting is over, results pending)
                else -> VotePollCardStatus.CLOSED
            }
        val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        val dateLabel =
            when (status) {
                VotePollCardStatus.ACTIVE -> "Closes ${formatter.format(round.votingEnd)}"
                VotePollCardStatus.VOTED -> "Closes ${formatter.format(round.votingEnd)}"
                VotePollCardStatus.CLOSED -> "Closed ${formatter.format(round.votingEnd)}"
            }
        val count = votedProposalCount ?: 0
        val total = round.proposals.size
        return VotePollCardState(
            roundId = round.id,
            title = stringRes(round.title),
            description = if (round.description.isNotEmpty()) stringRes(round.description) else stringRes(""),
            status = status,
            dateLabel = stringRes(dateLabel),
            votedLabel = if (votedProposalCount != null) stringRes(R.string.vote_poll_voted_count, count, total) else null,
            proposalCount = total,
            votedCount = count,
            onAction = { onRoundTapped(round.id, status) }
        )
    }

    private fun onRoundTapped(roundId: String, status: VotePollCardStatus) {
        when (status) {
            VotePollCardStatus.ACTIVE -> {
                navigationRouter.forward(VoteProposalListArgs(roundId = roundId))
            }

            VotePollCardStatus.VOTED -> {
                // "View My Votes" — opens proposal list in review mode to show submitted votes
                navigationRouter.forward(VoteProposalListArgs(roundId = roundId, isReviewMode = true))
            }

            VotePollCardStatus.CLOSED -> {
                navigationRouter.forward(VoteProposalListArgs(roundId = roundId))
            }
        }
    }

    private fun onBack() = navigationRouter.back()
}
