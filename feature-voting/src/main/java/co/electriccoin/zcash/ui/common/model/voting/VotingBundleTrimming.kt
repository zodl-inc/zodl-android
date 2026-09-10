package co.electriccoin.zcash.ui.common.model.voting

/**
 * Mirror of the `recoverable_bundle_policy_v1` privacy trim in `zcash_voting`.
 *
 * Bundle count is `ceil(noteCount / 5)`, so a wallet whose value sits in a few large notes plus a
 * long dust tail produces many bundles that carry almost no voting weight. Every bundle costs one
 * delegation proof plus one vote proof per question, each followed by a poll-until-mined wait, so
 * shedding the tail is what makes a many-question round finish in minutes instead of hours.
 *
 * [ENABLED] is a compile-time kill switch: flipping it to `false` restores the untrimmed behaviour
 * without touching any call site.
 */
internal object VotingBundleTrimPolicy {
    const val ENABLED = true

    /** Bundle count the trim aims for whenever the drop budget can pay for it. */
    const val MAX_PRIVACY_BUNDLES = 2

    /** Share of the selected note value the trim may discard, in basis points (100 bps = 1 %). */
    const val DROP_BPS = 100L

    /** Absolute ceiling on the discarded raw note value: 1,000 ZEC. */
    const val MAX_DROP_ZATOSHI = 100_000_000_000L

    const val BPS_DENOMINATOR = 10_000L
}

/**
 * How a prepared bundle setup was trimmed: [keepCount] bundles survive, [trimmedBundleCount] were
 * dropped, and [trimmedWeight] is their combined quantized voting weight — the figure the
 * confirmation screen reports to the user.
 */
internal data class VotingBundleTrim(
    val keepCount: Int,
    val trimmedBundleCount: Int,
    val trimmedWeight: Long
)

/**
 * Greedily pops the cheapest bundles off the value-DESC [rawWeights] tail while the accumulated
 * drop stays within the budget, never going below [VotingBundleTrimPolicy.MAX_PRIVACY_BUNDLES].
 * A drop that lands exactly on the budget is taken; one that would exceed it stops the loop.
 */
internal fun computeTrimmedBundleKeepCount(
    rawWeights: List<Long>,
    enabled: Boolean = VotingBundleTrimPolicy.ENABLED
): Int {
    val minimumBundles = VotingBundleTrimPolicy.MAX_PRIVACY_BUNDLES.coerceAtLeast(1)
    if (!enabled || rawWeights.size <= minimumBundles) {
        return rawWeights.size
    }

    val budget =
        minOf(
            rawWeights.sum() * VotingBundleTrimPolicy.DROP_BPS / VotingBundleTrimPolicy.BPS_DENOMINATOR,
            VotingBundleTrimPolicy.MAX_DROP_ZATOSHI
        )

    var keepCount = rawWeights.size
    var dropped = 0L
    while (keepCount > minimumBundles) {
        val last = rawWeights[keepCount - 1]
        if (dropped + last > budget) {
            break
        }
        dropped += last
        keepCount -= 1
    }
    return keepCount
}

/**
 * Restricts a prepared setup to its first [keepCount] bundles. Indexes below [keepCount] keep the
 * exact composition they had before the trim, which is what lets the trimmed suffix be deleted
 * from the voting DB without invalidating witnesses, PCZTs or Keystone memo weights.
 */
internal fun VotingBundleSetupResult.trimmedTo(keepCount: Int): VotingBundleSetupResult {
    require(keepCount in 0..bundleCount) {
        "Trimmed bundle count $keepCount is outside 0..$bundleCount"
    }
    require(bundleWeights.size >= bundleCount) {
        "Bundle setup is missing per-bundle weights: ${bundleWeights.size} < $bundleCount"
    }
    val keptWeights = bundleWeights.take(keepCount)
    return VotingBundleSetupResult(
        bundleCount = keepCount,
        eligibleWeight = keptWeights.sum(),
        bundleWeights = keptWeights
    )
}
