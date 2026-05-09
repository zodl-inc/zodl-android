package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class VoteCoinholderPollingState(
    val activeRounds: List<VotePollCardState>,
    val pastRounds: List<VotePollCardState>,
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val configErrorSheet: ZashiConfirmationState? = null,
    val unverifiedPollWarningSheet: ZashiConfirmationState? = null,
)

enum class VotePollCardStatus {
    ACTIVE,
    VOTED,
    CLOSED
}

enum class VotePollTrustIndicator {
    ZODL,
    UNVERIFIED
}

@Immutable
data class VotePollCardState(
    val roundId: String,
    val title: StringResource,
    val description: StringResource,
    val status: VotePollCardStatus,
    val sessionStatus: SessionStatus,
    val isActionEnabled: Boolean,
    val dateLabel: StringResource,
    val trustIndicator: VotePollTrustIndicator?,
    val votedLabel: StringResource?,
    val proposalCount: Int,
    val votedCount: Int,
    val onAction: () -> Unit,
)
