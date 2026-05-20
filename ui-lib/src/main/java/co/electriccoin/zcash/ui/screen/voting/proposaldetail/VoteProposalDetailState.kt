package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

@Immutable
data class VoteProposalDetailState(
    val positionLabel: StringResource,
    val title: StringResource,
    val description: StringResource,
    val forumUrl: String?,
    val options: List<VoteVoteOptionRowState>,
    val isLocked: Boolean,
    val isEditingFromReview: Boolean,
    val isFromList: Boolean,
    val showUnansweredSheet: Boolean,
    val unansweredCount: Int,
    val showPollEndedSheet: Boolean,
    val unverifiedPollWarningSheet: ZashiConfirmationState?,
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onViewMore: () -> Unit,
    val onConfirmUnanswered: () -> Unit,
    val onDismissUnanswered: () -> Unit,
    val onPollEndedClose: () -> Unit,
    val onPollEndedViewResults: () -> Unit,
) {
    companion object {
        val preview =
            VoteProposalDetailState(
                positionLabel = stringRes("1 of 2"),
                title = stringRes("Proposal A — Privacy tooling"),
                description = stringRes("Fund development of privacy tooling for the Zcash ecosystem."),
                forumUrl = null,
                options = listOf(VoteVoteOptionRowState.preview),
                isLocked = false,
                isEditingFromReview = false,
                isFromList = true,
                showUnansweredSheet = false,
                unansweredCount = 0,
                showPollEndedSheet = false,
                unverifiedPollWarningSheet = null,
                onBack = {},
                onNext = {},
                onViewMore = {},
                onConfirmUnanswered = {},
                onDismissUnanswered = {},
                onPollEndedClose = {},
                onPollEndedViewResults = {},
            )
    }
}

@Immutable
data class VoteVoteOptionRowState(
    val index: Int,
    val label: StringResource,
    val description: StringResource?,
    val color: VoteOptionDisplayColor,
    val isSelected: Boolean,
    val isLocked: Boolean,
    val onSelect: () -> Unit,
) {
    companion object {
        val preview =
            VoteVoteOptionRowState(
                index = 0,
                label = stringRes("Yes"),
                description = null,
                color = VoteOptionDisplayColor.SUPPORT,
                isSelected = false,
                isLocked = false,
                onSelect = {},
            )
    }
}
