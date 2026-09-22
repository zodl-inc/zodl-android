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
 * Also tracks delegation-phase progress separately, per BUNDLE rather than per proposal:
 * [VotingNextStep.Delegate]/[VotingNextStep.AdvanceDelegation]/
 * [VotingNextStep.AdvanceImportedDelegation] carry no proposal id (a bundle packs many
 * proposals' votes together, so delegation is inherently bundle-scoped), but the crate does
 * report `proof_progress` for these steps too. Without this, a round dominated by delegation
 * work for many bundles reports genuinely zero progress signal until the first proposal starts
 * casting -- observed live: "0 of 37" sat motionless, then jumped straight to "37 of 37" at the
 * very end. A delegating bundle's contribution is excluded from the sum once that same bundle
 * index appears in a proposal-scoped (casting) step, so a bundle's progress is never counted
 * twice as it transitions from delegating to casting.
 *
 * Delegation progress is deliberately kept OUT of [estimatedCompletedProposals] entirely --
 * that count only ever moves from real per-proposal signal (see [proposalCompletionFraction]),
 * so it can never claim a proposal is done purely because its bundle is still delegating. It is
 * folded into [fraction] (the visual bar only) at a fixed, capped weight
 * ([DELEGATION_PHASE_WEIGHT]) specifically so that delegation activity alone -- even across every
 * bundle in the round -- can never drive the bar to look fully done.
 *
 * [record] never regresses a single bundle's own stored contribution, and [fraction] never
 * returns a lower value than it has already returned -- both are monotonic for this tracker's
 * lifetime, matching [SubmitVotesUseCase]'s existing per-submission ratchet discipline.
 */
internal class VotingRoundProgressTracker {
    private val bundleProgressByProposal = mutableMapOf<Int, MutableMap<Int, Float>>()
    private val delegationProgressByBundle = mutableMapOf<Int, Float>()
    private var lastFraction = 0f
    private var lastProposalFraction = 0f
    private var lastEstimatedCompleted = 0

    /**
     * Records one step's proving progress. Proposal-scoped steps (see [proposalIdOf]) bucket by
     * proposal id; delegation steps (see [isDelegationStep]) bucket by `bundleIndex` instead,
     * since they carry no proposal id. Any other step kind (e.g. [VotingNextStep.Unknown]) is a
     * safe no-op -- its semantics aren't known to this SDK yet.
     */
    fun record(
        step: VotingNextStep?,
        proofProgress: Float?
    ) {
        if (step == null || proofProgress == null) return
        if (isDelegationStep(step)) {
            val bundleIndex = step.bundleIndex
            delegationProgressByBundle[bundleIndex] =
                maxOf(delegationProgressByBundle[bundleIndex] ?: 0f, proofProgress)
        } else {
            val proposalId = proposalIdOf(step)
            if (proposalId != null) {
                val bundleIndex = step.bundleIndex
                val bundleProgress = bundleProgressByProposal.getOrPut(proposalId) { mutableMapOf() }
                bundleProgress[bundleIndex] = maxOf(bundleProgress[bundleIndex] ?: 0f, proofProgress)
            }
        }
    }

    /**
     * A 0..1 fraction of the round's total work, or `null` when nothing measurable exists yet
     * (callers should show an indeterminate indicator in that case, per Vizor's own "a count
     * that reads as a flicker is worse than no count" rationale, rather than a determinate bar
     * pinned at or near empty).
     *
     * `= `[proposalCompletionFraction]` + `[DELEGATION_PHASE_WEIGHT]`-weighted delegation
     * progress`. The two terms are ADDED, not maxed: a delegating bundle and an already-casting
     * proposal represent non-overlapping units of work happening concurrently on different
     * bundles, so both must count. The weight exists specifically so that delegation progress
     * alone can never push this to 1.0: in the worst case every bundle in the round represents
     * only a single proposal (`numBundles == total`) and every one of them fully delegates with
     * nothing yet cast -- the delegation term still tops out at [DELEGATION_PHASE_WEIGHT] itself
     * (`(total/total) * DELEGATION_PHASE_WEIGHT`), leaving `1 - DELEGATION_PHASE_WEIGHT` of real
     * headroom that only [proposalCompletionFraction] can fill.
     */
    fun fraction(
        completedProposals: Int?,
        totalProposals: Int?
    ): Float? {
        val total = totalProposals?.takeIf { it > 0 } ?: return null
        val combined =
            (proposalCompletionFraction(completedProposals, total) + delegationFraction(total)).coerceIn(0f, 1f)
        lastFraction = maxOf(lastFraction, combined)
        return lastFraction.takeIf { it > 0f }
    }

    /**
     * An ESTIMATE of how many proposals are complete, derived from [proposalCompletionFraction]
     * -- deliberately NOT from [fraction], which also includes delegation-phase progress. A
     * proposal is only ever credited here once real per-proposal signal exists for it (its own
     * bundle(s) proving/casting, or the authoritative tally advancing) -- a bundle that is merely
     * delegating, with nothing cast yet, can move the visual bar a little but must never move
     * this count, since "N of M proposals complete" is a literal claim a user reasonably expects
     * to mean N whole proposals actually finished, not "N/M of the way through the round overall."
     *
     * This is an approximation, not an authoritative count: reaching full per-bundle proof
     * progress on a `CastVote` step means that vote was DISPATCHED, not that it is
     * chain-confirmed (true confirmation is a separate, later `AdvanceVote`/`AdvanceVoteBatch`
     * outcome the wire events don't cleanly expose -- see this class's own investigation notes).
     * A dispatched vote can still be rejected and need a retry, so this estimate can run ahead of
     * what is later confirmed.
     *
     * Two safeguards keep that approximation from ever contradicting the authoritative tally:
     * it never returns [totalProposals] itself until [completedProposals] genuinely reaches it
     * (so it can never claim the round is fully done before the crate confirms that), and it
     * never returns a lower value than [completedProposals] itself (so it can never fall behind
     * the authoritative count either). The returned value never regresses across calls, and is
     * `null` under the same "nothing measured yet" condition as [fraction].
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
        lastProposalFraction = maxOf(lastProposalFraction, proposalCompletionFraction(completedProposals, total))
        if (lastProposalFraction <= 0f) return null
        val estimate = (lastProposalFraction * total).toInt().coerceIn(0, total - 1)
        lastEstimatedCompleted = maxOf(lastEstimatedCompleted, maxOf(estimate, authoritative))
        return lastEstimatedCompleted
    }

    /** The authoritative tally's fraction, or the locally-measured per-proposal fraction -- whichever is further. */
    private fun proposalCompletionFraction(
        completedProposals: Int?,
        total: Int
    ): Float {
        val tallyFraction = (completedProposals ?: 0).toFloat() / total
        val fractionSum =
            bundleProgressByProposal.values.sumOf { bundleProgress ->
                (bundleProgress.values.minOrNull() ?: 0f).toDouble()
            }
        val measuredFraction = fractionSum.toFloat() / total
        return maxOf(measuredFraction, tallyFraction)
    }

    /**
     * [DELEGATION_PHASE_WEIGHT]-weighted sum of every currently-delegating bundle's own progress,
     * excluding any bundle already reflected on the casting side ([bundleProgressByProposal]) so
     * a bundle's progress is never counted on both sides as it transitions from delegating to
     * casting.
     */
    private fun delegationFraction(total: Int): Float {
        val castingBundleIndexes = bundleProgressByProposal.values.flatMapTo(mutableSetOf()) { it.keys }
        val delegationFractionSum =
            delegationProgressByBundle
                .filterKeys { it !in castingBundleIndexes }
                .values
                .sumOf { it.toDouble() }
        return (delegationFractionSum.toFloat() / total) * DELEGATION_PHASE_WEIGHT
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

    private fun isDelegationStep(step: VotingNextStep): Boolean =
        when (step) {
            is VotingNextStep.Delegate -> true
            is VotingNextStep.AdvanceDelegation -> true
            is VotingNextStep.AdvanceImportedDelegation -> true
            else -> false
        }

    private companion object {
        /**
         * How much of a bundle's total round-drive lifecycle delegation is assumed to represent,
         * relative to casting -- a deliberately conservative estimate (the crate exposes no
         * authoritative per-phase timing split), chosen so that delegation progress alone, even
         * across every bundle in the round in the worst case (one bundle per proposal, all fully
         * delegated, nothing yet cast), can never push [fraction] to 1.0 or credit a completed
         * proposal in [estimatedCompletedProposals] -- see both functions' own doc comments.
         */
        const val DELEGATION_PHASE_WEIGHT = 0.5f
    }
}
