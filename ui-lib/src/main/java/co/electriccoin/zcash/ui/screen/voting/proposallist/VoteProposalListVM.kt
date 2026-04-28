package co.electriccoin.zcash.ui.screen.voting.proposallist

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.VotingSessionRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.VoteOptionLabels
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.polldescription.VotePollDescriptionArgs
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class VoteProposalListVM(
    private val args: VoteProposalListArgs,
    private val getAllRounds: GetAllVotingRoundsUseCase,
    private val votingSession: VotingSessionRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val roundLce = mutableLce<VotingRound?>()

    init {
        roundLce.execute {
            val rounds = getAllRounds()
            if (args.roundId.isNotEmpty()) {
                rounds.firstOrNull { it.id == args.roundId }
            } else {
                rounds.firstOrNull { it.status == SessionStatus.ACTIVE }
            }
        }
    }

    val state: StateFlow<LceState<VoteProposalListState>> =
        combine(
            roundLce.state.map { it.success },
            votingSession.draftVotes,
        ) { round, drafts ->
            round?.let { createState(it, drafts) }
        }.withLce(groupLce(roundLce)) {
            errorStateMapper.mapToState(
                error = it,
                title = stringRes(R.string.vote_error_voting_unavailable_title),
                message = stringRes(R.string.vote_error_voting_unavailable_message),
                primaryStyle = ButtonStyle.PRIMARY,
            )
        }.stateIn(this)

    private fun createState(round: VotingRound, drafts: Map<Int, Int>): VoteProposalListState {
        val mode = if (args.isReviewMode) VoteProposalListMode.REVIEW else VoteProposalListMode.VOTING
        val proposals = round.proposals
        val votedCount = proposals.count { drafts.containsKey(it.id) }

        return VoteProposalListState(
            mode = mode,
            roundTitle = stringRes(round.title),
            snapshotHeight = round.snapshotHeight.takeIf { it > 0 },
            votedCount = votedCount,
            totalCount = proposals.size,
            metaLine = if (mode == VoteProposalListMode.VOTING) buildMetaLine(round) else null,
            description = round.description.takeIf { it.isNotEmpty() }?.let { stringRes(it) },
            onViewMore =
                round.description.takeIf { it.isNotEmpty() }?.let {
                    {
                        navigationRouter.forward(
                            VotePollDescriptionArgs(
                                title = round.title,
                                description = it,
                                discussionUrl = round.discussionUrl,
                            )
                        )
                    }
                },
            proposals = proposals.map { buildProposalRow(it, drafts) },
            ctaButton = buildCtaButton(mode, proposals, drafts, round.id),
            onBack = ::onBack,
        )
    }

    private fun buildProposalRow(proposal: Proposal, drafts: Map<Int, Int>): VoteProposalRowState {
        val draftIdx = drafts[proposal.id]
        val badge = draftIdx?.let { buildVoteBadge(proposal, it) }
        return VoteProposalRowState(
            id = proposal.id,
            zipNumber = proposal.zipNumber?.let { stringRes(it) },
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            voteBadge = badge,
            onClick = { onProposalTapped(proposal.id) },
        )
    }

    private fun buildVoteBadge(proposal: Proposal, optionIndex: Int): VoteVoteBadgeState {
        val option = proposal.options.firstOrNull { it.id == optionIndex }
        val label = option?.label ?: VoteOptionLabels.ABSTAIN.replaceFirstChar { it.uppercase() }
        val type =
            when {
                label.lowercase().contains(VoteOptionLabels.SUPPORT) -> VoteVoteBadgeType.SUPPORT
                label.lowercase().contains(VoteOptionLabels.OPPOSE) -> VoteVoteBadgeType.OPPOSE
                else -> VoteVoteBadgeType.ABSTAIN
            }
        return VoteVoteBadgeState(stringRes(label), type)
    }

    private fun buildCtaButton(
        mode: VoteProposalListMode,
        proposals: List<Proposal>,
        drafts: Map<Int, Int>,
        roundId: String,
    ): ButtonState? {
        if (mode == VoteProposalListMode.REVIEW) {
            return ButtonState(
                text = stringRes(R.string.vote_proposal_list_confirm_submit),
                style = ButtonStyle.PRIMARY,
                onClick = { onConfirmSubmit(roundId, drafts) }
            )
        }
        if (proposals.isEmpty()) return null
        val draftCount = proposals.count { drafts.containsKey(it.id) }
        val firstUnanswered = proposals.firstOrNull { !drafts.containsKey(it.id) }
        return when {
            draftCount == 0 -> {
                ButtonState(
                    text = stringRes(R.string.vote_proposal_list_start_voting),
                    style = ButtonStyle.PRIMARY,
                    onClick = { onProposalTapped(proposals.first().id) }
                )
            }

            draftCount < proposals.size -> {
                ButtonState(
                    text = stringRes(R.string.vote_proposal_list_continue_voting),
                    style = ButtonStyle.PRIMARY,
                    onClick = { firstUnanswered?.let { onProposalTapped(it.id) } }
                )
            }

            else -> {
                ButtonState(
                    text = stringRes(R.string.vote_proposal_list_review_submit),
                    style = ButtonStyle.PRIMARY,
                    onClick = { navigationRouter.forward(VoteProposalListArgs(roundId = roundId, isReviewMode = true)) }
                )
            }
        }
    }

    private fun buildMetaLine(round: VotingRound): StringResource {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
        val now = Instant.now()
        val remaining = ChronoUnit.SECONDS.between(now, round.votingEnd)
        val dateStr = "Ends ${formatter.format(round.votingEnd)}"
        val timeLeft =
            when {
                remaining <= 0 -> "Ended"
                remaining < 3600 -> "${remaining / 60}m left"
                remaining < 86400 -> "${remaining / 3600}h left"
                else -> "${remaining / 86400} day${if (remaining / 86400 == 1L) "" else "s"} left"
            }
        return stringRes(R.string.vote_proposal_list_meta_line, dateStr, timeLeft)
    }

    private fun onProposalTapped(proposalId: Int) {
        val roundId =
            roundLce.state.value.success
                ?.id ?: return
        val isEditingFromReview = args.isReviewMode
        navigationRouter.forward(
            VoteProposalDetailArgs(
                proposalId = proposalId,
                roundId = roundId,
                isEditingFromReview = isEditingFromReview,
            )
        )
    }

    private fun onConfirmSubmit(roundId: String, drafts: Map<Int, Int>) {
        val choicesJson = drafts.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }
        navigationRouter.forward(
            VoteConfirmSubmissionArgs(roundIdHex = roundId, choicesJson = choicesJson)
        )
    }

    private fun onBack() = navigationRouter.back()
}
