package co.electriccoin.zcash.ui.screen.pay

import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_FEE_BPS
import co.electriccoin.zcash.ui.common.model.floorToScale
import java.math.BigDecimal
import java.math.MathContext

/**
 * The Pay-specific parts of the "Max" chip, which must agree between what the chip shows ([ExactOutputVMMapper]) and
 * what a tap does ([PayVM]). The shared rules are in [co.electriccoin.zcash.ui.common.model.maxChipState].
 */
internal object PayMax {
    /** Decimals the token amount field accepts. */
    const val TOKEN_SCALE = 8

    private const val ZEC_SCALE = 8

    private const val BPS_DIVISOR = 10_000L

    /** Share of the ZEC that remains after the app fee the swap takes on the input (0.67 percent). */
    private val netOfAppFeeFactor: BigDecimal =
        BigDecimal(BPS_DIVISOR - AFFILIATE_FEE_BPS).divide(BigDecimal(BPS_DIVISOR), MathContext.DECIMAL128)

    /** True when both USD prices are usable; Max converts ZEC to the token through USD. */
    fun hasUsablePrices(zecUsdPrice: BigDecimal?, tokenUsdPrice: BigDecimal?): Boolean =
        zecUsdPrice.isPositive() && tokenUsdPrice.isPositive()

    /**
     * floor8(maxZec * (1 - appFee) * zecUsd / tokenUsd), or null if a price is unusable or nothing is left after
     * flooring. The quote for an exact token amount costs that amount plus the app fee, so the first fill leaves it
     * out of the ZEC it converts; otherwise the first quote always costs more than the wallet has.
     */
    fun tokenAmount(maxZec: BigDecimal, zecUsdPrice: BigDecimal?, tokenUsdPrice: BigDecimal?): BigDecimal? {
        if (zecUsdPrice == null || tokenUsdPrice == null || !hasUsablePrices(zecUsdPrice, tokenUsdPrice)) return null
        return maxZec
            .multiply(netOfAppFeeFactor, MathContext.DECIMAL128)
            .multiply(zecUsdPrice, MathContext.DECIMAL128)
            .divide(tokenUsdPrice, MathContext.DECIMAL128)
            .floorToScale(TOKEN_SCALE)
            .takeIf { it > BigDecimal.ZERO }
    }

    /**
     * The ZEC, floored to a zatoshi, that [token] is worth at the current prices: the inverse of [tokenAmount], so
     * `tokenAmount(zecEquivalent(t))` is never above `t`. Null if a price is unusable.
     */
    fun zecEquivalentZatoshi(token: BigDecimal, zecUsdPrice: BigDecimal?, tokenUsdPrice: BigDecimal?): Long? {
        if (zecUsdPrice == null || tokenUsdPrice == null || !hasUsablePrices(zecUsdPrice, tokenUsdPrice)) return null
        return token
            .multiply(tokenUsdPrice, MathContext.DECIMAL128)
            .divide(zecUsdPrice, MathContext.DECIMAL128)
            .divide(netOfAppFeeFactor, MathContext.DECIMAL128)
            .movePointRight(ZEC_SCALE)
            .setScale(0, java.math.RoundingMode.FLOOR)
            .toLong()
    }

    /** True once prices have loaded for a chosen asset yet the USD conversion is unusable ("$ —" in the design). */
    fun isPriceUnavailable(
        arePricesLoaded: Boolean,
        hasAsset: Boolean,
        zecUsdPrice: BigDecimal?,
        tokenUsdPrice: BigDecimal?
    ): Boolean = arePricesLoaded && hasAsset && !hasUsablePrices(zecUsdPrice, tokenUsdPrice)

    private fun BigDecimal?.isPositive() = this != null && this > BigDecimal.ZERO
}

/** One quote the Max Review got: the token amount it was requested for and the ZEC it costs. */
internal data class MaxQuotePoint(
    val token: BigDecimal,
    val zecIn: BigDecimal
)

/**
 * How the Pay Max Review sizes its re-quotes when the quote costs more ZEC than is spendable.
 *
 * The fill the form shows comes from the app's own prices, so the first quote includes what they leave out (provider
 * spread and fees, slippage) and costs more than the wallet has. The re-quote aims [MARGIN] below the cap, so a fixed
 * part of the provider's fee or a small price move between two quotes does not put it over again.
 *
 * - After one quote the token amount is scaled by `target / zecIn` (the cost is taken as proportional).
 * - After two quotes that are both over, the line through them (cost = fixed + proportional * token) gives the next
 *   amount. The fit is used only when it is sound (the token fell and the cost fell, and the result is a smaller
 *   positive amount); otherwise the last quote is scaled again with twice the margin.
 *
 * At most [MAX_REVISIONS] re-quotes follow the first; a quote still over the cap after them stops the Review with the
 * Max failure line.
 */
internal object MaxQuoteSizing {
    /** 0.3 percent of the cap is left unspent by a revised quote: about 0.003 ZEC per 1 ZEC. */
    private val MARGIN = BigDecimal("0.003")

    const val MAX_REVISIONS = 2

    private val TWO = BigDecimal(2)

    /**
     * The token amount to quote next, or null if none is left.
     *
     * @param history the quotes so far, oldest first, each one over [cap]
     * @param cap the most ZEC the quote may cost
     */
    fun nextToken(history: List<MaxQuotePoint>, cap: BigDecimal): BigDecimal? {
        val last = history.lastOrNull()
        return if (last == null || cap <= BigDecimal.ZERO || last.zecIn <= BigDecimal.ZERO) {
            null
        } else {
            val target = cap.multiply(BigDecimal.ONE - MARGIN, MathContext.DECIMAL128)
            val fitted = history.getOrNull(history.size - 2)?.let { fit(it, last, target) }
            val token =
                when {
                    fitted != null -> fitted
                    history.size > 1 -> harderScaled(last, cap)
                    else -> scaled(last, target)
                }
            token.floorToScale(PayMax.TOKEN_SCALE).takeIf { it > BigDecimal.ZERO }
        }
    }

    private fun scaled(last: MaxQuotePoint, target: BigDecimal): BigDecimal =
        last.token.multiply(target, MathContext.DECIMAL128).divide(last.zecIn, MathContext.DECIMAL128)

    private fun fit(prev: MaxQuotePoint, last: MaxQuotePoint, target: BigDecimal): BigDecimal? {
        val dToken = prev.token - last.token
        val dZec = prev.zecIn - last.zecIn
        if (dToken <= BigDecimal.ZERO || dZec <= BigDecimal.ZERO) return null
        val perToken = dZec.divide(dToken, MathContext.DECIMAL128)
        val fitted = last.token + (target - last.zecIn).divide(perToken, MathContext.DECIMAL128)
        return fitted.takeIf { it > BigDecimal.ZERO && it < last.token }
    }

    private fun harderScaled(last: MaxQuotePoint, cap: BigDecimal): BigDecimal =
        scaled(
            last,
            cap.multiply(BigDecimal.ONE - MARGIN.multiply(TWO), MathContext.DECIMAL128)
        )
}
