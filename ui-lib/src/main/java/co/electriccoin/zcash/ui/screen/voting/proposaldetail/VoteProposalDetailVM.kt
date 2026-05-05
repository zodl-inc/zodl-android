package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.VotingSessionRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.VoteOptionLabels
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class VoteProposalDetailVM(
    private val args: VoteProposalDetailArgs,
    private val getAllRounds: GetAllVotingRoundsUseCase,
    private val votingSession: VotingSessionRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val showUnansweredSheet = MutableStateFlow(false)
    private val roundLce = mutableLce<VotingRound?>()

    // Captured before any edits so Cancel can restore it.
    private var originalDraftOption: Int? = null

    init {
        roundLce.execute {
            val round = getAllRounds().firstOrNull { it.id == args.roundId }
            if (args.isEditingFromReview) {
                originalDraftOption = votingSession.draftVotes.value[args.proposalId]
            }
            round
        }
    }

    val state: StateFlow<LceState<VoteProposalDetailState>> =
        combine(
            roundLce.state.map { it.success },
            votingSession.draftVotes,
            showUnansweredSheet,
        ) { round, drafts, showSheet ->
            round?.let { createState(it, drafts, showSheet) }
        }.withLce(groupLce(roundLce)) {
            errorStateMapper.mapToState(
                error = it,
                title = stringRes(R.string.vote_error_proposal_unavailable_title),
                message = stringRes(R.string.vote_error_proposal_unavailable_message),
                primaryStyle = ButtonStyle.PRIMARY,
            )
        }.stateIn(this)

    private fun createState(
        round: VotingRound,
        drafts: Map<Int, Int>,
        showSheet: Boolean,
    ): VoteProposalDetailState {
        val proposals = round.proposals
        val proposalIndex = proposals.indexOfFirst { it.id == args.proposalId }
        val proposal = proposals.getOrNull(proposalIndex) ?: proposals.first()
        val total = proposals.size
        val position = if (proposalIndex >= 0) proposalIndex + 1 else 1
        val selectedIdx = drafts[proposal.id]
        val unansweredCount = proposals.count { !drafts.containsKey(it.id) }
        val pollEnded = round.status != SessionStatus.ACTIVE

        return VoteProposalDetailState(
            positionLabel = stringRes(R.string.vote_proposal_position, position, total),
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            forumUrl = proposal.forumUrl,
            options = buildOptions(proposal, selectedIdx),
            isLocked = pollEnded,
            isEditingFromReview = args.isEditingFromReview,
            showUnansweredSheet = showSheet && !pollEnded,
            unansweredCount = unansweredCount,
            showPollEndedSheet = pollEnded,
            onBack = ::onBack,
            onNext = { onNext(proposals, proposalIndex, drafts) },
            onConfirmUnanswered = { onConfirmUnanswered(round) },
            onDismissUnanswered = { showUnansweredSheet.value = false },
            onPollEndedClose = ::onBack,
            onPollEndedViewResults = { navigationRouter.forward(VoteResultsArgs(roundIdHex = round.id)) },
        )
    }

    private fun buildOptions(proposal: Proposal, selectedIdx: Int?): List<VoteVoteOptionRowState> {
        val options = proposal.options.toMutableList()
        val hasAbstain = options.any { it.label.lowercase().contains(VoteOptionLabels.ABSTAIN) }
        if (!hasAbstain) {
            val nextIndex = (options.maxOfOrNull { it.id } ?: 0) + 1
            options += VoteOption(id = nextIndex, label = VoteOptionLabels.ABSTAIN.replaceFirstChar { it.uppercase() })
        }
        val total = options.size
        return options.map { option ->
            VoteVoteOptionRowState(
                index = option.id,
                label = stringRes(option.label),
                color = option.toVoteVoteOptionColor(total),
                isSelected = selectedIdx == option.id,
                isLocked = false,
                onSelect = { votingSession.toggleDraft(proposal.id, option.id) },
            )
        }
    }

    private fun onBack() {
        if (args.isEditingFromReview) {
            // Cancel — restore the original draft selection
            votingSession.setDraft(args.proposalId, originalDraftOption)
        }
        navigationRouter.back()
    }

    private fun onNext(proposals: List<Proposal>, currentIndex: Int, drafts: Map<Int, Int>) {
        // When editing from review, Save just commits (already applied) and returns to Review
        if (args.isEditingFromReview) {
            navigationRouter.back()
            return
        }
        val isLast = currentIndex == proposals.lastIndex
        if (!isLast) {
            val next = proposals[currentIndex + 1]
            navigationRouter.forward(
                VoteProposalDetailArgs(proposalId = next.id, roundId = args.roundId)
            )
            return
        }
        // Last question — check if all drafted
        val allDrafted = proposals.all { drafts.containsKey(it.id) }
        if (allDrafted) {
            navigationRouter.forward(
                VoteProposalListArgs(roundId = args.roundId, isReviewMode = true)
            )
        } else {
            showUnansweredSheet.value = true
        }
    }

    private fun onConfirmUnanswered(round: VotingRound) {
        showUnansweredSheet.value = false
        votingSession.abstainUnanswered(round.proposals)
        navigationRouter.forward(
            VoteProposalListArgs(roundId = round.id, isReviewMode = true)
        )
    }
}

private const val BINARY_OPTION_COUNT = 2
private const val COLOR_CYCLE = 3

private fun VoteOption.toVoteVoteOptionColor(total: Int): VoteVoteOptionColor {
    if (label.lowercase().contains(VoteOptionLabels.ABSTAIN)) return VoteVoteOptionColor.ABSTAIN
    return when (total) {
        1 -> {
            VoteVoteOptionColor.SUPPORT
        }

        BINARY_OPTION_COUNT -> {
            if (id == 0) VoteVoteOptionColor.SUPPORT else VoteVoteOptionColor.OPPOSE
        }

        else -> {
            when (id % COLOR_CYCLE) {
                0 -> VoteVoteOptionColor.SUPPORT
                1 -> VoteVoteOptionColor.OPPOSE
                else -> VoteVoteOptionColor.OTHER
            }
        }
    }
}
