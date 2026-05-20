package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

sealed class VoteSubmissionStatus {
    data object Idle : VoteSubmissionStatus()

    data object LocalAuthorizing : VoteSubmissionStatus()

    data class Authorizing(
        val progress: Float
    ) : VoteSubmissionStatus()

    data class Submitting(
        val current: Int,
        val total: Int,
        val progress: Float
    ) : VoteSubmissionStatus()

    data object Completed : VoteSubmissionStatus()

    data class LocalAuthFailed(
        val error: String?
    ) : VoteSubmissionStatus()

    data class ProtocolAuthFailed(
        val error: String?
    ) : VoteSubmissionStatus()

    data class SubmissionFailed(
        val error: String?,
        val defaultError: StringResource? = null,
    ) : VoteSubmissionStatus()
}

internal fun VoteSubmissionStatus.isInFlight() =
    this is VoteSubmissionStatus.LocalAuthorizing ||
        this is VoteSubmissionStatus.Authorizing ||
        this is VoteSubmissionStatus.Submitting

internal fun VoteSubmissionStatus.isFailure() =
    this is VoteSubmissionStatus.LocalAuthFailed ||
        this is VoteSubmissionStatus.ProtocolAuthFailed ||
        this is VoteSubmissionStatus.SubmissionFailed

@Immutable
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
) {
    companion object {
        val preview =
            VoteConfirmSubmissionState(
                status = VoteSubmissionStatus.Idle,
                roundTitle = stringRes("ZF Grant Funding — Q3 2026"),
                votingWeightZEC = stringRes("12.345 ZEC"),
                hotkeyAddress = stringRes("u1abc...xyz"),
                isKeystoneUser = false,
                includesAuthorizationProgress = false,
                memo = stringRes("Round 1 · 2 proposals"),
                ctaButton = ButtonState.preview,
                errorSheet = null,
                onBack = {},
            )
    }
}
