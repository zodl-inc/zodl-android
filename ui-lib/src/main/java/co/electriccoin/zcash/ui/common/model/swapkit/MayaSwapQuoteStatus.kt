package co.electriccoin.zcash.ui.common.model.swapkit

import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.SwapStatus
import java.math.BigDecimal
import java.time.Instant

/**
 * A [SwapQuoteStatus] built in `MayaSwapDataSource.checkSwapStatus` from `/track` + loaded assets. Maya's
 * `/track` echoes neither the quote nor any USD/slippage, so Phase 1 derives USD from the loaded asset prices
 * and leaves slippage/fee at zero (Phase 2 persists those to metadata at submit and reconstructs them here).
 * [isSlippageRealized] is always false — Maya never returns a realized-slippage figure.
 */
data class MayaSwapQuoteStatus(
    override val originAsset: SwapAsset,
    override val destinationAsset: SwapAsset,
    override val depositAddress: SwapAddress,
    override val destinationAddress: SwapAddress,
    override val status: SwapStatus,
    override val amountInFormatted: BigDecimal,
    override val amountInUsd: BigDecimal,
    override val amountOutFormatted: BigDecimal,
    override val amountOutUsd: BigDecimal,
    override val amountInFee: BigDecimal,
    override val maxSlippage: BigDecimal,
    override val depositedAmountFormatted: BigDecimal?,
    override val refundedFormatted: BigDecimal?,
    override val timestamp: Instant,
    override val deadline: kotlin.time.Instant,
) : SwapQuoteStatus {
    override val isSlippageRealized: Boolean = false
    override val mode: SwapMode = SwapMode.EXACT_INPUT
}
