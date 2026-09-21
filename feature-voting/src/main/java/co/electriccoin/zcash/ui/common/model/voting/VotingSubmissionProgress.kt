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
     * One `RoundDriveEvent` fired during [SubmitVotesUseCase]'s `roundSession.run()` call.
     * [completedProposals]/[totalProposals] are the run's own proposal-completion tally (see
     * `VotingRoundWorkTally`'s doc comment) -- a stable "N of M" measured against the run's
     * first plan, unlike a per-event bundle/proposal id, which names whichever of several
     * concurrently-interleaved bundles happened to report last and flickers between bundles
     * with and without a proposal id. `null` until the first `PlanRefreshed` event arrives.
     * [proofProgress] is the 0..1 proving fraction of whichever step most recently reported
     * one, purely to give the progress bar smoother in-between movement; `null` when the most
     * recent event didn't carry one.
     */
    data class RunningRound(
        val completedProposals: Int?,
        val totalProposals: Int?,
        val proofProgress: Float?
    ) : VotingSubmissionProgress
}

data class VotingSubmissionResult(
    val submittedProposalCount: Int
)
