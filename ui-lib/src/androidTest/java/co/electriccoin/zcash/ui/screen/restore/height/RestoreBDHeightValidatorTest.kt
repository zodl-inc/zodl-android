package co.electriccoin.zcash.ui.screen.restore.height

import androidx.test.filters.SmallTest
import co.electriccoin.zcash.ui.design.component.InnerTextFieldState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.TextSelection
import co.electriccoin.zcash.ui.design.util.stringRes
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestoreBDHeightValidatorTest {
    @Test
    @SmallTest
    fun empty_input_has_no_error_and_is_not_valid() {
        val actual = RestoreBDHeightValidator.validate(newState("", null), SAPLING_ACTIVATION_HEIGHT)

        assertFalse(actual.isValid)
        assertNull(actual.error)
        assertNull(actual.blockHeight)
    }

    @Test
    @SmallTest
    fun invalid_separator_only_input_returns_integer_error() {
        val actual = RestoreBDHeightValidator.validate(newState(".", null), SAPLING_ACTIVATION_HEIGHT)

        assertEquals(RestoreBDHeightValidationError.INVALID_INTEGER, actual.error)
        assertFalse(actual.isValid)
    }

    @Test
    @SmallTest
    fun decimal_input_returns_integer_error() {
        val actual =
            RestoreBDHeightValidator.validate(
                newState("123.9", BigDecimal("123.9")),
                SAPLING_ACTIVATION_HEIGHT
            )

        assertEquals(RestoreBDHeightValidationError.INVALID_INTEGER, actual.error)
        assertFalse(actual.isValid)
    }

    @Test
    @SmallTest
    fun trailing_decimal_input_returns_integer_error() {
        val actual =
            RestoreBDHeightValidator.validate(
                newState("123.", BigDecimal("123")),
                SAPLING_ACTIVATION_HEIGHT
            )

        assertEquals(RestoreBDHeightValidationError.INVALID_INTEGER, actual.error)
        assertFalse(actual.isValid)
    }

    @Test
    @SmallTest
    fun decimal_with_zero_fraction_returns_integer_error() {
        val actual =
            RestoreBDHeightValidator.validate(
                newState("123.0", BigDecimal("123.0")),
                SAPLING_ACTIVATION_HEIGHT
            )

        assertEquals(RestoreBDHeightValidationError.INVALID_INTEGER, actual.error)
        assertFalse(actual.isValid)
    }

    @Test
    @SmallTest
    fun lower_than_sapling_activation_height_returns_minimum_error() {
        val actual =
            RestoreBDHeightValidator.validate(
                newState("419199", BigDecimal("419199")),
                SAPLING_ACTIVATION_HEIGHT
            )

        assertEquals(RestoreBDHeightValidationError.BELOW_SAPLING_ACTIVATION, actual.error)
        assertFalse(actual.isValid)
    }

    @Test
    @SmallTest
    fun sapling_activation_height_is_valid() {
        val actual =
            RestoreBDHeightValidator.validate(
                newState("419200", BigDecimal("419200")),
                SAPLING_ACTIVATION_HEIGHT
            )

        assertTrue(actual.isValid)
        assertEquals(419200L, actual.blockHeight)
        assertNull(actual.error)
    }

    @Test
    @SmallTest
    fun max_long_value_is_valid() {
        val actual =
            RestoreBDHeightValidator.validate(
                newState(Long.MAX_VALUE.toString(), BigDecimal(Long.MAX_VALUE)),
                SAPLING_ACTIVATION_HEIGHT
            )

        assertTrue(actual.isValid)
        assertEquals(Long.MAX_VALUE, actual.blockHeight)
        assertNull(actual.error)
    }

    @Test
    @SmallTest
    fun larger_than_long_max_returns_integer_error() {
        val actual =
            RestoreBDHeightValidator.validate(
                newState("9223372036854775808", BigDecimal("9223372036854775808")),
                SAPLING_ACTIVATION_HEIGHT
            )

        assertEquals(RestoreBDHeightValidationError.INVALID_INTEGER, actual.error)
        assertFalse(actual.isValid)
    }

    private fun newState(
        inputText: String,
        amount: BigDecimal?,
    ) = NumberTextFieldInnerState(
        innerTextFieldState =
            InnerTextFieldState(
                value = stringRes(inputText),
                selection = TextSelection.Start
            ),
        amount = amount,
        lastValidAmount = amount
    )

    private companion object {
        private const val SAPLING_ACTIVATION_HEIGHT = 419200L
    }
}
