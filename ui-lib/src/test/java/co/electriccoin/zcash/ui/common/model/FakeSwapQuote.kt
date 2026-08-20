package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import java.math.BigDecimal
import kotlin.time.Instant

/**
 * Hand-written [SwapQuote] double for tests. Cannot be a mockk: stubbing a member that returns a
 * `@JvmInline value class` (every [SwapAddress] impl is one) hangs mockk, so the address fields are
 * constructed directly here. The identity/amount/address fields are constructor parameters because
 * the swap quote validation in `SwapRepository` cross-checks them; everything else is a harmless
 * constant.
 */
@Suppress("LongParameterList")
class FakeSwapQuote(
    override val originAsset: SwapAsset,
    override val destinationAsset: SwapAsset,
    override val mode: SwapMode,
    override val amountIn: BigDecimal,
    override val amountInFormatted: BigDecimal,
    override val amountOutFormatted: BigDecimal,
    depositAddress: String,
    destinationAddress: String,
    refundAddress: String
) : SwapQuote {
    override val depositAddress: SwapAddress = DynamicSwapAddress(depositAddress)
    override val destinationAddress: SwapAddress = DynamicSwapAddress(destinationAddress)
    override val refundAddress: SwapAddress = DynamicSwapAddress(refundAddress)
    override val provider: String = "test"
    override val zecExchangeRate: BigDecimal = BigDecimal.ONE
    override val amountInUsd: BigDecimal = BigDecimal.ONE
    override val amountOut: BigDecimal = BigDecimal.ONE
    override val amountOutUsd: BigDecimal = BigDecimal.ONE
    override val affiliateFee: BigDecimal = BigDecimal.ZERO
    override val affiliateFeeZatoshi: Zatoshi = Zatoshi(0)
    override val affiliateFeeUsd: BigDecimal = BigDecimal.ZERO
    override val timestamp: Instant = Instant.fromEpochMilliseconds(0)
    override val deadline: Instant = Instant.fromEpochMilliseconds(0)
    override val slippage: BigDecimal = BigDecimal("2")

    override fun getTotal(proposal: Proposal?): BigDecimal = BigDecimal.ZERO

    override fun getTotalUsd(proposal: Proposal?): BigDecimal = BigDecimal.ZERO

    override fun getTotalFeesUsd(proposal: Proposal?): BigDecimal = BigDecimal.ZERO

    override fun getTotalFeesZatoshi(proposal: Proposal?): Zatoshi = Zatoshi(0)
}
