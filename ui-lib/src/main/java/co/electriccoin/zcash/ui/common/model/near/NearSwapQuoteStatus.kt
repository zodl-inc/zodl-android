package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_FEE_BPS
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant
import co.electriccoin.zcash.ui.common.model.SwapStatus as ModelSwapStatus

data class NearSwapQuoteStatus(
    val response: SwapStatusResponseDto,
    val origin: SwapAsset,
    val destination: SwapAsset,
    val depositAddress: SwapAddress,
    val destinationAddress: SwapAddress,
    val refundAddress: SwapAddress,
) : SwapQuoteStatus {
    init {
        val details = response.swapDetails
        if (details != null) {
            // zecExchangeRate divides by the effective amountInFormatted (swapDetails value when present),
            // so reject a non-positive server-supplied value to avoid a divide-by-zero. A null value is
            // fine: it falls back to the quote's amountInFormatted, which is already guarded.
            require(details.amountInFormatted == null || details.amountInFormatted.signum() > 0) {
                "Swap status has non-positive amountInFormatted=${details.amountInFormatted}"
            }
            requireConsistent(
                name = "amountIn",
                raw = details.amountIn,
                formatted = details.amountInFormatted,
                decimals = origin.decimals
            )
            requireConsistent(
                name = "amountOut",
                raw = details.amountOut,
                formatted = details.amountOutFormatted,
                decimals = destination.decimals
            )
        }
    }

    override val quote: SwapQuote =
        NearSwapQuote(
            response = response.quoteResponse,
            originAsset = origin,
            destinationAsset = destination,
            depositAddress = depositAddress,
            destinationAddress = destinationAddress,
            refundAddress = refundAddress,
        )

    override val timestamp: Instant = response.quoteResponse.timestamp.toJavaInstant()

    override val originAssetId: String = origin.assetId
    override val destinationAssetId: String = destination.assetId

    override val status: ModelSwapStatus
        get() =
            if (
                response.status == SwapStatus.PENDING_DEPOSIT &&
                Instant.now() > (response.quoteResponse.quote.deadline - 5.minutes).toJavaInstant()
            ) {
                ModelSwapStatus.EXPIRED
            } else {
                when (response.status) {
                    SwapStatus.KNOWN_DEPOSIT_TX -> ModelSwapStatus.PENDING
                    SwapStatus.PENDING_DEPOSIT -> ModelSwapStatus.PENDING
                    SwapStatus.INCOMPLETE_DEPOSIT -> ModelSwapStatus.INCOMPLETE_DEPOSIT
                    SwapStatus.PROCESSING -> ModelSwapStatus.PROCESSING
                    SwapStatus.SUCCESS -> ModelSwapStatus.SUCCESS
                    SwapStatus.REFUNDED -> ModelSwapStatus.REFUNDED
                    SwapStatus.FAILED -> ModelSwapStatus.FAILED
                    null -> ModelSwapStatus.PENDING
                }
            }

    override val isSlippageRealized: Boolean = response.swapDetails?.slippage != null

    @Suppress("MagicNumber")
    override val maxSlippage: BigDecimal =
        response.swapDetails
            ?.slippage
            ?.let {
                BigDecimal(it).divide(BigDecimal(100), MathContext.DECIMAL128)
            }
            ?: quote.slippage

    override val mode: SwapMode = quote.mode

    override val amountIn: BigDecimal = response.swapDetails?.amountIn ?: quote.amountIn

    override val amountInFormatted: BigDecimal = response.swapDetails?.amountInFormatted ?: quote.amountInFormatted

    override val amountInFee: BigDecimal =
        amountInFormatted
            .multiply(
                BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                MathContext.DECIMAL128
            )

    override val amountInUsd: BigDecimal = response.swapDetails?.amountInUsd ?: quote.amountInUsd

    override val amountOut: BigDecimal = response.swapDetails?.amountOut ?: quote.amountOut

    override val amountOutFormatted: BigDecimal =
        response.swapDetails?.amountOutFormatted ?: quote.amountOutFormatted

    override val amountOutUsd: BigDecimal = response.swapDetails?.amountOutUsd ?: quote.amountOutUsd

    override val depositedAmount: BigDecimal? = response.swapDetails?.depositedAmount

    override val depositedAmountFormatted: BigDecimal? = response.swapDetails?.depositedAmountFormatted

    override val depositedAmountUsd: BigDecimal? = response.swapDetails?.depositedAmountUsd

    override val refunded: BigDecimal? = response.swapDetails?.refundedAmount

    override val refundedFormatted: BigDecimal? = response.swapDetails?.refundedAmountFormatted

    override val zecExchangeRate: BigDecimal = amountInUsd.divide(amountInFormatted, MathContext.DECIMAL128)
}
