package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.voting.VotingRoundQuiescence
import cash.z.ecc.android.sdk.model.voting.VotingRoundRunReport
import cash.z.ecc.android.sdk.model.voting.VotingRoundStepFailure
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors

/**
 * Maps a completed round-drive run onto a [VotingErrors] case, following Vizor Wallet's proven
 * two-layer pattern for the same crate (see Phase 3 of
 * `2026-09-21-round-driver-production-completion-design.md`): switch exhaustively on the
 * *quiescence* (why the run stopped) first, since that's a small, meaningful enum; fall back to
 * matching [VotingRoundStepFailure.kind]/[VotingRoundStepFailure.message] text only for the
 * generic [VotingRoundQuiescence.Failures] case, since the crate's own failure-kind enum has no
 * stable string form (see that type's own doc comment).
 *
 * Returns `null` for the two continuation/success-shaped quiescences
 * ([VotingRoundQuiescence.NoWorkLeft], [VotingRoundQuiescence.BackgroundShareWorkOnly]) — every
 * other quiescence is a stopping condition the caller must surface.
 *
 * Three quiescences ([VotingRoundQuiescence.Cancelled], [VotingRoundQuiescence.PersistedChainTerminal]
 * / [VotingRoundQuiescence.ChainTerminal], [VotingRoundQuiescence.PassBudgetExhausted]) and one
 * `VotingErrors` case ([VotingErrors.MissingCachedCommitment]) have **no clean 1:1 mapping** onto
 * the pre-4.0 error vocabulary — forcing a semantic match would misrepresent the failure to the
 * user, so these fall through to [VotingErrors.UnexpectedSdkResponse] carrying the quiescence's own
 * raw outcome text, mirroring Vizor's own choice to keep generic text for exactly these kinds of
 * outcomes rather than hand-authoring copy that doesn't fit. [VotingErrors.MissingCachedCommitment]
 * has no round-driver quiescence to trigger it at all (it was specific to the old hand-rolled
 * retry-recovery cache) and stays unreachable — documented here, not silently dropped.
 */
internal fun VotingRoundRunReport.toVotingErrorOrNull(roundId: String): VotingErrors? =
    when (val quiescence = quiescence) {
        is VotingRoundQuiescence.NoWorkLeft,
        is VotingRoundQuiescence.BackgroundShareWorkOnly -> null

        is VotingRoundQuiescence.NeedsBundleSetup ->
            VotingErrors.MissingBundleCount(roundId)

        is VotingRoundQuiescence.NeedsBallot ->
            VotingErrors.OmittedCommittedProposal(
                roundId = roundId,
                proposalId = quiescence.openProposals.firstOrNull()
                    ?: quiescence.unrosteredIntents.first()
            )

        is VotingRoundQuiescence.ChainRecoveryStalled ->
            VotingErrors.TxConfirmationTimedOut(txHash = quiescence.outcome)

        is VotingRoundQuiescence.Failures ->
            failures.firstOrNull()?.toVotingErrorOrDefault(roundId)
                ?: VotingErrors.UnexpectedSdkResponse("Round $roundId reported failures with no detail")

        is VotingRoundQuiescence.PersistedChainTerminal ->
            VotingErrors.UnexpectedSdkResponse("Round $roundId ended in a persisted chain-terminal state")

        is VotingRoundQuiescence.ChainTerminal ->
            VotingErrors.UnexpectedSdkResponse("Round $roundId chain submission ended: ${quiescence.outcome}")

        is VotingRoundQuiescence.NeedsDelegationSignatures ->
            VotingErrors.UnexpectedSdkResponse(
                "Round $roundId needs delegation signatures for bundles ${quiescence.bundles} " +
                    "on a non-Keystone submission path"
            )

        is VotingRoundQuiescence.Cancelled ->
            VotingErrors.UnexpectedSdkResponse("Round $roundId was cancelled")

        is VotingRoundQuiescence.PassBudgetExhausted ->
            VotingErrors.UnexpectedSdkResponse(
                "Round $roundId exhausted its per-dispatch budget with ${quiescence.remaining.size} step(s) remaining"
            )

        is VotingRoundQuiescence.Unknown ->
            VotingErrors.UnexpectedSdkResponse(
                "Round $roundId reached an unrecognized quiescence kind: ${quiescence.kind}"
            )
    }

private fun VotingRoundStepFailure.toVotingErrorOrDefault(roundId: String): VotingErrors {
    val lowerMessage = message.lowercase()
    val lowerKind = kind.lowercase()
    return when {
        "spent" in lowerMessage && "nullifier" in lowerMessage ->
            VotingErrors.TxConfirmationTimedOut(txHash = message)

        "tree" in lowerKind || "sync" in lowerMessage ->
            VotingErrors.VoteTreeSyncFailed(roundId = roundId)

        "commitment" in lowerMessage && "mismatch" in lowerMessage ->
            VotingErrors.RecoveredVoteCommitmentMismatch(
                roundId = roundId,
                bundleIndex = bundleIndex ?: -1,
                proposalId = -1
            )

        "verif" in lowerMessage ->
            VotingErrors.RecoveredVoteVerificationUnavailable(
                roundId = roundId,
                bundleIndex = bundleIndex ?: -1,
                proposalId = -1
            )

        else ->
            VotingErrors.UnexpectedSdkResponse(
                "Round $roundId bundle ${bundleIndex ?: "?"} failure ($kind): $message"
            )
    }
}
