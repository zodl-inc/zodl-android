package co.electriccoin.zcash.ui.design.component

import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The parser always formats/parses with the forced number locale (period decimal, comma grouping),
 * regardless of device region. See MOB-1356 / MOB-1394.
 */
class ZashiNumberTextFieldParserTest {
    private fun assertNumericEquals(
        expected: String,
        actual: BigDecimal?
    ) {
        assertNotNull(actual, "expected $expected but was null")
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")
    }

    // region normalizeInput

    @Test
    fun normalizeInput_convertsTypedCommaToPeriod() {
        // Deceptive keyboard: the user types a comma as the decimal separator.
        assertEquals("1.5", ZashiNumberTextFieldParser.normalizeInput("1,5"))
    }

    @Test
    fun normalizeInput_keepsTypedPeriod() {
        assertEquals("1.5", ZashiNumberTextFieldParser.normalizeInput("1.5"))
    }

    @Test
    fun normalizeInput_stripsAllSpaceVariants() {
        // NO-BREAK, NARROW NO-BREAK and REGULAR spaces are all removed.
        assertEquals("1234.56", ZashiNumberTextFieldParser.normalizeInput("1 2 3 4.56"))
    }

    @Test
    fun normalizeInput_empty_returnsEmpty() {
        assertEquals("", ZashiNumberTextFieldParser.normalizeInput(""))
    }

    // endregion

    // region toBigDecimalOrNull

    @Test
    fun toBigDecimalOrNull_integer() = assertNumericEquals("123", ZashiNumberTextFieldParser.toBigDecimalOrNull("123"))

    @Test
    fun toBigDecimalOrNull_decimal() =
        assertNumericEquals("123.45", ZashiNumberTextFieldParser.toBigDecimalOrNull("123.45"))

    @Test
    fun toBigDecimalOrNull_trailingSeparator() =
        assertNumericEquals("123", ZashiNumberTextFieldParser.toBigDecimalOrNull("123."))

    @Test
    fun toBigDecimalOrNull_leadingSeparator() =
        assertNumericEquals("0.45", ZashiNumberTextFieldParser.toBigDecimalOrNull(".45"))

    @Test
    fun toBigDecimalOrNull_zero() = assertNumericEquals("0", ZashiNumberTextFieldParser.toBigDecimalOrNull("0"))

    @Test
    fun toBigDecimalOrNull_empty_isNull() = assertNull(ZashiNumberTextFieldParser.toBigDecimalOrNull(""))

    @Test
    fun toBigDecimalOrNull_onlySeparator_isNull() = assertNull(ZashiNumberTextFieldParser.toBigDecimalOrNull("."))

    @Test
    fun toBigDecimalOrNull_multipleSeparators_isNull() =
        assertNull(ZashiNumberTextFieldParser.toBigDecimalOrNull("12.34.56"))

    @Test
    fun toBigDecimalOrNull_nonNumeric_isNull() {
        assertNull(ZashiNumberTextFieldParser.toBigDecimalOrNull("abc"))
        assertNull(ZashiNumberTextFieldParser.toBigDecimalOrNull("12a34"))
    }

    // endregion

    // region separator handling matrix

    @Test
    fun normalizeInput_allIdenticalSeparators_areGrouping() {
        // The all-grouping branch: two or more identical separators are all grouping and removed.
        mapOf(
            "1,234,567" to "1234567",
            "1,234,567,890" to "1234567890",
            "1.234.567" to "1234567",
            "12,34,567" to "1234567", // non-uniform group sizes are still pure grouping
            "1,,234" to "1234" // repeated separator
        ).forEach { (input, expected) ->
            assertEquals(expected, ZashiNumberTextFieldParser.normalizeInput(input), "normalizeInput(\"$input\")")
        }
    }

    @Test
    fun normalizeInput_singleSeparator_isDecimal() {
        // Boundary of the all-grouping branch: a lone separator is always the decimal point.
        mapOf(
            "1,5" to "1.5",
            "1.5" to "1.5",
            "1,234" to "1.234",
            "100,000" to "100.000"
        ).forEach { (input, expected) ->
            assertEquals(expected, ZashiNumberTextFieldParser.normalizeInput(input), "normalizeInput(\"$input\")")
        }
    }

    @Test
    fun normalizeInput_mixedSeparators_keepLastAsDecimal() {
        mapOf(
            "1,234.56" to "1234.56",
            "1.234,56" to "1234.56",
            "1,234,567.89" to "1234567.89",
            "1.234.567,89" to "1234567.89",
            "12,34,567.8" to "1234567.8"
        ).forEach { (input, expected) ->
            assertEquals(expected, ZashiNumberTextFieldParser.normalizeInput(input), "normalizeInput(\"$input\")")
        }
    }

    @Test
    fun normalizeInput_trailingSeparator_isDecimalEvenWhenIdenticalToGrouping() {
        // MOB-1356 regression: a separator typed at the end is the freshly entered decimal point, even
        // when it matches earlier separators that would otherwise look like grouping.
        mapOf(
            "1.234." to "1234.", // typing 1 , 2 3 4 . (the typed comma already became ".")
            "1,234," to "1234.", // same shape with comma separators
            "1.234.567." to "1234567."
        ).forEach { (input, expected) ->
            assertEquals(expected, ZashiNumberTextFieldParser.normalizeInput(input), "normalizeInput(\"$input\")")
        }
    }

    @Test
    fun typingDigitsAndSeparators_buildsExpectedDecimal() {
        // Simulates typing 1,234.56 char-by-char: each keystroke re-normalizes the previous display + the new char.
        val keystrokes = listOf("1", ",", "2", "3", "4", ".", "5", "6")
        var display = ""
        keystrokes.forEach { key ->
            display = ZashiNumberTextFieldParser.normalizeInput(display + key)
        }
        assertEquals("1234.56", display)
        assertNumericEquals("1234.56", ZashiNumberTextFieldParser.toBigDecimalOrNull(display))
    }

    @Test
    fun normalizeInput_spacesAreGroupingThenSingleDecimal() {
        // Spaces (regular) are stripped first, leaving a single comma decimal.
        assertEquals("1234567.89", ZashiNumberTextFieldParser.normalizeInput("1 234 567,89"))
    }

    @Test
    fun pastedNumbers_normalizeAndParseToExpectedValue() {
        mapOf(
            "1,234.56" to "1234.56",
            "1.234,56" to "1234.56",
            "1,234,567" to "1234567",
            "1.234.567" to "1234567",
            "1,234,567.89" to "1234567.89"
        ).forEach { (input, expected) ->
            val normalized = ZashiNumberTextFieldParser.normalizeInput(input)
            assertNumericEquals(expected, ZashiNumberTextFieldParser.toBigDecimalOrNull(normalized))
        }
    }

    // endregion
}
