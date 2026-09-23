package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.voting.VotingNextStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VotingRoundProgressTrackerTest {
    /** Mirrors the private `DELEGATION_PHASE_WEIGHT` constant in the class under test. */
    private val delegationPhaseWeight = 0.5f

    @Test
    fun `fraction is null when total is unknown`() {
        val tracker = VotingRoundProgressTracker()

        assertNull(tracker.fraction(completedProposals = null, totalProposals = null))
        assertNull(tracker.fraction(completedProposals = 0, totalProposals = null))
    }

    @Test
    fun `fraction is null when nothing measured and tally is genuinely zero`() {
        val tracker = VotingRoundProgressTracker()

        assertNull(tracker.fraction(completedProposals = 0, totalProposals = 37))
    }

    @Test
    fun `fraction is null when nothing measured and tally is not yet known`() {
        val tracker = VotingRoundProgressTracker()

        assertNull(tracker.fraction(completedProposals = null, totalProposals = 37))
    }

    @Test
    fun `a single recorded step produces a positive fraction`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 0.5f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals(0.5f / 37f, fraction)
    }

    @Test
    fun `distinct in-flight proposals sum rather than only tracking the slowest one`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 2), proofProgress = 0.5f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals((1.0f + 0.5f) / 37f, fraction)
    }

    @Test
    fun `multiple bundles on the same proposal use the slowest bundle for that proposal`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 1, proposalId = 1), proofProgress = 0.2f)

        // Proposal 1's own fraction is min(1.0, 0.2) = 0.2, not 1.2 and not summed across bundles.
        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals(0.2f / 37f, fraction)
    }

    @Test
    fun `per-bundle progress within a proposal is monotonic`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 0.8f)
        // A later, lower value for the SAME bundle (a fresh proving pass restarting) must not
        // drag that bundle's stored contribution back down.
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 0.1f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals(0.8f / 37f, fraction)
    }

    @Test
    fun `the combined fraction never regresses across calls`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 2), proofProgress = 1.0f)
        val high = tracker.fraction(completedProposals = 0, totalProposals = 37)
        checkNotNull(high)

        // A brand-new bundle (proposal 3) reports in low -- the per-proposal SUM would only ever
        // grow from this, but simulate a case where the tally/measured mix would otherwise dip
        // (e.g. a later proposal's own bundle count momentarily lowers the per-proposal minimum)
        // by directly asserting the tracker's own ratchet holds even if an internal recomputation
        // produces a smaller instantaneous value than what was already reported.
        tracker.record(castVote(bundleIndex = 0, proposalId = 3), proofProgress = 0f)
        val next = tracker.fraction(completedProposals = 0, totalProposals = 37)
        checkNotNull(next)
        assertTrue(next >= high, "fraction regressed from $high to $next")
    }

    @Test
    fun `tally alone (no recorded steps) still produces a fraction once proposals are completed`() {
        val tracker = VotingRoundProgressTracker()

        val fraction = tracker.fraction(completedProposals = 10, totalProposals = 37)
        assertEquals(10f / 37f, fraction)
    }

    @Test
    fun `the measured fraction wins when it exceeds the tally fraction`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 2), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 3), proofProgress = 1.0f)

        // measured = 3/37, tally says only 1/37 -- measured should win since it's further along.
        val fraction = tracker.fraction(completedProposals = 1, totalProposals = 37)
        assertEquals(3f / 37f, fraction)
    }

    // --- delegation-phase progress (bundle-scoped, no proposal id) ---
    //
    // Delegate/AdvanceDelegation/AdvanceImportedDelegation steps carry no proposal id -- a bundle
    // packs many proposals' votes together, so delegation is inherently bundle-scoped -- but the
    // crate DOES report proof_progress for them (confirmed against the vendored crate's
    // RoundStepProgressView doc comment, and independently confirmed this SDK's own
    // parseRoundDriveProgress mapper already forwards proof_progress generically regardless of
    // step kind). Without tracking this, a round dominated by delegation work for many bundles
    // reports genuinely zero progress signal until the first proposal starts casting -- this was
    // observed live: "0 of 37" sat motionless, then jumped straight to "37 of 37" at the end.

    @Test
    fun `a Delegate step's progress contributes via bundle-scoped tracking, at the delegation phase weight`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 0.5f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals((0.5f / 37f) * delegationPhaseWeight, fraction)
    }

    @Test
    fun `AdvanceDelegation and AdvanceImportedDelegation steps also contribute via bundle-scoped tracking`() {
        val advanceDelegationTracker = VotingRoundProgressTracker()
        advanceDelegationTracker.record(VotingNextStep.AdvanceDelegation(bundleIndex = 0), proofProgress = 0.4f)
        assertEquals(
            (0.4f / 37f) * delegationPhaseWeight,
            advanceDelegationTracker.fraction(completedProposals = 0, totalProposals = 37)
        )

        val advanceImportedTracker = VotingRoundProgressTracker()
        advanceImportedTracker.record(VotingNextStep.AdvanceImportedDelegation(bundleIndex = 0), proofProgress = 0.6f)
        assertEquals(
            (0.6f / 37f) * delegationPhaseWeight,
            advanceImportedTracker.fraction(completedProposals = 0, totalProposals = 37)
        )
    }

    @Test
    fun `distinct delegating bundles sum rather than only tracking one`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 1.0f)
        tracker.record(VotingNextStep.Delegate(bundleIndex = 1), proofProgress = 0.5f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals(((1.0f + 0.5f) / 37f) * delegationPhaseWeight, fraction)
    }

    @Test
    fun `delegation progress for the same bundle is monotonic`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 0.8f)
        // A later, lower value for the SAME bundle must not drag its stored contribution back down.
        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 0.1f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals((0.8f / 37f) * delegationPhaseWeight, fraction)
    }

    @Test
    fun `delegation progress on one bundle adds to casting progress on a different bundle`() {
        val tracker = VotingRoundProgressTracker()

        // Bundle 0 is still delegating; bundle 1's proposal 5 is already casting. These are
        // non-overlapping units of work and must both count, not just the larger of the two --
        // but only the casting contribution counts at full weight, since only
        // estimatedCompletedProposals-relevant (real, per-proposal) progress does that.
        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 0.4f)
        tracker.record(castVote(bundleIndex = 1, proposalId = 5), proofProgress = 0.9f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals((0.9f / 37f) + (0.4f / 37f) * delegationPhaseWeight, fraction)
    }

    @Test
    fun `once a bundle starts casting its earlier delegation progress no longer double-counts`() {
        val tracker = VotingRoundProgressTracker()

        // Bundle 0 finishes delegating, then starts casting proposal 1. Once a CastVote step for
        // bundle 0 is recorded, bundle 0's delegation contribution must drop out of the
        // delegation sum -- its progress is now represented by the casting side instead, or the
        // combined total would count the same bundle's completion twice.
        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 0.3f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        // Only the casting contribution (0.3) counts now -- not (1.0 stale delegation) + 0.3.
        assertEquals(0.3f / 37f, fraction)
    }

    @Test
    fun `delegation progress alone can never push the fraction to 1, even in the worst-case bundle packing`() {
        val tracker = VotingRoundProgressTracker()

        // Worst case: every bundle represents exactly one proposal (numBundles == total), and
        // every single one is fully delegated with NOTHING cast yet. Without a bounded weight,
        // this would sum to totalProposals/totalProposals == 1.0, misreporting the round as done.
        repeat(4) { bundleIndex ->
            tracker.record(VotingNextStep.Delegate(bundleIndex = bundleIndex), proofProgress = 1.0f)
        }

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 4)
        checkNotNull(fraction)
        assertEquals(delegationPhaseWeight, fraction)
        assertTrue(fraction < 1.0f, "delegation-only progress must never reach 1.0, was $fraction")
    }

    @Test
    fun `delegation progress is still bounded when the wallet's note set produces more bundles than proposals`() {
        val tracker = VotingRoundProgressTracker()

        // Important #2 (final whole-plan review): a 1-proposal round whose wallet note set
        // produces 2 bundles (plausible -- bundle count is ceil(note_count / 5) in the crate,
        // independent of proposal count, NOT guaranteed numBundles <= totalProposals). Both
        // bundles fully delegate with nothing cast. Before the fix, dividing by bare
        // totalProposals (1) gave (2.0/1) * 0.5 == 1.0 -- a fully-filled bar with zero votes
        // cast. The fix must keep this bounded at DELEGATION_PHASE_WEIGHT.
        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 1.0f)
        tracker.record(VotingNextStep.Delegate(bundleIndex = 1), proofProgress = 1.0f)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 1)
        checkNotNull(fraction)
        assertTrue(
            fraction <= delegationPhaseWeight,
            "delegation-only progress with more bundles than proposals must stay bounded, was $fraction"
        )
        // The count itself must remain unaffected by this fix -- still zero, since nothing real
        // has been cast (the existing decoupling from delegation must not change).
        assertNull(tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 1))
    }

    @Test
    fun `the ratchet prevents a visible regression when a bundle transitions from delegation to casting`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 1.0f)
        val whileDelegating = tracker.fraction(completedProposals = 0, totalProposals = 37)
        checkNotNull(whileDelegating)

        // Bundle 0 now starts casting at a low proof progress -- the raw combined value would
        // momentarily dip (delegation contribution drops out, casting hasn't caught up yet), but
        // the displayed fraction must never move backwards.
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 0.1f)
        val afterTransition = tracker.fraction(completedProposals = 0, totalProposals = 37)
        checkNotNull(afterTransition)
        assertTrue(
            afterTransition >= whileDelegating,
            "fraction regressed from $whileDelegating to $afterTransition"
        )
    }

    @Test
    fun `a null step or null proof progress is a safe no-op`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(step = null, proofProgress = 0.5f)
        tracker.record(step = castVote(bundleIndex = 0, proposalId = 1), proofProgress = null)

        assertNull(tracker.fraction(completedProposals = 0, totalProposals = 37))
    }

    @Test
    fun `total proposals of zero never divides by zero`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)

        assertNull(tracker.fraction(completedProposals = 0, totalProposals = 0))
    }

    private fun castVote(
        bundleIndex: Int,
        proposalId: Int,
        choice: Int = 0
    ) = VotingNextStep.CastVote(bundleIndex = bundleIndex, proposalId = proposalId, choice = choice)

    // --- voteCommitProposalId (the crate's real per-draft identity) ---
    //
    // A CastVote *step* names only whichever proposal the crate happened to SELECT the whole
    // bundle-casting step with -- fixed for that step's entire lifetime, since `run_cast_vote`
    // casts every draft of a bundle as one unit. `voteCommitProposalId` is the crate's own
    // per-draft `VoteCommit` progress payload instead, which changes as each draft in the batch
    // actually proceeds -- see VotingRoundDriveProgress's own doc comment. Before this, every
    // in-batch record() call shared the step's one fixed proposal id, so bundleProgressByProposal
    // could never grow past a single entry for an entire many-proposal casting phase -- exactly
    // the "stuck at 1/37, then jumps to 37/37" behavior observed live.

    @Test
    fun `distinct voteCommitProposalId values are tracked as distinct proposals, even under one fixed step id`() {
        val tracker = VotingRoundProgressTracker()

        // All three calls share the SAME step (as the crate's step_selected/step_finished
        // lifetime actually behaves for one CastVote-batch step), but each carries a DIFFERENT
        // voteCommitProposalId, as the crate's VoteCommit payload actually does.
        val step = castVote(bundleIndex = 0, proposalId = 1)
        tracker.record(step, proofProgress = 1.0f, voteCommitProposalId = 1)
        tracker.record(step, proofProgress = 1.0f, voteCommitProposalId = 2)
        tracker.record(step, proofProgress = 0.5f, voteCommitProposalId = 3)

        // Without voteCommitProposalId, this would have collapsed to one entry (proposal 1) at
        // 0.5/37 (the last write wins on a single bucket). With it, three distinct proposals are
        // tracked and summed.
        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals((1.0f + 1.0f + 0.5f) / 37f, fraction)
    }

    @Test
    fun `voteCommitProposalId takes priority over the step's own fixed proposal id when both are present`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f, voteCommitProposalId = 9)

        // Bucketed under 9 (the real draft), not 1 (the step's fixed representative id) -- proven
        // by a second real draft (proposal 9 again vs. a fresh id) accumulating correctly.
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f, voteCommitProposalId = 10)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals((1.0f + 1.0f) / 37f, fraction)
    }

    @Test
    fun `a null voteCommitProposalId falls back to the step's own proposal id, unchanged from before`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 5), proofProgress = 0.5f, voteCommitProposalId = null)

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        assertEquals(0.5f / 37f, fraction)
    }

    // --- estimatedCompletedProposals ---

    @Test
    fun `estimated count is null when total is unknown`() {
        val tracker = VotingRoundProgressTracker()

        assertNull(tracker.estimatedCompletedProposals(completedProposals = null, totalProposals = null))
        assertNull(tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = null))
    }

    @Test
    fun `estimated count is null when nothing measured and tally is genuinely zero`() {
        val tracker = VotingRoundProgressTracker()

        assertNull(tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4))
    }

    @Test
    fun `estimated count reflects measured proposals below the authoritative tally`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 2), proofProgress = 1.0f)

        // 2 fully-measured proposals out of 4 -> fraction 0.5 -> estimate 2, even though the
        // authoritative tally hasn't caught up yet.
        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(2, estimate)
    }

    @Test
    fun `estimated count never reaches total until the authoritative tally says so`() {
        val tracker = VotingRoundProgressTracker()

        // All 4 proposals fully measured (fraction would mathematically be 1.0) but the
        // authoritative tally still reports 0 completed -- the estimate must stay capped below
        // total, never claiming the round is fully done before the real tally confirms it.
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 2), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 3), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 4), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(3, estimate)
    }

    @Test
    fun `estimated count snaps to the exact total once the authoritative tally confirms it`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)

        val beforeCompletion = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(1, beforeCompletion)

        val afterCompletion = tracker.estimatedCompletedProposals(completedProposals = 4, totalProposals = 4)
        assertEquals(4, afterCompletion)
    }

    @Test
    fun `estimated count never regresses across calls`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 2), proofProgress = 1.0f)
        val high = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        checkNotNull(high)

        // A brand-new, still-low-progress proposal must not drag the reported estimate back down.
        tracker.record(castVote(bundleIndex = 0, proposalId = 3), proofProgress = 0f)
        val next = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        checkNotNull(next)
        assertTrue(next >= high, "estimate regressed from $high to $next")
    }

    @Test
    fun `estimated count uses the authoritative tally when it is ahead of what was locally measured`() {
        val tracker = VotingRoundProgressTracker()

        // The tally jumped to 3 of 4 in one batch; nothing was locally measured at all.
        val estimate = tracker.estimatedCompletedProposals(completedProposals = 3, totalProposals = 4)
        assertEquals(3, estimate)
    }

    @Test
    fun `estimated count ignores delegation-only progress with nothing cast`() {
        val tracker = VotingRoundProgressTracker()

        // A single bundle covering both of this round's proposals finishes delegating -- but
        // nothing has actually been cast. The count must NOT claim a proposal is complete just
        // because its bundle finished delegating; only fraction() (the visual bar) may move here.
        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 2)
        assertNull(estimate)

        // The bar, in contrast, is allowed to show some motion from the same delegation progress.
        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 2)
        assertTrue(fraction != null && fraction > 0f, "fraction should still reflect delegation progress")
    }

    @Test
    fun `estimated count starts moving only once real per-proposal progress exists, even alongside delegation`() {
        val tracker = VotingRoundProgressTracker()

        // Bundle 0 is fully delegated (no proposal credited for it); proposal 1 in a different
        // bundle is fully cast (this one IS credited).
        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 1, proposalId = 1), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        // 1 of 4 proposals genuinely measured as fully cast; the delegating bundle contributes
        // nothing to this count, no matter how far its own delegation has progressed.
        assertEquals(1, estimate)
    }

    // --- recordPlan / multi-bundle proportional credit (live 13-bundle finding) ---
    //
    // A round with several bundles casts the SAME ballot once per bundle. An earlier version of
    // this required EVERY carrying bundle to report a proposal at full progress before counting
    // it at all -- correct in the strict sense, but for a many-bundle round that froze this at 0
    // for nearly the whole run while `fraction()` (fed by the same underlying data) visibly
    // climbed, a contradictory pairing a live tester immediately (and correctly) rejected: the
    // tracker already knows the real, granular proportion of work done at every moment, so a
    // frozen count instead of that known proportion was never actually "expected" behavior, just
    // a worse design choice. `recordPlan`'s carrying-bundle-count now scales each proposal's
    // OWN contribution proportionally instead of gating it behind unanimous full-progress
    // agreement -- see `proposalCompletionFraction`'s own doc comment.

    @Test
    fun `a proposal contributes only partial credit before every carrying bundle has reported it`() {
        val tracker = VotingRoundProgressTracker()
        tracker.recordPlan(listOf(0, 1))

        // Only bundle 0 (of the 2 required) has finished proposals 1 and 2; bundle 1 hasn't
        // reported either yet. Each is worth half credit until bundle 1 catches up -- two
        // half-credited proposals round down to 1 whole proposal, not 2.
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 0, proposalId = 2), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(1, estimate, "two proposals at half credit (1 of 2 carrying bundles) should round to 1, not 2")
    }

    @Test
    fun `a proposal counts as fully complete once every carrying bundle has reported it at full progress`() {
        val tracker = VotingRoundProgressTracker()
        tracker.recordPlan(listOf(0, 1))

        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 1, proposalId = 1), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(1, estimate)
    }

    @Test
    fun `a bundle dropping out of the plan after finishing does not shrink the carrying count`() {
        val tracker = VotingRoundProgressTracker()

        // First refresh: both bundles still owe vote work.
        tracker.recordPlan(listOf(0, 1))
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 1, proposalId = 1), proofProgress = 1.0f)

        // Bundle 0 finished casting and the plan no longer lists it -- recordPlan now REPLACES its
        // view of the latest plan rather than unioning forever (see recordPlan's own doc comment),
        // but bundle 0 must still count here because it already reported real progress above, so
        // it's retained via the separate union with observed bundle indexes at read time -- the
        // same split Vizor Wallet's own voteCarryingBundleIndexes()/caller pairing uses.
        tracker.recordPlan(listOf(1))

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(1, estimate)
    }

    @Test
    fun `a bundle the crate revises out of the plan before it ever casts does not inflate the required count`() {
        val tracker = VotingRoundProgressTracker()

        // First refresh: the plan looks like both bundles will carry a vote.
        tracker.recordPlan(listOf(0, 1))

        // Bundle 1's delegation ends terminal before it ever reports any vote progress -- unlike
        // the "drops out after finishing" case above, it never appears in bundleProgressByProposal
        // either. The crate's next PlanRefreshed correctly no longer lists it. An earlier version
        // of recordPlan unioned every plan forever, so bundle 1 stayed counted in the denominator
        // regardless -- artificially depressing the estimate below what bundle 0 alone measures.
        tracker.recordPlan(listOf(0))
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(
            1,
            estimate,
            "bundle 1 never carried a vote by the latest plan and never reported progress, so it " +
                "must not depress the estimate below what bundle 0 alone measured"
        )
    }

    @Test
    fun `bundles this tracker has itself observed are unioned in even without a matching plan refresh`() {
        val tracker = VotingRoundProgressTracker()

        // No recordPlan call at all -- the carrying-bundle count falls back entirely to observed
        // bundle indices, exactly like the pre-existing single-bundle tests rely on.
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)
        tracker.record(castVote(bundleIndex = 1, proposalId = 1), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(1, estimate)
    }

    @Test
    fun `a null or empty recordPlan call is a safe no-op`() {
        val tracker = VotingRoundProgressTracker()
        tracker.recordPlan(null)
        tracker.recordPlan(emptyList())

        // Only ever one bundle observed, so it alone is the full carrying count -- full credit.
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 4)
        assertEquals(1, estimate)
    }

    @Test
    fun `many bundles at different paces produce a proportional estimate, not a frozen zero`() {
        val tracker = VotingRoundProgressTracker()
        tracker.recordPlan((0 until 13).toList())

        // Bundle 0 (the fastest) has raced through every proposal; the other 12 of 13 required
        // bundles haven't reported anything at all yet -- reproduces the live 13-bundle
        // observation. The old strict-agreement rule froze this at 0 the whole time despite the
        // tracker already knowing roughly 1 of 13 bundles' worth of the round's total work is
        // genuinely done.
        for (proposalId in 1..37) {
            tracker.record(castVote(bundleIndex = 0, proposalId = proposalId), proofProgress = 1.0f)
        }

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 37)
        checkNotNull(estimate)
        // Roughly 37/13 ~= 2-3 proposals' worth -- moving and honest, neither stuck at 0 nor
        // claiming anywhere near the 37 bundle 0 alone raced through.
        assertTrue(estimate in 1..4, "expected a proportional estimate around 37/13 ~= 2-3, was $estimate")
    }

    /**
     * Regression test for a Float32 round-trip truncation bug: for `total = 41` (and many other
     * non-power-of-2 totals, e.g. 37, 47, 55, 61), `1f / 41f * 41f` does not recover exactly
     * `1.0f` in single precision -- it lands at `0.9999999f` -- so a plain `.toInt()` truncated a
     * genuinely fully-measured single proposal down to 0 instead of 1. The fix adds a small
     * epsilon before truncating.
     */
    @Test
    fun `a single fully-measured proposal out of 41 is not truncated to zero by float rounding`() {
        val tracker = VotingRoundProgressTracker()

        // One bundle, fully cast one proposal's worth of work -- the authoritative tally hasn't
        // confirmed it yet (completedProposals = 0), but the tracker has genuinely, fully
        // measured 1/41 of the round.
        tracker.record(castVote(bundleIndex = 0, proposalId = 1), proofProgress = 1.0f)

        val estimate = tracker.estimatedCompletedProposals(completedProposals = 0, totalProposals = 41)
        assertEquals(1, estimate)
    }

    /**
     * Regression test for the SAME live 13-bundle test, a second bug found after the first fix:
     * `fraction()` (the visual progress bar/ring, distinct from the "N of M" text
     * [estimatedCompletedProposals] fixes above) filled all the way to 1.0 once only 2 of 13
     * bundles had raced through every proposal, while the text correctly still read "0 of 37" --
     * a visibly contradictory combination the user caught live. Root cause: unlike
     * [estimatedCompletedProposals], [fraction]'s own [proposalCompletionFraction] never checked
     * the required bundle count at all -- a proposal's contribution was the minimum progress
     * among whichever bundles HAD reported, silently ignoring the other 11 bundles that had not
     * reported yet rather than counting their absence as zero.
     */
    @Test
    fun `fraction does not reach 100 percent when only a few of many carrying bundles have finished`() {
        val tracker = VotingRoundProgressTracker()
        tracker.recordPlan((0 until 13).toList())

        // Bundles 0 and 1 (2 of the 13 required) have raced through every proposal in the round;
        // the other 11 bundles haven't reported anything at all yet -- exactly the live scenario.
        for (proposalId in 1..37) {
            tracker.record(castVote(bundleIndex = 0, proposalId = proposalId), proofProgress = 1.0f)
            tracker.record(castVote(bundleIndex = 1, proposalId = proposalId), proofProgress = 1.0f)
        }

        val fraction = tracker.fraction(completedProposals = 0, totalProposals = 37)
        checkNotNull(fraction)
        // Roughly 2/13 of the round's total casting work is actually done (2 of 13 bundles, every
        // proposal) -- nowhere near 1.0, and specifically not the ~1.0 the old minimum-only
        // formula would have produced by crediting each proposal in full the moment just these
        // two bundles finished it.
        assertTrue(
            fraction < 0.3f,
            "fraction should reflect roughly 2/13 of total work, not fill up from only 2 of 13 bundles, was $fraction"
        )
        assertTrue(fraction > 0f, "fraction should still show real, non-zero progress")
    }

    @Test
    fun `fraction still reaches 100 percent once every carrying bundle has finished`() {
        val tracker = VotingRoundProgressTracker()
        tracker.recordPlan(listOf(0, 1, 2))

        for (proposalId in 1..37) {
            for (bundleIndex in 0..2) {
                tracker.record(castVote(bundleIndex = bundleIndex, proposalId = proposalId), proofProgress = 1.0f)
            }
        }

        val fraction = tracker.fraction(completedProposals = 37, totalProposals = 37)
        assertEquals(1f, fraction)
    }
}
