package co.electriccoin.zcash.ui.screen.pay

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

    /** True when both USD prices are usable; Max converts ZEC to the token through USD. */
    fun hasUsablePrices(zecUsdPrice: BigDecimal?, tokenUsdPrice: BigDecimal?): Boolean =
        zecUsdPrice.isPositive() && tokenUsdPrice.isPositive()

    /**
     * floor8(maxZec * zecUsd / tokenUsd), or null if a price is unusable or nothing is left after flooring.
     */
    fun tokenAmount(maxZec: BigDecimal, zecUsdPrice: BigDecimal?, tokenUsdPrice: BigDecimal?): BigDecimal? {
        if (zecUsdPrice == null || tokenUsdPrice == null || !hasUsablePrices(zecUsdPrice, tokenUsdPrice)) return null
        return maxZec
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
