package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource

sealed class VoteSubmissionStatus {
    data object Idle : VoteSubmissionStatus()

    data object LocalAuthorizing : VoteSubmissionStatus()

    data class Authorizing(val progress: Float) : VoteSubmissionStatus()

    data class Submitting(val current: Int, val total: Int, val progress: Float) : VoteSubmissionStatus()

    data object Completed : VoteSubmissionStatus()

    data class LocalAuthFailed(val error: String) : VoteSubmissionStatus()

    data class ProtocolAuthFailed(val error: String) : VoteSubmissionStatus()

    data class SubmissionFailed(val error: String) : VoteSubmissionStatus()
}

internal fun VoteSubmissionStatus.isInFlight() =
    this is VoteSubmissionStatus.LocalAuthorizing ||
        this is VoteSubmissionStatus.Authorizing ||
        this is VoteSubmissionStatus.Submitting

internal fun VoteSubmissionStatus.isFailure() =
    this is VoteSubmissionStatus.LocalAuthFailed ||
        this is VoteSubmissionStatus.ProtocolAuthFailed ||
        this is VoteSubmissionStatus.SubmissionFailed

data class VoteConfirmSubmissionState(
    val status: VoteSubmissionStatus,
    val roundTitle: StringResource,
    val votingWeightZEC: StringResource,
    val hotkeyAddress: StringResource,
    val isKeystoneUser: Boolean,
    val includesAuthorizationProgress: Boolean,
    val memo: StringResource,
    val ctaButton: ButtonState,
    val errorSheet: ZashiConfirmationState?,
    val onBack: () -> Unit,
)
