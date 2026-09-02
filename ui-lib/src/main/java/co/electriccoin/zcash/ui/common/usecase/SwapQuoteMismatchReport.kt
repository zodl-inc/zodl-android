package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType

/**
 * The technical detail behind a rejected swap quote (MOB-1340), bundled into the support email the
 * mismatch sheet's Report button composes. It carries no user amounts — only what support needs to
 * raise the mismatch with the swap provider.
 */
data class SwapQuoteMismatchReport(
    val provider: String,
    val mode: SwapMode,
    val originTokenTicker: String,
    val originChainTicker: String,
    val destinationTokenTicker: String,
    val destinationChainTicker: String,
    val mismatchType: SwapQuoteMismatchType,
    val depositAddress: String?,
)
