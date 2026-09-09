package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import java.math.BigDecimal
import kotlin.time.Instant

interface SwapQuote {
    val originAsset: SwapAsset
    val destinationAsset: SwapAsset
    val depositAddress: SwapAddress
    val destinationAddress: SwapAddress
    val refundAddress: SwapAddress
    val provider: SwapProvider
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
