package co.electriccoin.zcash.ui.screen.send.model

import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.StringResource.Companion.NUMBER_FORMAT_LOCALE

/**
 * A max amount computed for [address]. It is only meaningful for that recipient.
 */
data class ResolvedMaxAmount(
    val address: String,
    val zatoshi: Zatoshi,
) {
    /**
     * The form amount for this max. The last edited field is ZEC so the exact max is not floored on its way to the
     * review proposal; the fiat value follows from the exchange rate.
     */
    fun toAmountState(
        isTransparentOrTextRecipient: Boolean,
        exchangeRateState: ExchangeRateState,
    ): AmountState =
        AmountState.newFromZec(
            value = zatoshi.toZecString(NUMBER_FORMAT_LOCALE),
            fiatValue = "",
            isTransparentOrTextRecipient = isTransparentOrTextRecipient,
            exchangeRateState = exchangeRateState,
            lastFieldChangedByUser = AmountField.ZEC,
        )
}
