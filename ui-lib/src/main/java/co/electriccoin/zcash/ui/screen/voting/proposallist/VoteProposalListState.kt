package co.electriccoin.zcash.ui.screen.voting.proposallist

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

enum class VoteProposalListMode { VOTING, REVIEW }

enum class VoteVoteBadgeType { SUPPORT, OPPOSE, ABSTAIN }

@Immutable
data class VoteProposalListState(
    val mode: VoteProposalListMode,
    val roundTitle: StringResource,
    /** Snapshot block height displayed as `#2,800,000` next to the round title. */
    val snapshotHeight: Long?,
    val votedCount: Int,
    val totalCount: Int,
    val metaLine: StringResource?,
    val description: StringResource?,
    val onViewMore: (() -> Unit)?,
    val proposals: List<VoteProposalRowState>,
    val ctaButton: ButtonState?,
    val onBack: () -> Unit,
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
    val type: VoteVoteBadgeType,
)
