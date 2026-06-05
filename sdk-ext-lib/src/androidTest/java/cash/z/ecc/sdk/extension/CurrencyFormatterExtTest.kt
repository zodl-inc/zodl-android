package cash.z.ecc.sdk.extension

import androidx.test.filters.SmallTest
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CurrencyFormatterExtTest {
    // region zatoshiFormatter

    @Test
    @SmallTest
    fun zatoshiFormatter_usLocale_formatsCorrectly() {
        val formatter = zatoshiFormatter(Locale.US)
        val result = formatter.format(BigDecimal("1.23456789"))

        assertNotNull(result)
        assertTrue(result.contains("1"), "Should contain the integer part: \"$result\"")
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_maxFractionDigits_isEight() {
        val formatter = zatoshiFormatter(Locale.US)

        assertEquals(8, formatter.maximumFractionDigits)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_minFractionDigits_isThree() {
        val formatter = zatoshiFormatter(Locale.US)

        assertEquals(3, formatter.minimumFractionDigits)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_roundingMode_isHalfEven() {
        val formatter = zatoshiFormatter(Locale.US)

        assertEquals(RoundingMode.HALF_EVEN, formatter.roundingMode)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_preservesPrecision_eightDecimals() {
        val formatter = zatoshiFormatter(Locale.US)
        val result = formatter.format(BigDecimal("0.12345678"))

        assertEquals("0.12345678", result)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_truncatesNinthDecimal() {
        val formatter = zatoshiFormatter(Locale.US)
        val result = formatter.format(BigDecimal("0.123456789"))

        // HALF_EVEN rounds 9th decimal: 0.123456789 -> 0.12345679
        assertEquals("0.12345679", result)
    }

    @Test
    @SmallTest
    fun zatoshiFormatter_padsToThreeDecimals() {
        val formatter = zatoshiFormatter(Locale.US)
        val result = formatter.format(BigDecimal("1"))

        // Should show "1.000" (3 min decimals)
        assertEquals("1.000", result)
    }

    // endregion

    // region currencyFormatter

    @Test
    @SmallTest
    fun currencyFormatter_defaultParams_maxSixDecimals() {
        val formatter = currencyFormatter(Locale.US)

        assertEquals(6, formatter.maximumFractionDigits)
    }

    @Test
    @SmallTest
    fun currencyFormatter_defaultParams_minZeroDecimals() {
        val formatter = currencyFormatter(Locale.US)

        assertEquals(0, formatter.minimumFractionDigits)
    }

    @Test
    @SmallTest
    fun currencyFormatter_customMaxDecimals() {
        val formatter =
            currencyFormatter(
                locale = Locale.US,
                maximumFractionDigits = 2,
                minimumFractionDigits = 2
            )

        assertEquals(2, formatter.maximumFractionDigits)
        assertEquals(2, formatter.minimumFractionDigits)
    }

    @Test
    @SmallTest
    fun currencyFormatter_formatsLargeNumber_withGrouping() {
        val formatter = currencyFormatter(Locale.US)
        val result = formatter.format(BigDecimal("1234567.89"))

        // US locale should use comma grouping: "1,234,567.89"
        assertTrue(
            result.contains("1,234,567"),
            "Large number should have grouping separator: \"$result\""
        )
    }

    @Test
    @SmallTest
    fun currencyFormatter_formatsZero() {
        val formatter = currencyFormatter(Locale.US)
        val result = formatter.format(BigDecimal.ZERO)

        assertEquals("0", result)
    }

    @Test
    @SmallTest
    fun currencyFormatter_germanLocale_usesCommaDecimal() {
        val formatter = currencyFormatter(Locale.GERMANY)
        val result = formatter.format(BigDecimal("1234.56"))

        // German locale uses comma as decimal separator
        assertTrue(
            result.contains(","),
            "German locale should use comma as decimal separator: \"$result\""
        )
    }

    @Test
    @SmallTest
    fun currencyFormatter_nullMaxDecimals_noLimit() {
        val formatter =
            currencyFormatter(
                locale = Locale.US,
                maximumFractionDigits = null,
                minimumFractionDigits = null
            )

        // When null, the formatter inherits US locale's default (3 fraction digits)
        assertEquals(3, formatter.maximumFractionDigits)
    }

    // endregion

    // region ZcashDecimalFormatSymbols

    @Test
    @SmallTest
    fun zcashDecimalFormatSymbols_usLocale_hasDecimalSeparator() {
        val symbols = ZcashDecimalFormatSymbols(Locale.US)

        assertEquals('.', symbols.decimalSeparator)
    }

    @Test
    @SmallTest
    fun zcashDecimalFormatSymbols_germanLocale_hasCommaDecimalSeparator() {
        val symbols = ZcashDecimalFormatSymbols(Locale.GERMANY)

        assertEquals(',', symbols.decimalSeparator)
    }

    @Test
    @SmallTest
    fun zcashDecimalFormatSymbols_usLocale_hasGroupingSeparator() {
        val symbols = ZcashDecimalFormatSymbols(Locale.US)

        // US locale uses comma as grouping separator
        assertEquals(',', symbols.groupingSeparator, "US locale should have comma as grouping separator")
    }

    // endregion
}
