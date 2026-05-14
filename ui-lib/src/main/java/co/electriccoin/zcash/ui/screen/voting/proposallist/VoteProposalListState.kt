package co.electriccoin.zcash.ui.screen.voting.proposallist

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import kotlinx.serialization.Serializable

@Serializable
enum class VoteProposalListMode {
    VOTING,
    REVIEW,
    VOTED
}

@Immutable
data class VoteProposalListState(
    val mode: VoteProposalListMode,
    val roundTitle: StringResource,
    val snapshotHeight: Long?,
    val votedCount: Int,
    val totalCount: Int,
    val metaLine: VoteProposalMetaLineState?,
    val description: StringResource?,
    val discussionUrl: String?,
    val onViewMore: (() -> Unit)?,
    val proposals: List<VoteProposalRowState>,
    val ctaButton: ButtonState?,
    val onBack: () -> Unit,
)

@Immutable
data class VoteProposalMetaLineState(
    val leading: StringResource,
    val trailing: StringResource?,
)

@Immutable
data class VoteProposalRowState(
    val id: Int,
    val zipNumber: StringResource?,
    val title: StringResource,
    val description: StringResource,
    val voteBadge: VoteVoteBadgeState?,
    val onClick: () -> Unit,
)

@Immutable
data class VoteVoteBadgeState(
    val label: StringResource,
    val color: VoteOptionDisplayColor,
)
