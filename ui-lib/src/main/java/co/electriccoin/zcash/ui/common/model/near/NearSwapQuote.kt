package co.electriccoin.zcash.ui.common.model.near

import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.ZERO
import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_FEE_BPS
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.ZecSwapAsset
import co.electriccoin.zcash.ui.common.model.isSame
import java.math.BigDecimal
import java.math.MathContext
import kotlin.time.Instant

data class NearSwapQuote(
    val response: QuoteResponseDto,
    override val originAsset: SwapAsset,
    override val destinationAsset: SwapAsset,
    override val depositAddress: SwapAddress,
    override val destinationAddress: SwapAddress,
    override val refundAddress: SwapAddress,
) : SwapQuote {
    init {
        require(response.quoteRequest.originAsset == originAsset.assetId) {
            "Swap quote asset mismatch: requested originAsset=${originAsset.assetId} " +
                "but server returned ${response.quoteRequest.originAsset}"
        }
        require(response.quoteRequest.destinationAsset == destinationAsset.assetId) {
            "Swap quote asset mismatch: requested destinationAsset=${destinationAsset.assetId} " +
                "but server returned ${response.quoteRequest.destinationAsset}"
        }
        requireConsistent(
            name = "amountIn",
            raw = response.quote.amountIn,
            formatted = response.quote.amountInFormatted,
            decimals = originAsset.decimals
        )
        requireConsistent(
            name = "amountOut",
            raw = response.quote.amountOut,
            formatted = response.quote.amountOutFormatted,
            decimals = destinationAsset.decimals
        )
        requireWithinSlippage()
    }

    /**
     * Fail-closed slippage check on the server-determined (floating) side of the quote. The user-fixed
     * side is already pinned by requireQuoteMatchesUserAmount in the use case; this asserts the server's
     * own worst-case guarantee (minAmountOut / minAmountIn) does not exceed the slippage the user
     * requested. Only enforced when the server actually returns the bound — see the nullability note in
     * QuoteDetails.
     */
    private fun requireWithinSlippage() {
        val slippageFraction =
            BigDecimal(response.quoteRequest.slippageTolerance)
                .divide(BigDecimal("10000"), MathContext.DECIMAL128)
        when (response.quoteRequest.swapType) {
            // Output floats: the guaranteed minimum out must not be worse than amountOut * (1 - slippage).
            SwapType.EXACT_INPUT, SwapType.FLEX_INPUT -> {
                response.quote.minAmountOut?.let { min ->
                    val floor =
                        response.quote.amountOut.multiply(BigDecimal.ONE - slippageFraction, MathContext.DECIMAL128)
                    require(min >= floor) {
                        "Swap slippage exceeded: server-guaranteed minAmountOut=$min is below the slippage " +
                            "floor=$floor (amountOut=${response.quote.amountOut}, slippage=$slippageFraction)"
                    }
                }
            }

            // Input floats: the guaranteed input must not exceed amountIn * (1 + slippage).
            SwapType.EXACT_OUTPUT -> {
                response.quote.minAmountIn?.let { min ->
                    val ceiling =
                        response.quote.amountIn.multiply(BigDecimal.ONE + slippageFraction, MathContext.DECIMAL128)
                    require(min <= ceiling) {
                        "Swap slippage exceeded: server-guaranteed minAmountIn=$min is above the slippage " +
                            "ceiling=$ceiling (amountIn=${response.quote.amountIn}, slippage=$slippageFraction)"
                    }
                }
            }

            null -> {
                // do nothing
            }
        }
    }

    override val slippage: BigDecimal =
        BigDecimal(response.quoteRequest.slippageTolerance)
            .divide(BigDecimal("100", MathContext.DECIMAL128))

    override val provider = "near"

    override val mode: SwapMode =
        when (response.quoteRequest.swapType) {
            SwapType.FLEX_INPUT -> SwapMode.FLEX_INPUT
            SwapType.EXACT_INPUT -> SwapMode.EXACT_INPUT
            SwapType.EXACT_OUTPUT -> SwapMode.EXACT_OUTPUT
            null -> SwapMode.EXACT_INPUT
        }

    override val zecExchangeRate: BigDecimal =
        response.quote.amountInUsd
            .divide(response.quote.amountInFormatted, MathContext.DECIMAL128)

    override val amountIn: BigDecimal = response.quote.amountIn
    override val amountInFormatted: BigDecimal = response.quote.amountInFormatted
    override val amountInUsd: BigDecimal = response.quote.amountInUsd

    override val amountOut: BigDecimal = response.quote.amountOut
    override val amountOutUsd: BigDecimal = response.quote.amountOutUsd
    override val amountOutFormatted: BigDecimal = response.quote.amountOutFormatted

    override val affiliateFee: BigDecimal =
        response.quote.amountInFormatted
            .multiply(
                BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                MathContext.DECIMAL128
            )

    override val affiliateFeeZatoshi: Zatoshi =
        if (originAsset is ZecSwapAsset) {
            response.quote.amountInFormatted
                .coerceAtLeast(BigDecimal(0))
                .multiply(
                    BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                    MathContext.DECIMAL128
                ).convertZecToZatoshi()
        } else {
            response.quote.amountOutUsd
                .coerceAtLeast(BigDecimal(0))
                .multiply(
                    BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                    MathContext.DECIMAL128
                ).divide(zecExchangeRate, MathContext.DECIMAL128)
                .convertZecToZatoshi()
        }

    override val affiliateFeeUsd: BigDecimal =
        response.quote.amountInUsd
            .coerceAtLeast(BigDecimal(0))
            .multiply(
                BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                MathContext.DECIMAL128
            )

    override val timestamp: Instant = response.timestamp

    override val deadline: Instant = response.quote.deadline

    override fun getTotal(proposal: Proposal?) = amountInFormatted + (getZecFee(proposal) ?: BigDecimal.ZERO)

    override fun getTotalUsd(proposal: Proposal?) = amountInUsd + getZecFeeUsd(proposal)

    override fun getTotalFeesUsd(proposal: Proposal?) = affiliateFeeUsd + getZecFeeUsd(proposal)

    override fun getTotalFeesZatoshi(proposal: Proposal?): Zatoshi =
        (proposal?.totalFeeRequired() ?: Zatoshi.ZERO) + affiliateFeeZatoshi

    private fun getZecFee(proposal: Proposal?): BigDecimal? = proposal?.totalFeeRequired()?.convertZatoshiToZec()

    private fun getZecFeeUsd(proposal: Proposal?): BigDecimal =
        zecExchangeRate.multiply(getZecFee(proposal) ?: BigDecimal.ZERO, MathContext.DECIMAL128)
}

internal fun requireConsistent(name: String, raw: BigDecimal?, formatted: BigDecimal?, decimals: Int) {
    if (raw == null || formatted == null) return
    require(raw.compareTo(formatted.movePointRight(decimals)) == 0) {
        "Swap amount inconsistency: $name ($raw) does not match ${name}Formatted ($formatted) at $decimals decimals"
    }
}

internal fun requireMatchingAsset(name: String, expected: SimpleSwapAsset, actual: SwapAsset) {
    require(actual.isSame(expected.tokenTicker, expected.chainTicker)) {
        "Swap asset mismatch: expected $name=${expected.tokenTicker}/${expected.chainTicker} " +
            "but server returned ${actual.tokenTicker}/${actual.chainTicker}"
    }
}
