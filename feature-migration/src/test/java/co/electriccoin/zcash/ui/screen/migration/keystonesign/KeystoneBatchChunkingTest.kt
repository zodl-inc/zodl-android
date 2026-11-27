package co.electriccoin.zcash.ui.screen.migration.keystonesign

import cash.z.ecc.android.sdk.KeystoneSigningRoundBudget
import kotlin.test.Test
import kotlin.test.assertEquals

class KeystoneBatchChunkingTest {
    // The engine's real constants (zcash_pool_migration::signing_rounds): 96 total actions per QR
    // round, a note-split preparation weighs 16, a transfer weighs 3. Capacity: 26 transfers
    // alongside the split ((96-16)/3), 32 without (96/3).
    private val budget = KeystoneSigningRoundBudget(maxActions = 96, preparationActions = 16, transferActions = 3)

    @Test
    fun totalRounds_is_zero_when_there_is_nothing_to_sign() {
        assertEquals(0, keystoneBatchTotalRounds(hasSplit = false, transferCount = 0, budget = budget))
    }

    @Test
    fun totalRounds_is_one_when_everything_fits_under_the_action_budget() {
        // split (16) + 12 transfers (36) = 52 actions ≤ 96.
        assertEquals(1, keystoneBatchTotalRounds(hasSplit = true, transferCount = 12, budget = budget))
        // 32 transfers = 96 actions, exactly the budget.
        assertEquals(1, keystoneBatchTotalRounds(hasSplit = false, transferCount = 32, budget = budget))
    }

    @Test
    fun totalRounds_is_one_when_exactly_at_the_budget_with_a_split() {
        // split (16) + 26 transfers (78) = 94 ≤ 96; a 27th transfer would overflow.
        assertEquals(1, keystoneBatchTotalRounds(hasSplit = true, transferCount = 26, budget = budget))
    }

    @Test
    fun totalRounds_is_two_one_transfer_over_the_budget() {
        assertEquals(2, keystoneBatchTotalRounds(hasSplit = false, transferCount = 33, budget = budget))
        assertEquals(2, keystoneBatchTotalRounds(hasSplit = true, transferCount = 27, budget = budget))
    }

    @Test
    fun totalRounds_covers_the_real_worst_case_of_one_split_and_64_transfers() {
        // Round 0: split + 26; rounds 1-2: 32 + 6.
        assertEquals(3, keystoneBatchTotalRounds(hasSplit = true, transferCount = 64, budget = budget))
    }

    @Test
    fun totalRounds_is_one_for_a_split_with_no_transfers() {
        assertEquals(1, keystoneBatchTotalRounds(hasSplit = true, transferCount = 0, budget = budget))
    }

    @Test
    fun roundSlice_single_round_includes_the_split_and_every_transfer() {
        val slice = keystoneBatchRoundSlice(roundIndex = 0, hasSplit = true, transferCount = 12, budget = budget)
        assertEquals(
            KeystoneBatchRoundSlice(includeSplit = true, prepRange = IntRange.EMPTY, transferRange = 0 until 12),
            slice,
        )
    }

    @Test
    fun roundSlice_splits_the_worst_case_into_non_overlapping_exhaustive_rounds() {
        val round0 = keystoneBatchRoundSlice(roundIndex = 0, hasSplit = true, transferCount = 64, budget = budget)
        val round1 = keystoneBatchRoundSlice(roundIndex = 1, hasSplit = true, transferCount = 64, budget = budget)
        val round2 = keystoneBatchRoundSlice(roundIndex = 2, hasSplit = true, transferCount = 64, budget = budget)

        // Round 0: split (16 actions) + 26 transfers (78) = 94 ≤ 96.
        assertEquals(
            KeystoneBatchRoundSlice(includeSplit = true, prepRange = IntRange.EMPTY, transferRange = 0 until 26),
            round0,
        )
        // Round 1: no split, 32 transfers = 96 actions exactly.
        assertEquals(
            KeystoneBatchRoundSlice(includeSplit = false, prepRange = IntRange.EMPTY, transferRange = 26 until 58),
            round1,
        )
        // Round 2: the remaining 6.
        assertEquals(
            KeystoneBatchRoundSlice(includeSplit = false, prepRange = IntRange.EMPTY, transferRange = 58 until 64),
            round2,
        )

        // Every transfer is covered exactly once across all rounds.
        val covered = (round0.transferRange.toList() + round1.transferRange.toList() + round2.transferRange.toList())
        assertEquals((0 until 64).toList(), covered)
    }

