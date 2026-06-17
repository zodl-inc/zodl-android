package co.electriccoin.zcash.ui.common.model

import java.math.BigDecimal
import java.time.Instant

interface SwapQuoteStatus {
    val quote: SwapQuote

    val timestamp: Instant

    val originAssetId: String
    val destinationAssetId: String

    val status: SwapStatus

    val isSlippageRealized: Boolean
    val maxSlippage: BigDecimal
    val mode: SwapMode

    val amountInFee: BigDecimal
    val amountIn: BigDecimal
    val amountInFormatted: BigDecimal
    val amountInUsd: BigDecimal

    val amountOut: BigDecimal
    val amountOutFormatted: BigDecimal
    val amountOutUsd: BigDecimal

    val depositedAmount: BigDecimal?
    val depositedAmountFormatted: BigDecimal?
    val depositedAmountUsd: BigDecimal?

    val refunded: BigDecimal?
    val refundedFormatted: BigDecimal?

    val zecExchangeRate: BigDecimal
}
