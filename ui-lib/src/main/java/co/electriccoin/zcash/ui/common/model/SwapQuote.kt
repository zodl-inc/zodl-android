package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.ZERO
import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_FEE_BPS
import co.electriccoin.zcash.ui.common.model.near.QuoteResponseDto
import co.electriccoin.zcash.ui.common.model.near.SwapType.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.near.SwapType.EXACT_OUTPUT
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.math.MathContext

interface SwapQuote {
    val originAsset: SwapAsset
    val destinationAsset: SwapAsset
    val depositAddress: SwapAddress
    val destinationAddress: SwapAddress
    val refundAddress: SwapAddress
    val provider: String
    val mode: SwapMode
    val zecExchangeRate: BigDecimal

    val amountIn: BigDecimal
    val amountInFormatted: BigDecimal
    val amountInUsd: BigDecimal

    val amountOut: BigDecimal
    val amountOutUsd: BigDecimal
    val amountOutFormatted: BigDecimal

    val affiliateFee: BigDecimal
    val affiliateFeeZatoshi: Zatoshi
    val affiliateFeeUsd: BigDecimal

    val timestamp: Instant
    val deadline: Instant

    val slippage: BigDecimal

    fun getTotal(proposal: Proposal?): BigDecimal

    fun getTotalUsd(proposal: Proposal?): BigDecimal

    fun getTotalFeesUsd(proposal: Proposal?): BigDecimal

    fun getTotalFeesZatoshi(proposal: Proposal?): Zatoshi
}

data class NearSwapQuote(
    val response: QuoteResponseDto,
    override val originAsset: SwapAsset,
    override val destinationAsset: SwapAsset,
    override val depositAddress: SwapAddress,
    override val destinationAddress: SwapAddress,
    override val refundAddress: SwapAddress,
) : SwapQuote {
    override val slippage: BigDecimal =
        BigDecimal(response.quoteRequest.slippageTolerance)
            .divide(BigDecimal("100", MathContext.DECIMAL128))

    override val provider = "near"

    override val mode: SwapMode =
        when (response.quoteRequest.swapType) {
            EXACT_INPUT -> SwapMode.EXACT_INPUT
            EXACT_OUTPUT -> SwapMode.EXACT_OUTPUT
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
