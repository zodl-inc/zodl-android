package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Zatoshi
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestSwapQuoteUseCaseTest {
    @Test
    fun toExactQuoteZatoshi_acceptsWholeNumber() {
        val result = BigDecimal("123").toExactQuoteZatoshi()

        assertEquals(Zatoshi(123), result)
    }

    @Test
    fun toExactQuoteZatoshi_acceptsEquivalentWholeNumberWithScale() {
        val result = BigDecimal("123.0").toExactQuoteZatoshi()

        assertEquals(Zatoshi(123), result)
    }

    @Test
    fun toExactQuoteZatoshi_rejectsNonIntegralAmount() {
        val exception =
            assertFailsWith<InvalidSwapQuoteAmountException> {
                BigDecimal("123.9").toExactQuoteZatoshi()
            }

        assertEquals(BigDecimal("123.9"), exception.amount)
    }

    @Test
    fun toExactQuoteZatoshi_rejectsOverflow() {
        val exception =
            assertFailsWith<InvalidSwapQuoteAmountException> {
                BigDecimal("9223372036854775808").toExactQuoteZatoshi()
            }

        assertEquals(BigDecimal("9223372036854775808"), exception.amount)
    }
}
