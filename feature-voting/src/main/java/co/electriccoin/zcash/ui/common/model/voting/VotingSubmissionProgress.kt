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
     * counting through one fixed sequence), so this only names which step fired ([step], e.g.
     * `"StepSelected"`/`"StepFinished"`) plus its full debug text ([detail]) rather than a
     * progress fraction.
     */
    data class RunningRound(
        val step: String,
        val detail: String
    ) : VotingSubmissionProgress
}

data class VotingSubmissionResult(
    val submittedProposalCount: Int
)
