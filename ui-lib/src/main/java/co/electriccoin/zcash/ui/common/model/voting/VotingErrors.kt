package co.electriccoin.zcash.ui.common.model.voting

/**
 * Expected voting-flow failures that should surface as retryable or navigable UI states.
 *
 * These values represent missing state, stale replay data, or transient infrastructure failures.
 * They should not be treated as protocol corruption.
 */
sealed interface VotingErrors {
    /**
     * Stable message for logs and fallback UI mapping.
     */
    val userMessage: String

    /**
     * The selected wallet account was unavailable when a voting action started.
     */
    data object NoSelectedAccount : VotingErrors {
        override val userMessage = "No selected wallet account is available"
    }

    /**
     * The wallet is known but cannot vote in the requested round.
     */
    data object Ineligible : VotingErrors {
        override val userMessage = "Wallet is not eligible for this vote"
    }

    /**
     * The wallet has not scanned far enough to prove snapshot-height eligibility.
     */
    data class WalletSyncing(
        val scannedHeight: Long?,
        val snapshotHeight: Long
    ) : VotingErrors {
        override val userMessage =
            "Wallet sync is below the voting snapshot height (${scannedHeight ?: 0}/$snapshotHeight)"
    }

    /**
     * Durable recovery state is missing for a round that is being submitted or resumed.
     */
    data class MissingPreparedRecovery(
        val roundId: String
    ) : VotingErrors {
        override val userMessage = "Voting round $roundId has not been prepared"
    }

    /**
     * Neither recovery storage nor the voting DB can provide the prepared bundle count.
     */
    data class MissingBundleCount(
        val roundId: String
    ) : VotingErrors {
        override val userMessage = "Voting round $roundId has no prepared bundle count"
    }

    /**
     * Submission is resuming after preparation, but the voting hotkey seed is unavailable.
     */
    data class MissingHotkeySeed(
        val roundId: String
    ) : VotingErrors {
        override val userMessage = "Voting round $roundId has no stored hotkey seed"
    }

    /**
     * The resolved service config has no vote server URL to submit against.
     */
    data object MissingVotingServerUrl : VotingErrors {
        override val userMessage = "Voting server URL is not configured"
    }

    /**
     * A submitted transaction was not visible to the vote server within the polling budget.
     */
    data class TxConfirmationTimedOut(
        val txHash: String
    ) : VotingErrors {
        override val userMessage = "Transaction $txHash was not confirmed in time"
    }

    /**
     * The vote commitment tree could not be synchronized from the configured vote server.
     */
    data class VoteTreeSyncFailed(
        val roundId: String
    ) : VotingErrors {
        override val userMessage = "Failed to synchronize vote tree for round $roundId"
    }

    /**
     * A cached vote transaction exists, but the local commitment payload needed for replay is absent.
     */
    data class MissingCachedCommitment(
        val roundId: String,
        val bundleIndex: Int,
        val proposalId: Int
    ) : VotingErrors {
        override val userMessage =
            "Missing stored vote commitment bundle for round $roundId bundle $bundleIndex proposal $proposalId"
    }

    /**
     * A successful SDK or vote-server response was missing required structured data.
     */
    data class UnexpectedSdkResponse(
        val detail: String
    ) : VotingErrors {
        override val userMessage = detail
    }
}

/**
 * Exception wrapper used at coroutine boundaries that already map failures to voting error UI.
 */
class VotingSubmissionRecoverableException(
    val failure: VotingErrors
) : Exception(failure.userMessage)

/**
 * Result of a fast, non-blocking probe for an already-cached transaction hash.
 */
sealed interface TxConfirmationProbeResult {
    data class Confirmed(
        val confirmation: TxConfirmation
    ) : TxConfirmationProbeResult

    data object NotFound : TxConfirmationProbeResult

    data class Rejected(
        val log: String
    ) : TxConfirmationProbeResult
}

/**
 * Replay decision for idempotent vote-submission paths.
 */
sealed interface VotingReplayDecision {
    data object RetryFresh : VotingReplayDecision

    data object ResumeComplete : VotingReplayDecision

    data class RetryLater(
        val reason: VotingErrors
    ) : VotingReplayDecision
}
