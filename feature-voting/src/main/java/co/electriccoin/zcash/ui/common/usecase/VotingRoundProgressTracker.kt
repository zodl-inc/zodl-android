package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.voting.VotingNextStep

/**
 * Tracks round-drive proving progress per PROPOSAL (each proposal's own fraction being the
 * minimum across that proposal's own bundles) rather than per bundle, so the visual fraction
 * accounts for every proposal currently in flight at once -- not just the slowest bundle of a
 * single proposal. The round driver interleaves many proposals/bundles concurrently, so summing
 * every in-flight proposal's own partial credit moves the bar meaningfully even in a
 * many-proposal round, where a single proposal's own contribution is a tiny fraction of the
 * total. Mirrors the core idea of Vizor Wallet's `chp-benchmark-v5main` integration for the same
 * crate (`voting_progress_presentation.dart`'s `fractionSum` term) without replicating its exact
 * "finished" bookkeeping.
 *
 * [record] never regresses a single bundle's own stored contribution, and [fraction] never
 * returns a lower value than it has already returned -- both are monotonic for this tracker's
 * lifetime, matching [SubmitVotesUseCase]'s existing per-submission ratchet discipline.
 */
internal class VotingRoundProgressTracker {
    private val bundleProgressByProposal = mutableMapOf<Int, MutableMap<Int, Float>>()
    private var lastFraction = 0f
    private var lastEstimatedCompleted = 0

    /** Records one step's proving progress. A safe no-op for steps with no [proposalId][proposalIdOf]. */
    fun record(
        step: VotingNextStep?,
        proofProgress: Float?
    ) {
        if (step == null || proofProgress == null) return
        val proposalId = proposalIdOf(step) ?: return
        val bundleIndex = step.bundleIndex
        val bundleProgress = bundleProgressByProposal.getOrPut(proposalId) { mutableMapOf() }
        bundleProgress[bundleIndex] = maxOf(bundleProgress[bundleIndex] ?: 0f, proofProgress)
    }

    /**
     * A 0..1 fraction of the round's total work, or `null` when nothing measurable exists yet
     * (callers should show an indeterminate indicator in that case, per Vizor's own "a count
     * that reads as a flicker is worse than no count" rationale, rather than a determinate bar
     * pinned at or near empty).
     */
    fun fraction(
        completedProposals: Int?,
        totalProposals: Int?
    ): Float? {
        val total = totalProposals?.takeIf { it > 0 } ?: return null
        val tallyFraction = (completedProposals ?: 0).toFloat() / total
        val fractionSum =
            bundleProgressByProposal.values.sumOf { bundleProgress ->
                (bundleProgress.values.minOrNull() ?: 0f).toDouble()
            }
        val measuredFraction = fractionSum.toFloat() / total
        val combined = maxOf(measuredFraction, tallyFraction).coerceIn(0f, 1f)
        lastFraction = maxOf(lastFraction, combined)
        return lastFraction.takeIf { it > 0f }
    }

    /**
     * An ESTIMATE of how many proposals are complete, derived from [fraction] rather than
     * waiting for the crate's own batched tally -- moves as soon as any proposal's own bundles
     * are cast/proven, not only when the crate confirms a batch. This is an approximation, not
     * an authoritative count: reaching full per-bundle proof progress on a `CastVote` step means
     * that vote was DISPATCHED, not that it is chain-confirmed (true confirmation is a separate,
     * later `AdvanceVote`/`AdvanceVoteBatch` outcome the wire events don't cleanly expose -- see
     * this class's own investigation notes). A dispatched vote can still be rejected and need a
     * retry, so this estimate can run ahead of what is later confirmed.
     *
     * Two safeguards keep that approximation from ever contradicting the authoritative tally:
     * it never returns [totalProposals] itself until [completedProposals] genuinely reaches it
     * (so it can never claim the round is fully done before the crate confirms that), and it
     * never returns a lower value than [completedProposals] itself (so it can never fall behind
     * the authoritative count either). Like [fraction], the returned value never regresses across
     * calls, and is `null` under the same "nothing measurable yet" condition.
     */
    fun estimatedCompletedProposals(
        completedProposals: Int?,
        totalProposals: Int?
    ): Int? {
        val total = totalProposals?.takeIf { it > 0 } ?: return null
        val authoritative = (completedProposals ?: 0).coerceAtMost(total)
        if (authoritative >= total) {
            lastEstimatedCompleted = total
            return total
        }
        val fraction = fraction(completedProposals, totalProposals) ?: return null
        val estimate = (fraction * total).toInt().coerceIn(0, total - 1)
        lastEstimatedCompleted = maxOf(lastEstimatedCompleted, maxOf(estimate, authoritative))
        return lastEstimatedCompleted
    }

    private fun proposalIdOf(step: VotingNextStep?): Int? =
        when (step) {
            is VotingNextStep.CastVote -> step.proposalId
            is VotingNextStep.AdvanceVote -> step.proposalId
            is VotingNextStep.AdvanceVoteBatch -> step.proposalId
            is VotingNextStep.SubmitShares -> step.proposalId
            is VotingNextStep.ConfirmShare -> step.proposalId
            else -> null
        }
}
