package co.electriccoin.zcash.ui.screen.voting.results

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

@Immutable
data class VoteResultsState(
    val roundTitle: StringResource,
    val roundDescription: StringResource,
    val votedMetaLine: StringResource?,
    val proposals: List<VoteProposalResultState>,
    val isLoadingResults: Boolean,
    val doneButton: ButtonState,
    val onBack: () -> Unit,
    val onViewMore: (() -> Unit)?,
) {
    companion object {
        val preview =
            VoteResultsState(
                roundTitle = stringRes("ZF Grant Funding — Q3 2026"),
                roundDescription = stringRes("Shielded vote on the allocation of Zcash Foundation grant funds."),
                votedMetaLine = stringRes("Voted May 10 · 12.345 ZEC"),
                proposals = listOf(VoteProposalResultState.preview),
                isLoadingResults = false,
                doneButton = ButtonState.preview,
                onBack = {},
                onViewMore = null,
            )
    }
}

@Immutable
data class VoteProposalResultState(
    val zipNumber: StringResource?,
    val title: StringResource,
    val description: StringResource,
    val options: List<VoteOptionResultState>,
    val totalZec: StringResource,
    val votedLabel: StringResource?,
) {
    companion object {
        val preview =
            VoteProposalResultState(
                zipNumber = stringRes("ZIP-317"),
                title = stringRes("Proposal A"),
                description = stringRes("Fund development of privacy tooling."),
                options = listOf(VoteOptionResultState.preview),
                totalZec = stringRes("1,234.56 ZEC"),
                votedLabel = null,
            )
    }
}

@Immutable
data class VoteOptionResultState(
    val label: StringResource,
    val amountZec: StringResource,
    val fraction: Float,
    val isWinner: Boolean,
    val color: VoteOptionDisplayColor,
) {
    companion object {
        val preview =
            VoteOptionResultState(
                label = stringRes("Yes"),
                amountZec = stringRes("800.00 ZEC"),
                fraction = 0.65f,
                isWinner = true,
                color = VoteOptionDisplayColor.SUPPORT,
            )
    }
}
