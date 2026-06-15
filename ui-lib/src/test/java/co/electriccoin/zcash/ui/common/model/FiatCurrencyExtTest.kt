package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.FiatCurrency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Invariants for the app-side supported-currency list backing the currency conversion picker
 * (MOB-1124). The list is product data, so these guard against accidental edits.
 */
class FiatCurrencyExtTest {
    @Test
    fun usdIsTheFirstCurrency() {
        // USD is the default and must lead the display order.
        assertEquals(FiatCurrency.USD, supportedFiatCurrencies.first())
    }

    @Test
    fun containsTheExpectedCurrencyCount() {
        assertEquals(EXPECTED_CURRENCY_COUNT, supportedFiatCurrencies.size)
    }

    @Test
    fun hasNoDuplicateCurrencies() {
        val codes = supportedFiatCurrencies.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "supportedFiatCurrencies contains duplicates: $codes")
    }

    @Test
    fun allCodesAreValidIso4217Alpha3() {
        supportedFiatCurrencies.forEach {
            assertTrue(FiatCurrency.isAlpha3Code(it.code), "${it.code} is not a valid ISO 4217 alpha-3 code")
        }
    }
}

private const val EXPECTED_CURRENCY_COUNT = 23
