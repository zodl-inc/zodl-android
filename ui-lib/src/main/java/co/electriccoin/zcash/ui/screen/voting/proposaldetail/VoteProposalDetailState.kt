package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.util.StringResource

/**
 * Color category for a vote option.
 * Maps to iOS voteOptionColor() in VotingComponents.swift:
 *   - "abstain" label → ABSTAIN (HyperBlue)
 *   - 2-option: index 0 → SUPPORT, index 1 → OPPOSE
 *   - 3+ options: palette[index] (first two = SUPPORT/OPPOSE)
 */
enum class VoteVoteOptionColor { SUPPORT, OPPOSE, ABSTAIN, OTHER }

@Immutable
data class VoteProposalDetailState(
    val positionLabel: StringResource,
    val title: StringResource,
    val description: StringResource,
    val forumUrl: String?,
    val options: List<VoteVoteOptionRowState>,
    val isLocked: Boolean,
    val isEditingFromReview: Boolean,
    val showUnansweredSheet: Boolean,
    val unansweredCount: Int,
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onConfirmUnanswered: () -> Unit,
    val onDismissUnanswered: () -> Unit,
)

@Immutable
data class VoteVoteOptionRowState(
    val index: Int,
    val label: StringResource,
    val color: VoteVoteOptionColor,
    val isSelected: Boolean,
    val isLocked: Boolean,
    val onSelect: () -> Unit,
)
