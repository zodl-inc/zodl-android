package co.electriccoin.zcash.ui.screen.pay

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayMaxTest {
    @Test
    fun usablePricesMustBothBePositive() {
        assertTrue(PayMax.hasUsablePrices(BigDecimal("30"), BigDecimal("0.5")))
        assertFalse(PayMax.hasUsablePrices(null, BigDecimal("1")))
        assertFalse(PayMax.hasUsablePrices(BigDecimal("1"), null))
        assertFalse(PayMax.hasUsablePrices(BigDecimal.ZERO, BigDecimal("1")))
        assertFalse(PayMax.hasUsablePrices(BigDecimal("1"), BigDecimal.ZERO))
        assertFalse(PayMax.hasUsablePrices(BigDecimal("-1"), BigDecimal("1")))
    }

    @Test
    fun tokenAmountIsFlooredNotRounded() {
        // 1.23456789 * (1 - 0.0067) * 30 / 11 = 3.344444414010...
        assertEquals(
            BigDecimal("3.34444441"),
            PayMax.tokenAmount(BigDecimal("1.23456789"), BigDecimal("30"), BigDecimal("11"))
        )
    }

    @Test
    fun tokenAmountIsNullWithoutUsablePrices() {
        assertNull(PayMax.tokenAmount(BigDecimal.ONE, null, BigDecimal.ONE))
        assertNull(PayMax.tokenAmount(BigDecimal.ONE, BigDecimal.ONE, null))
        assertNull(PayMax.tokenAmount(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
        assertNull(PayMax.tokenAmount(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO))
    }

    @Test
    fun tokenAmountIsNullWhenNothingRemainsAfterFlooring() {
        assertNull(PayMax.tokenAmount(BigDecimal("0.00000001"), BigDecimal("1"), BigDecimal("1000000000")))
    }

    @Test
    fun zecEquivalentIsFlooredToAZatoshi() {
        // 3.35225338 * 11 / 30 / (1 - 0.0067); floored to zatoshi
        assertEquals(
            123_745_049L,
            PayMax.zecEquivalentZatoshi(BigDecimal("3.35225338"), BigDecimal("30"), BigDecimal("11"))
        )
    }

    @Test
    fun tokenAmountOfTheZecEquivalentNeverExceedsTheToken() {
        val token = BigDecimal("3.35225338")
        val zat = PayMax.zecEquivalentZatoshi(token, BigDecimal("30"), BigDecimal("11"))!!
        val back = PayMax.tokenAmount(BigDecimal(zat).movePointLeft(8), BigDecimal("30"), BigDecimal("11"))!!
        assertTrue(back <= token)
    }

    @Test
    fun zecEquivalentIsNullWithoutUsablePrices() {
        assertNull(PayMax.zecEquivalentZatoshi(BigDecimal.ONE, null, BigDecimal.ONE))
        assertNull(PayMax.zecEquivalentZatoshi(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO))
    }

    @Test
    fun priceIsUnavailableOnlyOncePricesHaveLoadedForAnAsset() {
        val price = BigDecimal("2")
        assertTrue(PayMax.isPriceUnavailable(true, true, null, price))
        assertTrue(PayMax.isPriceUnavailable(true, true, price, null))
        assertFalse(PayMax.isPriceUnavailable(true, true, price, price))
        assertFalse(PayMax.isPriceUnavailable(false, true, null, null))
        assertFalse(PayMax.isPriceUnavailable(true, false, null, null))
    }
}
