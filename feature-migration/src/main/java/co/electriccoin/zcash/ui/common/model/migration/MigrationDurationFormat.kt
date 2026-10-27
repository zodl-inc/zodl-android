package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.model.BlockSpacing
import co.electriccoin.zcash.migration.BuildConfig

/**
 * Formats a migration plan's total span so it reflects whatever interval the current
 * [cash.z.ecc.android.sdk.OrchardMigrationSdk] implementation actually schedules transfers at —
 * minutes for the compressed debug cadence, hours for the real one — instead of a hardcoded
 * "~24 hours" that only matched production timing.
 *
 * [fineGrained] (default: testnet builds) keeps minute resolution above one hour ("~1 h 15 min")
 * — the whole testnet plan spans ~1-2 h (12-block buckets), so integer-hour bucketing collapsed
 * most transfers into an identical "~1 hours" label. Mainnet keeps the coarse hour display.
 *
 * A duration describing an UPCOMING moment — a transfer's own "due in" hint, the header's
 * total-span/remaining estimate — is floored at [TESTNET_PRIVACY_FLOOR_SECONDS]/
 * [MAINNET_PRIVACY_FLOOR_SECONDS] (2026-08-03, decision with the user): never reveal a migration
 * timing more precise than that, since a tighter number could help correlate this wallet's
 * upcoming broadcast across the network before it happens. [fineGrained] doubles as the network
 * selector for which floor applies, matching its own existing default derivation, so callers that
 * pin [fineGrained] for a deterministic test get a deterministic floor for free.
 *
 * [applyPrivacyFloor] (default `true`) lets a caller opt OUT for an "ago" duration on an
 * ALREADY-SENT/mined transaction (2026-08-06, revised decision with the user): the correlation
 * risk the floor guards against is specific to revealing precise timing before/around the moment
 * of broadcast; once a transfer is mined, its exact block time is already public on-chain
 * regardless of what this app's own UI shows, so flooring "Sent 1 min ago" to "Sent ~10 min ago"
 * has no remaining privacy benefit — it only makes the row less informative. Every OTHER caller
 * (all upcoming/future estimates) keeps the floor at its previous default.
 */
fun formatMigrationDuration(
    totalSeconds: Long,
    fineGrained: Boolean = isTestnetBuildFlavor(),
    applyPrivacyFloor: Boolean = true,
): String {
    val floorSeconds = if (fineGrained) TESTNET_PRIVACY_FLOOR_SECONDS else MAINNET_PRIVACY_FLOOR_SECONDS
    val seconds = if (applyPrivacyFloor) totalSeconds.coerceAtLeast(floorSeconds) else totalSeconds.coerceAtLeast(0L)
    val hours = seconds / SECONDS_PER_HOUR
    val minutesPastHour = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return when {
        seconds < SECONDS_PER_HOUR -> "~${seconds / SECONDS_PER_MINUTE} min"
        !fineGrained -> "~$hours ${if (hours == 1L) "hour" else "hours"}"
        minutesPastHour == 0L -> "~$hours h"
        else -> "~$hours h $minutesPastHour min"
    }
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val TESTNET_PRIVACY_FLOOR_SECONDS = 600L // 10 minutes
private const val MAINNET_PRIVACY_FLOOR_SECONDS = 3600L // 1 hour

internal fun isTestnetBuildFlavor(): Boolean = BuildConfig.FLAVOR.contains("testnet", ignoreCase = true)

/**
 * Estimates the wall-clock duration, in seconds, spanned by a block-height difference, using the
 * network's average block time.
 *
 * [cash.z.ecc.android.sdk.TransferProposal]'s `anchorHeight`/`nextExecutableAfterHeight`/
 * `expiryHeight` are block heights, not timestamps — they must never be used directly as (or
 * compared against) epoch seconds. Doing so previously made every scheduled transfer appear
 * decades overdue: a block height (~4.18M) stored as `scheduledAtEpochSeconds` and later compared
 * against a real 2026 `Instant` measures the gap back to the Unix epoch, not to the actual
 * scheduled time.
 */
fun estimatedSecondsBetweenHeights(
    fromHeight: Long,
    toHeight: Long,
    // 75s protocol target as fallback. Pass OrchardMigrationSdk.estimatedSecondsPerBlock()
    // wherever an SDK is in reach — testnet's minimum-difficulty bursts make the constant a
    // large overestimate (observed live: "~1 h" plans coming due within minutes).
    secondsPerBlock: Long = BlockSpacing.POST_BLOSSOM_SECONDS,
    // From this height blocks come NU7_RATIO times as often (ZIP 218: 75s -> 25s), so a range that spans
    // it cannot use one rate. null (no activation known, or not passed) keeps the single-rate math.
    nu7ActivationHeight: Long? = null,
    // The height the measured [secondsPerBlock] belongs to (the chain tip it was measured at). Defaults
    // to [fromHeight], which is the tip for every "from now" projection.
    rateHeight: Long = fromHeight,
): Long =
    when {
        nu7ActivationHeight == null -> {
            (toHeight - fromHeight) * secondsPerBlock
        }

        // Past ranges (to < from, "scheduled 2h ago") are the negation of the forward range.
        toHeight < fromHeight -> {
            -estimatedSecondsBetweenHeights(toHeight, fromHeight, secondsPerBlock, nu7ActivationHeight, rateHeight)
        }

        else -> {
            forwardSecondsAcrossNu7(fromHeight, toHeight, secondsPerBlock, nu7ActivationHeight, rateHeight)
        }
    }

/**
 * The height a measured block rate holds at, for [estimatedSecondsBetweenHeights]'s `rateHeight`.
 *
 * The SDK reads the rate at its estimated chain tip, so that is the height to attribute it to. A plan anchor is not: it
 * is drawn from a bucket up to 287 blocks below the tip, and a rate read just after NU7 would be priced as a pre-NU7
 * one for a plan anchored just before it, three times too fast. [fallbackHeight] is used only when the tip is unknown
 * (the SDK answers -1 before any block is scanned).
 */
fun rateReferenceHeight(
    estimatedTip: Long,
    fallbackHeight: Long?,
): Long? = estimatedTip.takeIf { it > 0L } ?: fallbackHeight

private fun forwardSecondsAcrossNu7(
    fromHeight: Long,
    toHeight: Long,
    secondsPerBlock: Long,
    nu7ActivationHeight: Long,
    rateHeight: Long,
): Long {
    val ratio = BlockSpacing.NU7_RATIO
    val rateIsPostNu7 = rateHeight >= nu7ActivationHeight
    val preNu7Rate = if (rateIsPostNu7) secondsPerBlock * ratio else secondsPerBlock
    val postNu7Rate = if (rateIsPostNu7) secondsPerBlock else (secondsPerBlock / ratio).coerceAtLeast(1L)
    // Heights in (from, to] that are below the activation height are the pre-NU7 blocks.
    val preNu7Blocks = (minOf(toHeight, nu7ActivationHeight - 1) - fromHeight).coerceIn(0L, toHeight - fromHeight)
    val postNu7Blocks = (toHeight - fromHeight) - preNu7Blocks
    return preNu7Blocks * preNu7Rate + postNu7Blocks * postNu7Rate
}
