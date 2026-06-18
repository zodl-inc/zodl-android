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

    // region normalize + parse end-to-end

    @Test
    fun commaInput_normalizesAndParses() {
        val normalized = ZashiNumberTextFieldParser.normalizeInput("1,5")
        assertNumericEquals("1.5", ZashiNumberTextFieldParser.toBigDecimalOrNull(normalized))
    }

    @Test
    fun pastedUsGrouped_dropsGroupingKeepsDecimal() {
        // "1,234.56": last separator '.' is the decimal; the earlier ',' grouping is removed.
        assertEquals("1234.56", ZashiNumberTextFieldParser.normalizeInput("1,234.56"))
        assertNumericEquals(
            "1234.56",
            ZashiNumberTextFieldParser.toBigDecimalOrNull(ZashiNumberTextFieldParser.normalizeInput("1,234.56"))
        )
    }

    @Test
    fun pastedEuropeanGrouped_dropsGroupingKeepsDecimal() {
        // "1.234,56": last separator ',' is the decimal; the earlier '.' grouping is removed.
        assertEquals("1234.56", ZashiNumberTextFieldParser.normalizeInput("1.234,56"))
    }

    @Test
    fun pastedMultiGroup_dropsAllGroupingKeepsDecimal() {
        assertEquals("1234567.89", ZashiNumberTextFieldParser.normalizeInput("1,234,567.89"))
    }

    @Test
    fun pastedGroupedInteger_dropsAllGrouping() {
        // All separators identical → pure grouping, no decimal part.
        assertEquals("1234567", ZashiNumberTextFieldParser.normalizeInput("1,234,567"))
        assertNumericEquals(
            "1234567",
            ZashiNumberTextFieldParser.toBigDecimalOrNull(ZashiNumberTextFieldParser.normalizeInput("1,234,567"))
        )
    }

    @Test
    fun pastedDotGroupedInteger_dropsAllGrouping() {
        // European dot grouping, all identical → "1234567".
        assertEquals("1234567", ZashiNumberTextFieldParser.normalizeInput("1.234.567"))
    }

    // endregion
}
