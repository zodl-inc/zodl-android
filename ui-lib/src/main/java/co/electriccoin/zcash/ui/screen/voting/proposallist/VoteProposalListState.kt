package co.electriccoin.zcash.ui.screen.voting.proposallist

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
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
    val proposals: List<VoteProposalRowState>?,
    val ctaButton: ButtonState?,
    val onBack: () -> Unit,
    val ineligibleSheet: ZashiConfirmationState? = null,
    val walletSyncingSheet: ZashiConfirmationState? = null,
) {
    companion object {
        val preview =
            VoteProposalListState(
                mode = VoteProposalListMode.VOTING,
                roundTitle = stringRes("ZF Grant Funding — Q3 2026"),
                snapshotHeight = 2_500_000L,
                votedCount = 0,
                totalCount = 2,
                metaLine = VoteProposalMetaLineState.preview,
                description = stringRes("Shielded vote on the allocation of Zcash Foundation grant funds."),
                discussionUrl = null,
                onViewMore = null,
                proposals = listOf(VoteProposalRowState.preview),
                ctaButton = null,
                onBack = {},
            )
    }
}

@Immutable
data class VoteProposalMetaLineState(
    val leading: StringResource,
    val trailing: StringResource?,
) {
    companion object {
        val preview =
            VoteProposalMetaLineState(
                leading = stringRes("Snapshot: block 2,500,000"),
                trailing = null,
            )
    }
}

@Immutable
data class VoteProposalRowState(
    val id: Int,
    val zipNumber: StringResource?,
    val title: StringResource,
    val description: StringResource,
    val voteBadge: VoteVoteBadgeState?,
    val onClick: () -> Unit,
) {
    companion object {
        val preview =
            VoteProposalRowState(
                id = 0,
                zipNumber = stringRes("ZIP-317"),
                title = stringRes("Proposal A — Privacy tooling"),
                description = stringRes("Fund development of privacy tooling for Zcash ecosystem."),
                voteBadge = null,
                onClick = {},
            )
    }
}

@Immutable
data class VoteVoteBadgeState(
    val label: StringResource,
    val color: VoteOptionDisplayColor,
) {
    companion object {
        val preview =
            VoteVoteBadgeState(
                label = stringRes("Yes"),
                color = VoteOptionDisplayColor.SUPPORT,
            )
    }
}
