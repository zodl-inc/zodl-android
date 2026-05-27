package co.electriccoin.zcash.ui.screen.request.model

import androidx.test.filters.SmallTest
import cash.z.ecc.android.sdk.model.FiatCurrencyConversion
import co.electriccoin.zcash.ui.test.getAppContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Expanded math precision and edge case tests for AmountState currency conversion.
 * Complements [AmountStateTest] which covers basic happy-path cases.
 */
class AmountStateMathTest {
    // region toZecString precision

    @Test
    @SmallTest
    fun toZecString_oneDollarAtPriceHundred_returnsPointZeroOne() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 100.0
            )
        val state = AmountState(amount = "1", currency = RequestCurrency.FIAT, isValid = true)

        val result = state.toZecString(conversion, getAppContext())

        // $1 / $100 per ZEC = 0.01 ZEC
        assertNotNull(result)
        val parsed = result.replace(",", ".").toBigDecimalOrNull()
        assertNotNull(parsed, "Result should be parseable: \"$result\"")
        assertTrue(
            parsed.toDouble() in 0.009..0.011,
            "Expected ~0.01, got: $parsed"
        )
    }

    @Test
    @SmallTest
    fun toZecString_largeFiatAmount_succeeds() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 50.0
            )
        val state = AmountState(amount = "10000", currency = RequestCurrency.FIAT, isValid = true)

        val result = state.toZecString(conversion, getAppContext())

        assertNotNull(result)
        assertFalse(result.isEmpty(), "Large fiat amount should produce a result")
    }

    @Test
    @SmallTest
    fun toZecString_verySmallFiatAmount_succeeds() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 30.0
            )
        val state = AmountState(amount = "0.01", currency = RequestCurrency.FIAT, isValid = true)

        val result = state.toZecString(conversion, getAppContext())

        assertNotNull(result)
        assertFalse(result.isEmpty(), "Small fiat amount should produce a result")
    }

    @Test
    @SmallTest
    fun toZecString_highZecPrice_producesSmallZecAmount() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 10000.0
            )
        val state = AmountState(amount = "10", currency = RequestCurrency.FIAT, isValid = true)

        val result = state.toZecString(conversion, getAppContext())

        // $10 / $10000 = 0.001 ZEC
        val parsed = result.replace(",", ".").toBigDecimalOrNull()
        assertNotNull(parsed, "Result should be parseable: \"$result\"")
        assertTrue(parsed.toDouble() < 0.01, "High price should produce small ZEC: $parsed")
    }

    @Test
    @SmallTest
    fun toZecString_lowZecPrice_producesLargeZecAmount() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 0.5
            )
        val state = AmountState(amount = "10", currency = RequestCurrency.FIAT, isValid = true)

        val result = state.toZecString(conversion, getAppContext())

        // $10 / $0.5 = 20 ZEC
        val parsed = result.replace(",", "").replace(".", "").toBigDecimalOrNull()
        assertNotNull(parsed, "Result should contain a number: \"$result\"")
    }

    // endregion

    // region toZecStringFloored precision

    @Test
    @SmallTest
    fun toZecStringFloored_floorsTruncatesDown() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 33.33
            )
        // $10 / $33.33 = 0.30003... ZEC
        val state = AmountState(amount = "10", currency = RequestCurrency.FIAT, isValid = true)

        val result = state.toZecStringFloored(conversion, getAppContext())

        assertNotNull(result)
        assertFalse(result.contains("ZEC"), "Floored result should not contain ticker: \"$result\"")
    }

    @Test
    @SmallTest
    fun toZecStringFloored_verySmallAmount_doesNotReturnNegative() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 100000.0
            )
        val state = AmountState(amount = "0.01", currency = RequestCurrency.FIAT, isValid = true)

        val result = state.toZecStringFloored(conversion, getAppContext())

        assertNotNull(result)
        assertFalse(result.startsWith("-"), "Result should never be negative: \"$result\"")
    }

    // endregion

    // region toFiatString precision

    @Test
    @SmallTest
    fun toFiatString_oneZecAtPriceHundred_returnsHundred() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 100.0
            )
        val state = AmountState(amount = "1", currency = RequestCurrency.ZEC, isValid = true)

        val result = state.toFiatString(getAppContext(), conversion)

        assertFalse(result.isEmpty(), "1 ZEC at $100 should produce a value")
        assertTrue(result.contains("100"), "Expected $100, got: \"$result\"")
    }

    @Test
    @SmallTest
    fun toFiatString_halfZec_returnsHalfPrice() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 100.0
            )
        val state = AmountState(amount = "0.5", currency = RequestCurrency.ZEC, isValid = true)

        val result = state.toFiatString(getAppContext(), conversion)

        assertFalse(result.isEmpty(), "0.5 ZEC at $100 should produce $50")
        assertTrue(result.contains("50"), "Expected $50, got: \"$result\"")
    }

    @Test
    @SmallTest
    fun toFiatString_verySmallZecAmount_returnsNonEmpty() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 30.0
            )
        val state = AmountState(amount = "0.001", currency = RequestCurrency.ZEC, isValid = true)

        val result = state.toFiatString(getAppContext(), conversion)

        // 0.001 ZEC * $30 = $0.03
        assertFalse(result.isEmpty(), "Small ZEC amount should still produce fiat: \"$result\"")
    }

    @Test
    @SmallTest
    fun toFiatString_largeZecAmount_succeeds() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 30.0
            )
        val state = AmountState(amount = "1000", currency = RequestCurrency.ZEC, isValid = true)

        val result = state.toFiatString(getAppContext(), conversion)

        // 1000 ZEC * $30 = $30,000
        assertFalse(result.isEmpty(), "Large ZEC amount should produce fiat")
    }

    @Test
    @SmallTest
    fun toFiatString_zeroZec_returnsZeroFormatted() {
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = 30.0
            )
        val state = AmountState(amount = "0", currency = RequestCurrency.ZEC, isValid = true)

        val result = state.toFiatString(getAppContext(), conversion)

        assertFalse(result.isEmpty(), "Zero ZEC should produce a formatted value")
    }

    // endregion

    // region Round-trip consistency

    @Test
    @SmallTest
    fun roundTrip_fiatToZecAndBack_isConsistent() {
        val priceOfZec = 45.67
        val conversion =
            FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = priceOfZec
            )

        // Start with $10 fiat
        val fiatState = AmountState(amount = "10", currency = RequestCurrency.FIAT, isValid = true)
        val zecString = fiatState.toZecString(conversion, getAppContext())

        // Now convert that ZEC back to fiat
        val zecState =
            AmountState(
                amount = zecString.replace(",", ".").trim(),
                currency = RequestCurrency.ZEC,
                isValid = true
            )
        val fiatResult = zecState.toFiatString(getAppContext(), conversion)

        // The round-trip should produce something close to $10
        assertFalse(fiatResult.isEmpty(), "Round-trip should produce a value")
        val parsedFiat = fiatResult.replace(",", "").replace(" ", "").toBigDecimalOrNull()
        assertNotNull(parsedFiat, "Round-trip result should be parseable: \"$fiatResult\"")
        assertTrue(
            parsedFiat.toDouble() in 9.0..11.0,
            "Round-trip of \$10 should be within tolerance, got: $parsedFiat"
        )
    }

    // endregion

    // region MemoState validation

    @Test
    @SmallTest
    fun memoState_validMemo_isValid() {
        val memo = MemoState.new("Hello world", "1.0")

        assertTrue(memo.isValid())
        assertEquals("Hello world", memo.text)
        assertEquals("1.0", memo.zecAmount)
    }

    @Test
    @SmallTest
    fun memoState_emptyMemo_isValid() {
        val memo = MemoState.new("", "1.0")

        assertTrue(memo.isValid())
    }

    @Test
    @SmallTest
    fun memoState_tooLongMemo_isInvalid() {
        // Memo max is 512 bytes. Create a string that exceeds it.
        val longMemo = "a".repeat(600)
        val memo = MemoState.new(longMemo, "1.0")

        assertFalse(memo.isValid())
        assertTrue(memo is MemoState.InValid)
    }

    @Test
    @SmallTest
    fun memoState_exactMaxLength_isValid() {
        // ASCII chars are 1 byte each, max is 512
        val maxMemo = "a".repeat(512)
        val memo = MemoState.new(maxMemo, "1.0")

        assertTrue(memo.isValid())
    }

    @Test
    @SmallTest
    fun memoState_unicodeMemo_countsByteSize() {
        // Emoji and non-ASCII characters are multi-byte
        val unicodeMemo = "\uD83D\uDE00".repeat(200) // 200 smiley faces, 4 bytes each = 800 bytes
        val memo = MemoState.new(unicodeMemo, "1.0")

        assertFalse(memo.isValid(), "Unicode memo exceeding 512 bytes should be invalid")
    }

    @Test
    @SmallTest
    fun memoState_byteSize_isTracked() {
        val memo = MemoState.new("Hello", "1.0")

        assertTrue(memo.byteSize > 0, "Byte size should be tracked")
    }

    // endregion
}
