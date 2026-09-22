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
}
