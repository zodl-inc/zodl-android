package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.voting.component.VoteWalletHeaderIconsState

// ─── Submission Status (mirrors iOS batchSubmissionStatus) ───────────────────

sealed class VoteSubmissionStatus {
    /** TX preview — user has not tapped Confirm yet. */
    data object Idle : VoteSubmissionStatus()

    /** Authorization transaction in flight. progress in [0, 1]. */
    data class Authorizing(
        val progress: Float
    ) : VoteSubmissionStatus()

    /** Vote shares being submitted. current is 1-based. */
    data class Submitting(
        val current: Int,
        val total: Int,
        val progress: Float
    ) : VoteSubmissionStatus()

    /** All votes confirmed on-chain. */
    data object Completed : VoteSubmissionStatus()

    /** Something went wrong. */
    data class Failed(
        val error: String
    ) : VoteSubmissionStatus()
}

// ─── State ────────────────────────────────────────────────────────────────────

data class VoteConfirmSubmissionState(
    val status: VoteSubmissionStatus,
    // Static data shown across all states
    val roundTitle: StringResource,
    val votingWeightZEC: StringResource,
    val hotkeyAddress: StringResource,
    val walletHeaderIcons: VoteWalletHeaderIconsState,
    // Idle-only
    val memo: StringResource,
    val ctaButton: ButtonState,
    val onBack: () -> Unit,
)
