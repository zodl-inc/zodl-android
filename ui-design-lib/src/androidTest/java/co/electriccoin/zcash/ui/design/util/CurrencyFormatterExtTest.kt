package co.electriccoin.zcash.ui.design.util

import androidx.test.filters.SmallTest
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CurrencyFormatterExtTest {
    // region zatoshiFormatter

    @Test
    @SmallTest
    fun zatoshiFormatter_formatsCorrectly() {
        val formatter = zatoshiFormatter()
        val result = formatter.format(BigDecimal("1.23456789"))

        assertNotNull(result)
        assertTrue(result.contains("1"), "Should contain the integer part: \"$result\"")
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_maxFractionDigits_isEight() {
        val formatter = zatoshiFormatter()

        assertEquals(8, formatter.maximumFractionDigits)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_minFractionDigits_isThree() {
        val formatter = zatoshiFormatter()

        assertEquals(3, formatter.minimumFractionDigits)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_roundingMode_isHalfEven() {
        val formatter = zatoshiFormatter()

        assertEquals(RoundingMode.HALF_EVEN, formatter.roundingMode)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_preservesPrecision_eightDecimals() {
        val formatter = zatoshiFormatter()
        val result = formatter.format(BigDecimal("0.12345678"))

        assertEquals("0.12345678", result)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_truncatesNinthDecimal() {
        val formatter = zatoshiFormatter()
        val result = formatter.format(BigDecimal("0.123456789"))

        // HALF_EVEN rounds 9th decimal: 0.123456789 -> 0.12345679
        assertEquals("0.12345679", result)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_padsToThreeDecimals() {
        val formatter = zatoshiFormatter()
        val result = formatter.format(BigDecimal("1"))

        // Should show "1.000" (3 min decimals)
        assertEquals("1.000", result)
    }

    // endregion

    // region currencyFormatter

    @Test
    @SmallTest
    fun currencyFormatter_defaultParams_maxSixDecimals() {
        val formatter = currencyFormatter()

        assertEquals(6, formatter.maximumFractionDigits)
    }

    @Test
    @SmallTest
    fun currencyFormatter_defaultParams_minZeroDecimals() {
        val formatter = currencyFormatter()

        assertEquals(0, formatter.minimumFractionDigits)
    }

    @Test
    @SmallTest
    fun currencyFormatter_customMaxDecimals() {
        val formatter =
            currencyFormatter(
                maximumFractionDigits = 2,
                minimumFractionDigits = 2
            )

        assertEquals(2, formatter.maximumFractionDigits)
        assertEquals(2, formatter.minimumFractionDigits)
    }

    @Test
    @SmallTest
    fun currencyFormatter_formatsLargeNumber_withGrouping() {
        val formatter = currencyFormatter()
        val result = formatter.format(BigDecimal("1234567.89"))

        // Forced number locale uses comma grouping: "1,234,567.89"
        assertTrue(
            result.contains("1,234,567"),
            "Large number should have grouping separator: \"$result\""
        )
    }

    @Test
    @SmallTest
    fun currencyFormatter_formatsZero() {
        val formatter = currencyFormatter()
        val result = formatter.format(BigDecimal.ZERO)

        assertEquals("0", result)
    }

    @Test
    @SmallTest
    fun currencyFormatter_forcesPeriodDecimalSeparator() {
        val formatter = currencyFormatter()
        val result = formatter.format(BigDecimal("1234.56"))

        // Regardless of device region, the forced number locale uses a period decimal separator.
        assertTrue(
            result.contains("."),
            "Forced number locale should use a period decimal separator: \"$result\""
        )
    }

    @Test
    @SmallTest
    fun currencyFormatter_nullMaxDecimals_noLimit() {
        val formatter =
            currencyFormatter(
                maximumFractionDigits = null,
                minimumFractionDigits = null
            )

        // When null, the formatter inherits the forced locale's default (3 fraction digits)
        assertEquals(3, formatter.maximumFractionDigits)
    }

    // endregion
}
