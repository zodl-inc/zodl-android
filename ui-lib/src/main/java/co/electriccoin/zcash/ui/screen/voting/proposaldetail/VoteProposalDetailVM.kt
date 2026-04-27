package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.VotingSessionRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
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

    init {
        roundLce.execute {
            getAllRounds().firstOrNull { it.id == args.roundId }
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
                title = stringRes("Proposal unavailable"),
                message = stringRes("Could not load proposal. Please try again."),
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

        return VoteProposalDetailState(
            positionLabel = stringRes("$position OF $total"),
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            forumUrl = proposal.forumUrl,
            options = buildOptions(proposal, selectedIdx),
            isLocked = false,
            isEditingFromReview = args.isEditingFromReview,
            showUnansweredSheet = showSheet,
            unansweredCount = unansweredCount,
            onBack = ::onBack,
            onNext = { onNext(proposals, proposalIndex, drafts) },
            onConfirmUnanswered = { onConfirmUnanswered(round) },
            onDismissUnanswered = { showUnansweredSheet.value = false },
        )
    }

    private fun buildOptions(proposal: Proposal, selectedIdx: Int?): List<VoteVoteOptionRowState> {
        val options = proposal.options.toMutableList()
        val hasAbstain = options.any { it.label.lowercase().contains("abstain") }
        if (!hasAbstain) {
            val nextIndex = (options.maxOfOrNull { it.id } ?: 0) + 1
            options += VoteOption(id = nextIndex, label = "Abstain")
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

    private fun onBack() = navigationRouter.back()

    private fun onNext(proposals: List<Proposal>, currentIndex: Int, drafts: Map<Int, Int>) {
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

private fun VoteOption.toVoteVoteOptionColor(total: Int): VoteVoteOptionColor {
    if (label.lowercase().contains("abstain")) return VoteVoteOptionColor.ABSTAIN
    return when (total) {
        1 -> {
            VoteVoteOptionColor.SUPPORT
        }

        2 -> {
            if (id == 0) VoteVoteOptionColor.SUPPORT else VoteVoteOptionColor.OPPOSE
        }

        else -> {
            when (id % 3) {
                0 -> VoteVoteOptionColor.SUPPORT
                1 -> VoteVoteOptionColor.OPPOSE
                else -> VoteVoteOptionColor.OTHER
            }
        }
    }
}
