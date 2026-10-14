package co.electriccoin.zcash.ui.screen.swap.quote

import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.DynamicSwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A plain [SwapQuote] test double rather than a mock: MockK's automatic answer generation for
 * an unstubbed or freshly-recorded property backed by a `@JvmInline value class` (here
 * [SwapAddress]'s implementations) never terminates, so every quote field is a real value.
 */
internal data class FakeSwapQuote(
    override val destinationAsset: SwapAsset,
    override val mode: SwapMode,
    override val originAsset: SwapAsset = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
    override val depositAddress: SwapAddress = DynamicSwapAddress("deposit-address"),
    override val destinationAddress: SwapAddress = DynamicSwapAddress("dest-address"),
    override val refundAddress: SwapAddress = DynamicSwapAddress("refund-address"),
    override val provider: String = "near",
    override val zecExchangeRate: BigDecimal = BigDecimal("30"),
    override val amountIn: BigDecimal = BigDecimal("1.5"),
    override val amountInFormatted: BigDecimal = BigDecimal("1.5"),
    override val amountInUsd: BigDecimal = BigDecimal("100"),
    override val amountOut: BigDecimal = BigDecimal("99"),
    override val amountOutUsd: BigDecimal = BigDecimal("99"),
    override val amountOutFormatted: BigDecimal = BigDecimal("99"),
    override val affiliateFee: BigDecimal = BigDecimal("0.01"),
    override val affiliateFeeZatoshi: Zatoshi = Zatoshi(1000),
    override val affiliateFeeUsd: BigDecimal = BigDecimal("1"),
    override val timestamp: Instant = Clock.System.now(),
    override val deadline: Instant = Clock.System.now(),
    override val slippage: BigDecimal = BigDecimal("1"),
) : SwapQuote {
    override fun getTotal(proposal: Proposal?): BigDecimal = amountIn

    override fun getTotalUsd(proposal: Proposal?): BigDecimal = amountInUsd

    override fun getTotalFeesUsd(proposal: Proposal?): BigDecimal = affiliateFeeUsd

    override fun getTotalFeesZatoshi(proposal: Proposal?): Zatoshi = affiliateFeeZatoshi
}