    @Test
    fun roundSlice_without_a_split_packs_transfers_tightly_at_the_boundary() {
        val round0 = keystoneBatchRoundSlice(roundIndex = 0, hasSplit = false, transferCount = 33, budget = budget)
        val round1 = keystoneBatchRoundSlice(roundIndex = 1, hasSplit = false, transferCount = 33, budget = budget)

        assertEquals(
            KeystoneBatchRoundSlice(includeSplit = false, prepRange = IntRange.EMPTY, transferRange = 0 until 32),
            round0,
        )
        assertEquals(
            KeystoneBatchRoundSlice(includeSplit = false, prepRange = IntRange.EMPTY, transferRange = 32 until 33),
            round1,
        )
    }

    @Test
    fun roundSlice_matches_the_settled_on_device_validation_shape() {
        // The 2026-07-24 settled decision: 96 actions/round, validated 8/8 green on device. A
        // 30-transfer no-split batch (90 actions) is a single round — under the OLD 40-item
        // tx-count cap this was also one round, but a 40-transfer batch (120 actions!) is NOT
        // allowed anymore: it exceeds the action budget and must split.
        assertEquals(1, keystoneBatchTotalRounds(hasSplit = false, transferCount = 30, budget = budget))
        assertEquals(2, keystoneBatchTotalRounds(hasSplit = false, transferCount = 40, budget = budget))
    }

    // ── Whole-tree batches: extra preparations (16 actions each) in the same ceremony ────

    @Test
    fun extraPrepsPackAlongsideTheSplitAndTransfersInActionBudget() {
        // split (16) + 3 extra preps (48) = 64, leaving 32 actions → 10 transfers in round 0.
        val round0 =
            keystoneBatchRoundSlice(
                roundIndex = 0,
                hasSplit = true,
                prepCount = 3,
                transferCount = 15,
                budget = budget,
            )
        assertEquals(
            KeystoneBatchRoundSlice(includeSplit = true, prepRange = 0 until 3, transferRange = 0 until 10),
            round0,
        )
        // Round 1: remaining 5 transfers.
        val round1 =
            keystoneBatchRoundSlice(
                roundIndex = 1,
                hasSplit = true,
                prepCount = 3,
                transferCount = 15,
                budget = budget,
            )
        assertEquals(
            KeystoneBatchRoundSlice(includeSplit = false, prepRange = 3 until 3, transferRange = 10 until 15),
            round1,
        )
        assertEquals(2, keystoneBatchTotalRounds(hasSplit = true, prepCount = 3, transferCount = 15, budget = budget))
    }

    @Test
    fun tx9ShapedTreeSignsInOneRound() {
        // The live 15-tx run shape: 1 split + 3 further preps + 11 transfers =
        // 16 + 48 + 33 = 97 > 96 → needs 2 rounds; without the split's round-0 weight the
        // remaining tree (3 preps + 11 transfers = 81) fits one round.
        assertEquals(2, keystoneBatchTotalRounds(hasSplit = true, prepCount = 3, transferCount = 11, budget = budget))
        assertEquals(1, keystoneBatchTotalRounds(hasSplit = false, prepCount = 3, transferCount = 11, budget = budget))
    }

    @Test
    fun manyPrepsSpillIntoFollowUpRounds() {
        // 7 extra preps = 112 actions: round 0 = split + 5 preps (96 exactly),
        // round 1 = 2 preps (32) + up to 21 transfers (63) = 95.
        val round0 = keystoneBatchRoundSlice(0, hasSplit = true, prepCount = 7, transferCount = 30, budget = budget)
        assertEquals(0 until 5, round0.prepRange)
        assertEquals(IntRange.EMPTY.isEmpty(), round0.transferRange.isEmpty())
        val round1 = keystoneBatchRoundSlice(1, hasSplit = true, prepCount = 7, transferCount = 30, budget = budget)
        assertEquals(5 until 7, round1.prepRange)
        assertEquals(21, round1.transferRange.count())
    }
}
