package co.electriccoin.zcash.ui.common.model.voting

import kotlin.test.Test
import kotlin.test.assertEquals

class VotingBundleMemoTest {
    @Test
    fun rawWeightsFollowCanonicalBundleOrdering() {
        val notesJson =
            notesJson(
                31_568_000L,
                26_000_000L,
                13_000_000L,
                12_500_000L,
                5_000_000L,
                4_000_000L,
                3_000_000L,
                3_000_000L,
                2_000_000L,
                1_000_000L
            )

        assertEquals(
            listOf(88_068_000L, 13_000_000L),
            votingBundleRawWeights(notesJson)
        )
    }

    @Test
    fun rawWeightsDropBundlesBelowBallotDivisor() {
        val notesJson =
            notesJson(
                30_000_000L,
                20_000_000L,
                10_000_000L,
                10_000_000L,
                5_000_000L,
                1_000_000L
            )

        assertEquals(
            listOf(75_000_000L),
            votingBundleRawWeights(notesJson)
        )
    }

    @Test
    fun rawZecLabelAlwaysUsesEightDecimals() {
        assertEquals("0.31568000 ZEC", 31_568_000L.toVotingRawZecLabel())
        assertEquals("1.25000000 ZEC", 125_000_000L.toVotingRawZecLabel())
    }

    private fun notesJson(vararg values: Long): String =
        values
            .mapIndexed { index, value -> """{"value":$value,"position":$index}""" }
            .joinToString(prefix = "[", postfix = "]")
}
