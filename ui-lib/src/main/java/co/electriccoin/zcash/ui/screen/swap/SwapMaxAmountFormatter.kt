package co.electriccoin.zcash.ui.screen.swap

import cash.z.ecc.android.sdk.ext.toZecExact
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.floorToScale
import java.math.BigDecimal

/**
 * The Swap-specific parts of the "Max" chip; the shared rules are in
 * [co.electriccoin.zcash.ui.common.model.maxChipState]. Nothing here ever rounds up: the From field is converted
 * back into ZEC by the insufficient-funds check, so a rounded-up value would flag a just-tapped Max as over balance.
 */
internal object SwapMaxAmountFormatter {
    private const val USD_SCALE = 2

    /**
     * The value to put into the From field for [maxAmount], or null if it cannot be computed: the exact ZEC in
     * [CurrencyType.TOKEN] mode, `maxZec * zecUsdPrice` floored to cents in [CurrencyType.FIAT] mode, which requires
     * a ZEC price above zero.
     */
    fun format(maxAmount: Zatoshi, currencyType: CurrencyType, zecUsdPrice: BigDecimal?): BigDecimal? =
        when (currencyType) {
            CurrencyType.TOKEN -> {
                maxAmount.toZecExact()
            }

            CurrencyType.FIAT -> {
                zecUsdPrice?.takeIf { it.signum() > 0 }?.let { price ->
                    maxAmount.toZecExact().multiply(price).floorToScale(USD_SCALE)
                }
            }
        }

    /**
     * The Swap conditions for Max, on top of a spendable balance: no quote request running and, in FIAT mode, a
     * ZEC price above zero.
     */
    fun isMaxUsable(
        isRequestingQuote: Boolean,
        currencyType: CurrencyType,
        zecUsdPrice: BigDecimal?
    ): Boolean = !isRequestingQuote && (currencyType == CurrencyType.TOKEN || (zecUsdPrice?.signum() ?: 0) > 0)

    /**
     * The `adjust` the Max fill passes to [co.electriccoin.zcash.ui.common.viewmodel.MaxFillPolicy.refresh] when a
     * [quote] refreshes: the quote's ZEC-side deduction from the amount sent.
     *
     * It reads no quote field, and is the identity, because a From-ZEC quote carries no ZEC-side deduction from the
     * amount the user enters:
     * - `amountInFormatted` is the amount sent, and the repository rejects a quote whose value differs from the
     *   user's amount (`requireQuoteMatchesUserAmount`);
     * - the provider fee ([SwapQuote.affiliateFee], e.g. "Including a 0.85% Zcash fee") is a share of that amount, not
     *   an addition to it, and the quote total adds only the network fee of the proposal;
     * - slippage (`minAmountOut`) lowers what the recipient gets, not the ZEC sent;
     * - the quote has no maximum deposit field, only `minAmountIn`, which a Max fill is never below.
     * The network fee and the marginal-fee buffer are already inside the baseline pinned at the tap. The seam stays
     * here so a quote field that does deduct ZEC has one place to land.
     */
    @Suppress("UNUSED_PARAMETER")
    fun quoteAdjust(quote: SwapQuote?): (Zatoshi) -> Zatoshi = { it }
}
