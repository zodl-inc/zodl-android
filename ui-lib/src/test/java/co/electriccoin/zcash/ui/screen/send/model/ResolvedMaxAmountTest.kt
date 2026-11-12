package co.electriccoin.zcash.ui.screen.send.model

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResolvedMaxAmountTest {
    @Test
    fun appliesTheExactMaxWithZecAsTheLastEditedField() {
        val state =
            ResolvedMaxAmount("u1", Zatoshi(123_456_789)).toAmountState(
                isTransparentOrTextRecipient = false,
                exchangeRateState = ExchangeRateState.OptedOut
            )

        val valid = assertIs<AmountState.Valid>(state)
        assertEquals(Zatoshi(123_456_789), valid.zatoshi)
        assertEquals(AmountField.ZEC, valid.lastFieldChangedByUser)
    }

    @Test
    fun amountsAboveOneThousandZecSurviveGrouping() {
        val state =
            ResolvedMaxAmount("t1", Zatoshi(123_456_789_012)).toAmountState(
                isTransparentOrTextRecipient = true,
                exchangeRateState = ExchangeRateState.OptedOut
            )

        assertEquals(Zatoshi(123_456_789_012), assertIs<AmountState.Valid>(state).zatoshi)
    }
}
