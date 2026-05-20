package co.electriccoin.zcash.ui.screen.voting.results

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

data class VoteResultsState(
    val roundTitle: StringResource,
    val roundDescription: StringResource,
    val votedMetaLine: StringResource?,
    val proposals: List<VoteProposalResultState>,
    val isLoadingResults: Boolean,
    val doneButton: ButtonState,
    val onBack: () -> Unit,
    val onViewMore: (() -> Unit)?,
)

data class VoteProposalResultState(
    val zipNumber: StringResource?,
    val title: StringResource,
    val description: StringResource,
    val options: List<VoteOptionResultState>,
    val totalZec: StringResource,
    val votedLabel: StringResource?,
)

data class VoteOptionResultState(
    val label: StringResource,
    val amountZec: StringResource,
    val fraction: Float,
    val isWinner: Boolean,
)
