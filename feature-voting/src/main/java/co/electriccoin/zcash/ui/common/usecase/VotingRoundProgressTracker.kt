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
 * crate (`voting_progress_presentation.dart`'s `fractionSum` term) for [fraction], and now also
 * its "finished" bookkeeping for [estimatedCompletedProposals] -- see [recordPlan] and
 * [truePerBundleCompletedCount].
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
    private val planVoteCarryingBundleIndexes = mutableSetOf<Int>()
    private var lastFraction = 0f
    private var lastProposalFraction = 0f
    private var lastEstimatedCompleted = 0

    /**
     * Records one step's proving progress. Proposal-scoped steps (see [proposalIdOf]) bucket by
     * proposal id; delegation steps (see [isDelegationStep]) bucket by `bundleIndex` instead,
     * since they carry no proposal id. Any other step kind (e.g. [VotingNextStep.Unknown]) is a
     * safe no-op -- its semantics aren't known to this SDK yet.
     *
     * [voteCommitProposalId], when non-null, is the crate's own real per-draft identity from a
     * `VoteCommit` progress payload (`VotingRoundDriveProgress.voteCommitProposalId`) -- preferred
     * over [step]'s own (fixed-for-the-whole-batch) proposal id, since a single `CastVote` step
     * casts every draft of its bundle as one unit (`run_cast_vote`'s own doc comment) while this
     * value changes as each draft's own proof actually proceeds. `null` for every step kind that
     * doesn't report one (delegation steps, and any `StepProgress` payload that isn't
     * `VoteCommit`), in which case [step]'s own id is used exactly as before.
     */
    fun record(
        step: VotingNextStep?,
        proofProgress: Float?,
        voteCommitProposalId: Int? = null
    ) {
        if (step == null || proofProgress == null) return
        if (isDelegationStep(step)) {
            val bundleIndex = step.bundleIndex
            delegationProgressByBundle[bundleIndex] =
                maxOf(delegationProgressByBundle[bundleIndex] ?: 0f, proofProgress)
        } else {
            val proposalId = voteCommitProposalId ?: proposalIdOf(step)
            if (proposalId != null) {
                val bundleIndex = step.bundleIndex
                val bundleProgress = bundleProgressByProposal.getOrPut(proposalId) { mutableMapOf() }
                bundleProgress[bundleIndex] = maxOf(bundleProgress[bundleIndex] ?: 0f, proofProgress)
            }
        }
    }

    /**
     * Replaces the tracker's view of one `PlanRefreshed` event's `voteCarryingBundleIndexes`
     * (`VotingRoundDriveProgress.voteCarryingBundleIndexes`) -- every bundle the round's LATEST
     * live plan still owes a vote-family step for. Verified directly against Vizor Wallet's own
     * `voting_resume_plan.dart$voteCarryingBundleIndexes()`: it is a pure function that recomputes
     * fresh from the latest plan on every call, with no persistent accumulator of its own -- its
     * own doc comment states the union with bundles the run has *observed* reporting progress
     * happens at the caller, over that fresh value, not by ever accumulating stale plan snapshots.
     * An earlier version of this function unioned every call's list into a permanent set instead
     * of replacing it, so a bundle the crate later revised OUT of the vote-carrying set (e.g. its
     * delegation ended terminal before it ever cast) stayed counted forever, artificially
     * inflating [proposalCompletionFraction]'s denominator and holding the estimate down. A bundle
     * that finishes casting and *did* report real progress before dropping off the plan is still
     * correctly retained -- via the separate union with [bundleProgressByProposal]'s observed
     * bundle indexes at read time, exactly mirroring Vizor's split. A `null` call (no
     * `PlanRefreshed` event this tick) is a safe no-op and leaves the last-known plan in place; an
     * empty list is a real, current "the plan has no vote-carrying bundles right now" and replaces
     * accordingly.
     */
    fun recordPlan(voteCarryingBundleIndexes: List<Int>?) {
        if (voteCarryingBundleIndexes != null) {
            planVoteCarryingBundleIndexes.clear()
            planVoteCarryingBundleIndexes += voteCarryingBundleIndexes
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
     * alone can never push this to 1.0: even in the worst case, where the wallet's note set
     * produces MORE bundles than there are proposals in the round (bundle count is driven by
     * `ceil(note_count / 5)` in the crate, independent of proposal count -- NOT guaranteed
     * `numBundles <= total`) and every one of them fully delegates with nothing yet cast, the
     * delegation term still tops out at [DELEGATION_PHASE_WEIGHT] itself -- see
     * [delegationFraction]'s own doc comment for the denominator fix that guarantees this --
     * leaving `1 - DELEGATION_PHASE_WEIGHT` of real headroom that only
     * [proposalCompletionFraction] can fill.
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
     * **Many-bundle behavior (revised after live testing on a 13-bundle round):** an earlier
     * version of this required EVERY bundle carrying the ballot to report a proposal at full
     * progress before counting it -- correct in the strict sense, but for a many-bundle round
     * that meant this sat frozen at 0 for nearly the whole run while [fraction] (fed by the same
     * underlying data via [proposalCompletionFraction]) visibly climbed, a contradictory pairing
     * a live tester immediately caught and rejected: the tracker DOES know the real, granular
     * proportion of work done at every moment, so showing a frozen count instead of that known
     * proportion was indefensible, not "expected." This estimate is now [proposalCompletionFraction]
     * itself, scaled to a proposal count -- exactly in step with the bar, using every bundle's
     * real contribution (already correctly weighted by how many of the required bundles have
     * reported) rather than requiring unanimous full-progress agreement first.
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
        // See PROPOSAL_ESTIMATE_EPSILON's own doc comment: absorbs Float round-trip error that
        // would otherwise truncate a genuinely fully-measured proposal count short by one for
        // many non-power-of-2 totals.
        val estimate = (lastProposalFraction * total + PROPOSAL_ESTIMATE_EPSILON).toInt().coerceIn(0, total - 1)
        lastEstimatedCompleted = maxOf(lastEstimatedCompleted, maxOf(estimate, authoritative))
        return lastEstimatedCompleted
    }

    /**
     * The authoritative tally's fraction, or the locally-measured per-proposal fraction --
     * whichever is further.
     *
     * **Many-bundle correctness (added after a live 13-bundle test showed this bar filling to
     * 100% once only 2 of 13 bundles had raced through every proposal, while
     * [estimatedCompletedProposals] correctly still read "0 of 37"):** each proposal's own
     * contribution used to be the minimum progress among whichever bundles happened to have
     * reported on it SO FAR -- correct when that set is every bundle carrying the ballot, wrong
     * once it is a small subset of a much larger required count, since bundles that have not
     * reported yet were silently absent from the minimum rather than counted as their true (zero)
     * contribution. Scaling that minimum by `reportingBundles / requiredBundles` fixes this while
     * leaving every existing single/few-bundle behavior unchanged: when every carrying bundle has
     * already reported (`reportingBundles == requiredBundles`, the common case), the scale factor
     * is exactly `1` and this reduces to the original minimum-only formula.
     */
    private fun proposalCompletionFraction(
        completedProposals: Int?,
        total: Int
    ): Float {
        val tallyFraction = (completedProposals ?: 0).toFloat() / total
        val observedBundleIndexes = bundleProgressByProposal.values.flatMapTo(mutableSetOf()) { it.keys }
        val requiredBundles = (planVoteCarryingBundleIndexes + observedBundleIndexes).size
        val fractionSum =
            if (requiredBundles <= 0) {
                0.0
            } else {
                bundleProgressByProposal.values.sumOf { bundleProgress ->
                    val minProgress = (bundleProgress.values.minOrNull() ?: 0f).toDouble()
                    (minProgress * bundleProgress.size / requiredBundles).coerceAtMost(1.0)
                }
            }
        val measuredFraction = fractionSum.toFloat() / total
        return maxOf(measuredFraction, tallyFraction)
    }

    /**
     * [DELEGATION_PHASE_WEIGHT]-weighted sum of every currently-delegating bundle's own progress,
     * excluding any bundle already reflected on the casting side ([bundleProgressByProposal]) so
     * a bundle's progress is never counted on both sides as it transitions from delegating to
     * casting.
     *
     * **Denominator (fixed, Important #2 of the final whole-plan review):** this used to divide
     * by bare [total] (a count of PROPOSALS) under the assumption `numBundles <= total`, but that
     * is not actually guaranteed by the crate -- bundle count is driven by the wallet's note set
     * (`ceil(note_count / 5)`, independent of proposal count). A 1-proposal round whose note set
     * produces 2 bundles, both fully delegating with nothing cast, would compute
     * `(2.0/1) * DELEGATION_PHASE_WEIGHT == 1.0` -- the bar would read 100% complete with zero
     * votes cast. Normalizing by `maxOf(total, delegationProgressByBundle.size +
     * castingBundleIndexes.size)` instead -- never smaller than the actual number of bundles
     * being summed -- preserves today's behavior whenever bundles <= proposals (the common case,
     * where this maxOf is a no-op) and caps the delegation term at [DELEGATION_PHASE_WEIGHT]
     * whenever bundle count exceeds proposal count, instead of letting it exceed that cap. Does
     * not change [proposalCompletionFraction]'s own division by [total], nor
     * [estimatedCompletedProposals]'s existing decoupling from delegation entirely -- both keep
     * using the proposal-scoped [total] unchanged.
     */
    private fun delegationFraction(total: Int): Float {
        val castingBundleIndexes = bundleProgressByProposal.values.flatMapTo(mutableSetOf()) { it.keys }
        val delegationFractionSum =
            delegationProgressByBundle
                .filterKeys { it !in castingBundleIndexes }
                .values
                .sumOf { it.toDouble() }
        val bundleCountFloor = delegationProgressByBundle.size + castingBundleIndexes.size
        return (delegationFractionSum.toFloat() / maxOf(total, bundleCountFloor)) * DELEGATION_PHASE_WEIGHT
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

        /**
         * Absorbs Float32 round-trip error in [estimatedCompletedProposals]'s
         * `lastProposalFraction * total` product -- e.g. for `total = 41`, `1f / 41f * 41f`
         * evaluates to `0.9999999f` rather than exactly `1.0f` in single precision, which would
         * otherwise truncate a genuinely fully-measured proposal count one short. Comfortably
         * larger than any realistic Float32 round-trip error yet far too small to round up
         * genuine partial progress into the next whole proposal.
         */
        const val PROPOSAL_ESTIMATE_EPSILON = 1e-4f
    }
}
