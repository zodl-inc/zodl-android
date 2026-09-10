package co.electriccoin.zcash.ui.common.model.voting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VotingBundleTrimmingTest {
    @Test
    fun emptyKeepsZero() {
        assertEquals(0, computeTrimmedBundleKeepCount(emptyList()))
    }

    @Test
    fun singleBundleNeverTrimmed() {
        assertEquals(1, computeTrimmedBundleKeepCount(listOf(ZEC)))
    }

    @Test
    fun trimsTailWithinOnePercentBudget() {
        val weights = listOf(1_000 * ZEC, 500 * ZEC, ZEC, ZEC)

        assertEquals(2, computeTrimmedBundleKeepCount(weights))
    }

    @Test
    fun budgetExactlyMetIsInclusive() {
        // 1 % of 1_000 ZEC is exactly 10 ZEC, which is exactly what the two tail bundles cost.
        val weights = listOf(890 * ZEC, 100 * ZEC, 5 * ZEC, 5 * ZEC)

        assertEquals(2, computeTrimmedBundleKeepCount(weights))
    }

    @Test
    fun stopsWhenTheNextDropWouldExceedTheBudget() {
        // One zatoshi over the same 10 ZEC budget: the cheaper tail bundle still goes, the next
        // one no longer fits.
        val weights = listOf(890 * ZEC, 100 * ZEC, 5 * ZEC, 5 * ZEC + 1)

        assertEquals(3, computeTrimmedBundleKeepCount(weights))
    }

    @Test
    fun stopsAtTwoBundles() {
        val weights = listOf(100_000 * ZEC) + List(20) { ZEC }

        assertEquals(VotingBundleTrimPolicy.MAX_PRIVACY_BUNDLES, computeTrimmedBundleKeepCount(weights))
    }

    @Test
    fun budgetCappedAtThousandZec() {
        // 1 % of 1_000_300 ZEC would be 10_003 ZEC, but the absolute cap is 1_000 ZEC, so only two
        // of the three 400 ZEC tail bundles fit inside the budget.
        val weights = listOf(999_000 * ZEC, 100 * ZEC, 400 * ZEC, 400 * ZEC, 400 * ZEC)

        assertEquals(3, computeTrimmedBundleKeepCount(weights))
    }

    @Test
    fun neverDropsBelowTwoWhenBudgetAllows() {
        // The budget could pay for every tail bundle many times over, yet two bundles survive.
        val weights = listOf(1_000_000 * ZEC, ZEC, ZEC, ZEC, ZEC)

        assertEquals(2, computeTrimmedBundleKeepCount(weights))
    }

    @Test
    fun disabledPolicyKeepsAll() {
        val weights = listOf(1_000 * ZEC, 500 * ZEC, ZEC, ZEC)

        assertEquals(weights.size, computeTrimmedBundleKeepCount(weights, enabled = false))
    }

    @Test
    fun fortyBundlesCollapseToTwo() {
        // Mirrors the crate's privacy_trim_collapses_concentrated_whale_with_dust_tail: a holder
        // with 8 x 500 ZEC plus 190 x 0.1 ZEC chunks into 40 bundles worth 4019 ZEC in total, and
        // the tail costs 18.80 ZEC - well inside the 40.19 ZEC budget.
        val weights =
            buildList {
                add(2_500 * ZEC)
                add(1_500 * ZEC + 2 * DUST)
                repeat(37) { add(5 * DUST) }
                add(3 * DUST)
            }

        assertEquals(40, weights.size)
        assertEquals(4_019 * ZEC, weights.sum())

        val keepCount = computeTrimmedBundleKeepCount(weights)

        assertEquals(2, keepCount)
        assertEquals(1_880_000_000L, weights.drop(keepCount).sum())
        assertTrue(weights.drop(keepCount).sum() <= weights.sum() / 100)
    }

    @Test
    fun trimmedSetupKeepsThePrefixAndSumsTheKeptWeights() {
        val setup =
            VotingBundleSetupResult(
                bundleCount = 4,
                eligibleWeight = 10L,
                bundleWeights = listOf(4L, 3L, 2L, 1L)
            )

        val trimmed = setup.trimmedTo(2)

        assertEquals(2, trimmed.bundleCount)
        assertEquals(7L, trimmed.eligibleWeight)
        assertEquals(listOf(4L, 3L), trimmed.bundleWeights)
    }

    private companion object {
        const val ZEC = 100_000_000L
        const val DUST = ZEC / 10
    }
}
