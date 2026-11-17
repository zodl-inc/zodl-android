package co.electriccoin.zcash.ui.screen.swap

import cash.z.ecc.android.sdk.model.Zatoshi
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwapMaxAmountFormatterTest {
    private val price = BigDecimal("30")

    @Test
    fun tokenModeReturnsZecWithEightDecimals() {
        val result = SwapMaxAmountFormatter.format(Zatoshi(123_456_789), CurrencyType.TOKEN, price)
        assertEquals(BigDecimal("1.23456789"), result)
    }

    @Test
    fun tokenModeIgnoresMissingPrice() {
        val result = SwapMaxAmountFormatter.format(Zatoshi(1), CurrencyType.TOKEN, null)
        assertEquals(BigDecimal("0.00000001"), result)
    }

    @Test
    fun fiatModeFloorsToCents() {
        val result = SwapMaxAmountFormatter.format(Zatoshi(123_456_789), CurrencyType.FIAT, price)
        assertEquals(BigDecimal("37.03"), result)
    }

    @Test
    fun fiatModeNeverRoundsUp() {
        val result = SwapMaxAmountFormatter.format(Zatoshi(99_999_999), CurrencyType.FIAT, price)
        assertEquals(BigDecimal("29.99"), result)
    }

    @Test
    fun fiatModeNeverExceedsMaxValue() {
        val maxZatoshi = Zatoshi(987_654_321)
        val fiat = SwapMaxAmountFormatter.format(maxZatoshi, CurrencyType.FIAT, BigDecimal("33.337"))!!
        val backToZec = fiat.divide(BigDecimal("33.337"), 20, java.math.RoundingMode.DOWN)
        assertTrue(backToZec <= BigDecimal.valueOf(maxZatoshi.value, 8))
    }

    @Test
    fun fiatModeWithoutPriceIsNull() {
        assertNull(SwapMaxAmountFormatter.format(Zatoshi(100_000_000), CurrencyType.FIAT, null))
    }

    @Test
    fun fiatModeWithZeroPriceIsNull() {
        assertNull(SwapMaxAmountFormatter.format(Zatoshi(100_000_000), CurrencyType.FIAT, BigDecimal.ZERO))
    }

    @Test
    fun fiatModeWithNegativePriceIsNull() {
        assertNull(SwapMaxAmountFormatter.format(Zatoshi(100_000_000), CurrencyType.FIAT, BigDecimal("-1")))
    }

    @Test
    fun usableInTokenModeEvenWithoutPrice() {
        assertTrue(SwapMaxAmountFormatter.isMaxUsable(false, CurrencyType.TOKEN, null))
    }

    @Test
    fun notUsableWhileQuoteIsRequested() {
        assertFalse(SwapMaxAmountFormatter.isMaxUsable(true, CurrencyType.TOKEN, price))
        assertFalse(SwapMaxAmountFormatter.isMaxUsable(true, CurrencyType.FIAT, price))
    }

    @Test
    fun fiatModeNeedsPositivePrice() {
        assertTrue(SwapMaxAmountFormatter.isMaxUsable(false, CurrencyType.FIAT, price))
        assertFalse(SwapMaxAmountFormatter.isMaxUsable(false, CurrencyType.FIAT, null))
        assertFalse(SwapMaxAmountFormatter.isMaxUsable(false, CurrencyType.FIAT, BigDecimal.ZERO))
        assertFalse(SwapMaxAmountFormatter.isMaxUsable(false, CurrencyType.FIAT, BigDecimal("-1")))
    }
}
