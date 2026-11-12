package co.electriccoin.zcash.ui.screen.pay

import java.math.BigDecimal
import java.math.MathContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaxQuoteSizingTest {
    private val cap = BigDecimal(100)

    private fun point(token: String, zecIn: String) = MaxQuotePoint(BigDecimal(token), BigDecimal(zecIn))

    /** A provider whose quote costs a fixed part plus a part proportional to the token amount. */
    private fun cost(token: BigDecimal, fixed: Int = 10, perToken: Int = 30): BigDecimal =
        BigDecimal(fixed) + token.multiply(BigDecimal(perToken), MathContext.DECIMAL128)

    @Test
    fun afterOneQuoteTheAmountIsScaledToLandTheMarginBelowTheCap() {
        // 3 * (100 * 0.997) / 110 = 2.7190909...
        assertEquals(
            BigDecimal("2.71909090"),
            MaxQuoteSizing.nextToken(listOf(point("3", "110")), cap)
        )
    }

    @Test
    fun aFixedPartOfTheProviderFeeIsFoundByTheSecondRevision() {
        val first = BigDecimal("4")
        val firstCost = cost(first)
        val second = MaxQuoteSizing.nextToken(listOf(MaxQuotePoint(first, firstCost)), cap)!!
        // The proportional guess leaves the fixed part over the cap
        assertTrue(cost(second) > cap)

        val third =
            MaxQuoteSizing.nextToken(
                listOf(MaxQuotePoint(first, firstCost), MaxQuotePoint(second, cost(second))),
                cap
            )!!

        assertTrue(cost(third) <= cap)
        // and it stays close to the cap: within 0.1 percent of the target
        assertTrue(cost(third) >= BigDecimal("99.6"))
    }

    @Test
    fun aCostThatRoseWhileTheAmountFellFallsBackToScalingTheLastQuoteWithTwiceTheMargin() {
        // price drift between the two quotes: the second, smaller amount cost more
        val history = listOf(point("4", "130"), point("3", "131"))

        val next = MaxQuoteSizing.nextToken(history, cap)

        // 3 * (100 * 0.994) / 131 = 2.2763358...
        assertEquals(BigDecimal("2.27633587"), next)
    }

    @Test
    fun twoAlmostEqualAmountsNeverGiveAnAmountAboveTheLastOne() {
        val history = listOf(point("3.00000001", "110"), point("3.00000000", "109.99999"))

        val next = MaxQuoteSizing.nextToken(history, cap)

        assertNotNull(next)
        assertTrue(next < BigDecimal("3.00000000"))
        assertTrue(next > BigDecimal.ZERO)
    }

    @Test
    fun nothingIsSizedWithoutAQuoteOrACap() {
        assertNull(MaxQuoteSizing.nextToken(emptyList(), cap))
        assertNull(MaxQuoteSizing.nextToken(listOf(point("3", "110")), BigDecimal.ZERO))
        assertNull(MaxQuoteSizing.nextToken(listOf(point("3", "0")), cap))
    }

    @Test
    fun anAmountThatFloorsToZeroIsNull() {
        assertNull(MaxQuoteSizing.nextToken(listOf(point("0.00000001", "1000")), cap))
    }
}
