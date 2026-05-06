package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.util.StringResource

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
    val showPollEndedSheet: Boolean,
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onConfirmUnanswered: () -> Unit,
    val onDismissUnanswered: () -> Unit,
    val onPollEndedClose: () -> Unit,
    val onPollEndedViewResults: () -> Unit,
)

@Immutable
data class VoteVoteOptionRowState(
    val index: Int,
    val label: StringResource,
    val color: VoteOptionDisplayColor,
    val isSelected: Boolean,
    val isLocked: Boolean,
    val onSelect: () -> Unit,
)
