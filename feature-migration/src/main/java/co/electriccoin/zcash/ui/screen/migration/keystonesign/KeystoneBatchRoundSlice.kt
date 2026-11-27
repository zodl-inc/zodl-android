package co.electriccoin.zcash.ui.screen.migration.keystonesign

import cash.z.ecc.android.sdk.KeystoneSigningRoundBudget

// ACTION-based chunking of a Keystone batch-signing sequence, driven by the engine's own
// signing-round budget (KeystoneSigningRoundBudget, from `zcash_pool_migration::signing_rounds`:
// Keystone = 96 TOTAL Orchard actions per QR round; every preparation weighs 16 actions, a
// migration transfer 3). This replaces the earlier 40-item TX-COUNT cap, which encoded the revoked
// 128-action target (`#ext-zodl-valargroup` history; the settled decision of 2026-07-24 is
// 96 actions/round, validated 8/8 green on device).
//
// The batch covers the WHOLE plan: the first layer-0 split (its own immediate-broadcast path),
// every further preparation of the note-split tree, and every transfer — all built (and therefore
// pre-signable) at commit, sign-now/prove-later. Items pack per round largest-first in stable
// order (split → preparations → transfers), topping each round up with transfers — for the two
// engine item sizes (16/3) this per-round largest-first fill matches the engine's optimal
// `MinRounds` round count.

/**
 * One round of a (possibly multi-round) Keystone batch-signing sequence: whether this round's QR
 * includes the first note-split PCZT (always round 0, if a split exists at all), plus which slices
 * of the extra-preparation list and the transfer list (by index into the full, unchunked lists)
 * belong to this round.
 */
data class KeystoneBatchRoundSlice(
    val includeSplit: Boolean,
    val prepRange: IntRange,
    val transferRange: IntRange,
)

/** Per-round (extraPreps, transfers) counts for the whole batch — the single packing loop. */
private fun packRounds(
    hasSplit: Boolean,
    prepCount: Int,
    transferCount: Int,
    budget: KeystoneSigningRoundBudget,
): List<Pair<Int, Int>> {
    if (!hasSplit && prepCount == 0 && transferCount == 0) return emptyList()
    val prepWeight = budget.preparationActions.coerceAtLeast(1)
    val transferWeight = budget.transferActions.coerceAtLeast(1)
    val rounds = mutableListOf<Pair<Int, Int>>()
    var prepsLeft = prepCount
    var transfersLeft = transferCount
    var first = true
    while (first || prepsLeft > 0 || transfersLeft > 0) {
        var remaining = budget.maxActions - if (first && hasSplit) prepWeight else 0
        val preps = (remaining / prepWeight).coerceAtMost(prepsLeft).coerceAtLeast(0)
        remaining -= preps * prepWeight
        val transfers = (remaining / transferWeight).coerceAtMost(transfersLeft).coerceAtLeast(0)
        rounds += preps to transfers
        prepsLeft -= preps
        transfersLeft -= transfers
        first = false
        // Guard: a degenerate budget that fits nothing must not loop forever — force progress.
        if (preps == 0 && transfers == 0 && (prepsLeft > 0 || transfersLeft > 0)) {
            if (prepsLeft > 0) {
                rounds[rounds.lastIndex] = 1 to rounds.last().second
                prepsLeft -= 1
            } else {
                rounds[rounds.lastIndex] = rounds.last().first to 1
                transfersLeft -= 1
            }
        }
    }
    return rounds
}

/**
 * How many QR-signing rounds a batch of [prepCount] extra preparations plus [transferCount]
 * transfers (plus an optional first note split) needs under [budget].
 */
fun keystoneBatchTotalRounds(
    hasSplit: Boolean,
    transferCount: Int,
    budget: KeystoneSigningRoundBudget,
    prepCount: Int = 0,
): Int = packRounds(hasSplit, prepCount, transferCount, budget).size

/**
 * The [KeystoneBatchRoundSlice] for [roundIndex] (0-based). Rounds fill in order — split first
 * (round 0 only), then extra preparations, each round topped up with transfers.
 */
fun keystoneBatchRoundSlice(
    roundIndex: Int,
    hasSplit: Boolean,
    transferCount: Int,
    budget: KeystoneSigningRoundBudget,
    prepCount: Int = 0,
): KeystoneBatchRoundSlice {
    require(roundIndex >= 0) { "roundIndex must be non-negative, was $roundIndex" }
    val rounds = packRounds(hasSplit, prepCount, transferCount, budget)
    var prepStart = 0
    var transferStart = 0
    for (i in 0 until roundIndex) {
        val (p, t) = rounds.getOrNull(i) ?: (0 to 0)
        prepStart += p
        transferStart += t
    }
    val (p, t) = rounds.getOrNull(roundIndex) ?: (0 to 0)
    return KeystoneBatchRoundSlice(
        includeSplit = hasSplit && roundIndex == 0,
        prepRange = prepStart until (prepStart + p),
        transferRange = transferStart until (transferStart + t),
    )
}
