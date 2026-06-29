package co.electriccoin.zcash.ui.common.model.swapkit

import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import java.math.BigDecimal

/**
 * A [SwapAsset] sourced from SwapKit/Maya. Built in `MayaSwapDataSource.getSupportedTokens` from
 * `/tokens?provider=MAYACHAIN` enriched with `POST /price` — unlike NEAR, the token list carries no price,
 * so [usdPrice] is supplied separately. [assetId] is the SwapKit `CHAIN.TICKER[-ADDRESS]` identifier used
 * verbatim as `sellAsset`/`buyAsset` on `/v3/quote`.
 */
data class MayaSwapAsset(
    override val tokenTicker: String,
    override val tokenName: StringResource,
    override val tokenIcon: ImageResource,
    override val blockchain: SwapBlockchain,
    override val usdPrice: BigDecimal?,
    override val assetId: String,
    override val decimals: Int,
) : SwapAsset
