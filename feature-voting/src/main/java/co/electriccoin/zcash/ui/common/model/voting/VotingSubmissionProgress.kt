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
     * [totalProposals] is the run's own stable count from its first plan (see
     * `VotingRoundWorkTally`'s doc comment); `null` until the first `PlanRefreshed` event
     * arrives. [completedProposals] is an ESTIMATE from `VotingRoundProgressTracker`, not the
     * crate's own authoritative tally directly -- the authoritative count only updates in
     * batches (observed live: it can sit at 0 for well over a minute, then jump straight to the
     * full total), so this blends in locally-measured per-proposal progress to move sooner.
     * Reaching full proof progress on a proposal's `CastVote` means that vote was DISPATCHED,
     * not chain-confirmed, so this estimate can (harmlessly) run ahead of what the crate later
     * confirms -- it never falls behind the authoritative count, and never claims the round is
     * fully done before the crate's own tally says so. [proofProgress] is a 0..1 fraction of the
     * ROUND'S total remaining work (not a single step's own fraction), built the same way --
     * summing every proposal currently in flight so the bar moves visibly even in a
     * many-proposal round, rather than tracking only the slowest bundle of a single proposal.
     * Both are `null` until any progress has been measured at all; callers may show an
     * indeterminate indicator in that case. A `CastVote` *step*'s own bundle/proposal id was
     * deliberately dropped from this model entirely: it names whichever of several
     * concurrently-interleaved bundles happened to report last and flickers between bundles with
     * and without a proposal id -- see `VotingRoundDriveProgress.step`'s own doc comment.
     * [currentProposalId], in contrast, is safe to show: it comes from the crate's real per-draft
     * `VoteCommit` progress payload (`VotingRoundDriveProgress.voteCommitProposalId`), which
     * `VotingRoundProgressTracker.currentProposalId()` exposes as "whichever proposal's proof the
     * crate is actually working on right now" -- proofs are serialized one at a time
     * (`max_proof_concurrency: 1`), so this does not flicker the way the step-level id does.
     * `null` until the first such payload arrives; never regresses to `null` afterward.
     */
    data class RunningRound(
        val completedProposals: Int?,
        val totalProposals: Int?,
        val proofProgress: Float?,
        val currentProposalId: Int? = null
    ) : VotingSubmissionProgress
}

data class VotingSubmissionResult(
    val submittedProposalCount: Int
)
