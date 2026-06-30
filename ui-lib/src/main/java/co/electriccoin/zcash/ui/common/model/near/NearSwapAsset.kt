package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import java.math.BigDecimal

data class NearSwapAsset(
    override val tokenTicker: String,
    override val tokenName: StringResource,
    override val tokenIcon: ImageResource,
    override val usdPrice: BigDecimal?,
    override val assetId: String,
    override val decimals: Int,
    override val blockchain: SwapBlockchain,
) : SwapAsset
