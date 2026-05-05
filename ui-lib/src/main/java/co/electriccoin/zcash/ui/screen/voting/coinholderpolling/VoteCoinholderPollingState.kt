package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class VoteCoinholderPollingState(
    val items: List<VotePollCardState>,
    val onBack: () -> Unit,
    val onRefresh: () -> Unit = {},
)

enum class VotePollCardStatus { ACTIVE, VOTED, CLOSED }

@Immutable
data class VotePollCardState(
    val roundId: String,
    val title: StringResource,
    val description: StringResource,
    val status: VotePollCardStatus,
    val dateLabel: StringResource,
    /** e.g. "2 of 3 voted" — null when not yet voted */
    val votedLabel: StringResource?,
    val proposalCount: Int,
    val votedCount: Int,
    val onAction: () -> Unit,
)
