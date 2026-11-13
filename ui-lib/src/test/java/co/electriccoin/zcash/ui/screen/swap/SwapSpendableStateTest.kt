package co.electriccoin.zcash.ui.screen.swap

import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.asPrivacySensitive
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.balances.BalanceButtonState
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Swap From-card Spendable element is the shipped text label ("Spendable: 5,456.78" / "Spendable: (spinner)"),
 * driven by the shared pill rules, tappable, and converted to USD in FIAT mode as the old label was.
 */
class SwapSpendableStateTest {
    private var clicks = 0

    private fun pill(amount: Zatoshi?) =
        BalanceButtonState(icon = 0, text = stringRes("Spendable"), amount = amount, onClick = { clicks++ })

    @Test
    fun noPillMeansNoLabel() {
        assertNull(swapSpendableState(null, CurrencyType.TOKEN) { BigDecimal.ZERO })
    }

    @Test
    fun anUnknownAmountIsTheLoadingLabel() {
        val state = swapSpendableState(pill(null), CurrencyType.TOKEN) { BigDecimal.ZERO }

        assertEquals(stringRes(R.string.balance_availableTitle), state?.text)
        assertTrue(state?.isLoading == true)
    }

    @Test
    fun anAmountIsTheSpendableLabelInZec() {
        val amount = Zatoshi(545_678_000_000)
        val state = swapSpendableState(pill(amount), CurrencyType.TOKEN) { BigDecimal.ZERO }

        assertEquals(
            stringRes(R.string.swapAndPay_max, stringRes(amount, TickerLocation.HIDDEN).asPrivacySensitive()),
            state?.text
        )
        assertFalse(state?.isLoading == true)
    }

    @Test
    fun fiatModeShowsTheUsdValueOfTheSpendableAmount() {
        // android.icu is not available in plain unit tests
        mockkObject(FiatCurrency.USD)
        every { FiatCurrency.USD.symbol } returns "$"
        try {
            val state = swapSpendableState(pill(Zatoshi(100_000_000)), CurrencyType.FIAT) { BigDecimal("30") }

            assertEquals(
                stringRes(
                    R.string.swapAndPay_max,
                    stringResByDynamicCurrencyNumber(BigDecimal("30"), "$").asPrivacySensitive()
                ),
                state?.text
            )
        } finally {
            unmockkObject(FiatCurrency.USD)
        }
    }

    @Test
    fun tappingTheLabelRunsThePillClick() {
        swapSpendableState(pill(Zatoshi(1)), CurrencyType.TOKEN) { BigDecimal.ZERO }?.onClick?.invoke()

        assertEquals(1, clicks)
    }
}
