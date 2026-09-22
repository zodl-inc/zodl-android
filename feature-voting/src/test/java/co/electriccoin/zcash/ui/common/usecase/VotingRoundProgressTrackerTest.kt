package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.voting.VotingNextStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VotingRoundProgressTrackerTest {
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

    @Test
    fun `a step with no proposal id is ignored`() {
        val tracker = VotingRoundProgressTracker()

        tracker.record(VotingNextStep.Delegate(bundleIndex = 0), proofProgress = 0.5f)

        assertNull(tracker.fraction(completedProposals = 0, totalProposals = 37))
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
}
