package co.electriccoin.zcash.ui.common.model.voting

sealed interface VotingSubmissionProgress {
    data class Authorizing(
        val progress: Float
    ) : VotingSubmissionProgress

    data class Submitting(
        val current: Int,
        val total: Int,
        val progress: Float
    ) : VotingSubmissionProgress

    /**
     * One `RoundDriveEvent` fired during [SubmitVotesUseCase]'s `roundSession.run()` call --
     * the round-driver architecture doesn't expose a linear current/total count the way
     * [Submitting] does (`RoundDriveEvent` interleaves several bundles' steps rather than
     * counting through one fixed sequence). [bundleIndex]/[proposalId] name which bundle/
     * proposal the event belongs to (from `VotingRoundDriveProgress.step`, when the event names
     * one), and [proofProgress] is the 0..1 proving fraction when the crate reported one --
     * both `null` when the event doesn't carry that information (e.g. a `PlanRefreshed` or
     * `Delegate` step has no `proposalId`).
     */
    data class RunningRound(
        val bundleIndex: Int?,
        val proposalId: Int?,
        val proofProgress: Float?
    ) : VotingSubmissionProgress
}

data class VotingSubmissionResult(
    val submittedProposalCount: Int
)
