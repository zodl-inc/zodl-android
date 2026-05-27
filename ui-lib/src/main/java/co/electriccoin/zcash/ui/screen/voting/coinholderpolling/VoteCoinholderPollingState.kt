package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.VoteTrustIndicator

@Immutable
data class VoteCoinholderPollingState(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onConfigSettings: () -> Unit,
    val pastRounds: List<VotePollCardState>? = null,
    val activeRounds: List<VotePollCardState>? = null,
    val configErrorSheet: ZashiConfirmationState? = null,
    val unverifiedPollWarningSheet: ZashiConfirmationState? = null,
) {
    companion object {
        val preview =
            VoteCoinholderPollingState(
                activeRounds = listOf(VotePollCardState.preview),
                pastRounds = emptyList(),
                onBack = {},
                onRefresh = {},
                onConfigSettings = {},
            )
    }
}

enum class VotePollCardStatus {
    ACTIVE,
    VOTED,
    CLOSED
}

@Immutable
data class VotePollCardState(
    val roundId: String,
    val roundNumber: Int,
    val title: StringResource,
    val description: StringResource,
    val status: VotePollCardStatus,
    val sessionStatus: SessionStatus,
    val isActionEnabled: Boolean,
    val dateLabel: StringResource,
    val trustIndicator: VoteTrustIndicator?,
    val votedLabel: StringResource?,
    val proposalCount: Int,
    val votedCount: Int,
    val onAction: () -> Unit,
) {
    companion object {
        val preview =
            VotePollCardState(
                roundId = "preview-round-1",
                roundNumber = 1,
                title = stringRes("ZF Grant Funding — Q3 2026"),
                description = stringRes("Shielded vote on the allocation of Zcash Foundation grant funds."),
                status = VotePollCardStatus.ACTIVE,
                sessionStatus = SessionStatus.ACTIVE,
                isActionEnabled = true,
                dateLabel = stringRes("Closes May 15"),
                trustIndicator = VoteTrustIndicator.ZODL,
                votedLabel = null,
                proposalCount = 2,
                votedCount = 0,
                onAction = {},
            )
    }
}
