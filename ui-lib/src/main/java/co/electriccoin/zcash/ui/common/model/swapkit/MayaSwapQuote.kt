package co.electriccoin.zcash.ui.common.model.swapkit

import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.ZERO
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapProvider
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.isZCashAsset
import java.math.BigDecimal
import java.math.MathContext
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * A [SwapQuote] built in `MayaSwapDataSource.requestQuote` from `/v3/quote` + `/v3/swap` + request inputs +
 * loaded assets. Maya is exact-input only ([SwapMode.EXACT_INPUT]). Several values NEAR returns directly are
 * derived here: USD amounts from the supplied quote-time prices, base-unit amounts by shifting the formatted
 * decimals, and the affiliate split from the dashboard-configured bps. [deadline] is synthesized
 * (`timestamp + 2h`) — Maya's only TTL is the ~60s route expiry, too short to reuse.
 */
class MayaSwapQuote(
    override val originAsset: SwapAsset,
    override val destinationAsset: SwapAsset,
    override val depositAddress: SwapAddress,
    override val destinationAddress: SwapAddress,
    override val refundAddress: SwapAddress,
    override val amountInFormatted: BigDecimal,
    override val amountOutFormatted: BigDecimal,
    override val slippage: BigDecimal,
    override val timestamp: Instant,
    private val originUsdPrice: BigDecimal,
    private val destinationUsdPrice: BigDecimal,
    private val affiliateFeeBps: Int,
    /** The Maya swap-instruction memo from `/v3/swap`. Phase 2 attaches it as the deposit OP_RETURN. */
    val memo: String?,
) : SwapQuote {
    init {
        require(amountInFormatted.signum() > 0) {
            "Swap quote has non-positive amountInFormatted=$amountInFormatted"
        }
    }

    override val provider = SwapProvider.MAYA

    override val mode = SwapMode.EXACT_INPUT

    override val amountIn: BigDecimal = amountInFormatted.movePointRight(originAsset.decimals)
    override val amountInUsd: BigDecimal = amountInFormatted.multiply(originUsdPrice, MathContext.DECIMAL128)

    override val amountOut: BigDecimal = amountOutFormatted.movePointRight(destinationAsset.decimals)
    override val amountOutUsd: BigDecimal = amountOutFormatted.multiply(destinationUsdPrice, MathContext.DECIMAL128)

    override val zecExchangeRate: BigDecimal = amountInUsd.divide(amountInFormatted, MathContext.DECIMAL128)

    private val affiliateRate: BigDecimal =
        BigDecimal(affiliateFeeBps).divide(BigDecimal("10000"), MathContext.DECIMAL128)

    override val affiliateFee: BigDecimal = amountInFormatted.multiply(affiliateRate, MathContext.DECIMAL128)

    override val affiliateFeeZatoshi: Zatoshi =
        when {
            originAsset.isZCashAsset -> {
                amountInFormatted
                    .coerceAtLeast(BigDecimal.ZERO)
                    .multiply(affiliateRate, MathContext.DECIMAL128)
                    .convertZecToZatoshi()
            }

            zecExchangeRate.signum() > 0 -> {
                amountOutUsd
                    .coerceAtLeast(BigDecimal.ZERO)
                    .multiply(affiliateRate, MathContext.DECIMAL128)
                    .divide(zecExchangeRate, MathContext.DECIMAL128)
                    .convertZecToZatoshi()
            }

            else -> {
                Zatoshi.ZERO
            }
        }

    override val affiliateFeeUsd: BigDecimal =
        amountInUsd.coerceAtLeast(BigDecimal.ZERO).multiply(affiliateRate, MathContext.DECIMAL128)

    override val deadline: Instant = timestamp + 2.hours

    override fun getTotal(proposal: Proposal?) = amountInFormatted + (getZecFee(proposal) ?: BigDecimal.ZERO)

    override fun getTotalUsd(proposal: Proposal?) = amountInUsd + getZecFeeUsd(proposal)

    override fun getTotalFeesUsd(proposal: Proposal?) = affiliateFeeUsd + getZecFeeUsd(proposal)

    override fun getTotalFeesZatoshi(proposal: Proposal?): Zatoshi =
        (proposal?.totalFeeRequired() ?: Zatoshi.ZERO) + affiliateFeeZatoshi

    private fun getZecFee(proposal: Proposal?): BigDecimal? = proposal?.totalFeeRequired()?.convertZatoshiToZec()

    private fun getZecFeeUsd(proposal: Proposal?): BigDecimal =
        zecExchangeRate.multiply(getZecFee(proposal) ?: BigDecimal.ZERO, MathContext.DECIMAL128)
}
