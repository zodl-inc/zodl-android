package co.electriccoin.zcash.ui.common.model

import java.math.BigDecimal
import java.time.Instant

interface SwapQuoteStatus {
    val originAsset: SwapAsset
    val destinationAsset: SwapAsset
    val depositAddress: SwapAddress
    val destinationAddress: SwapAddress

    val timestamp: Instant
    val deadline: kotlin.time.Instant

    val status: SwapStatus

    val isSlippageRealized: Boolean
    val maxSlippage: BigDecimal
    val mode: SwapMode

    val amountInFee: BigDecimal
    val amountInFormatted: BigDecimal
    val amountInUsd: BigDecimal
    val amountOutFormatted: BigDecimal
    val amountOutUsd: BigDecimal
    val depositedAmountFormatted: BigDecimal?
    val refundedFormatted: BigDecimal?
}
