package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import java.math.BigDecimal

sealed interface SwapAsset {
    val tokenTicker: String
    val tokenName: StringResource
    val tokenIcon: ImageResource
    val usdPrice: BigDecimal?
    val assetId: String
    val decimals: Int
    val blockchain: SwapBlockchain

    val chainTicker: String
        get() = blockchain.chainTicker

    val chainName: StringResource
        get() = blockchain.chainName

    val chainIcon: ImageResource
        get() = blockchain.chainIcon
}

fun SwapAsset.isSame(
    token: String,
    chain: String
): Boolean = tokenTicker.equals(token, true) && chainTicker.equals(chain, true)

data class DynamicSwapAsset(
    override val tokenTicker: String,
    override val tokenName: StringResource,
    override val tokenIcon: ImageResource,
    override val usdPrice: BigDecimal?,
    override val assetId: String,
    override val decimals: Int,
    override val blockchain: SwapBlockchain,
) : SwapAsset

data class ZecSwapAsset(
    override val tokenTicker: String,
    override val tokenName: StringResource,
    override val tokenIcon: ImageResource,
    override val blockchain: SwapBlockchain,
    override val usdPrice: BigDecimal?,
    override val assetId: String,
    override val decimals: Int,
) : SwapAsset {
    val alternativeTokenIcon: ImageResource = imageRes(R.drawable.ic_zec_round_full)

    fun getQuoteChainIcon(isShielded: Boolean): ImageResource =
        if (isShielded) {
            imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded)
        } else {
            imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded)
        }
}

fun SwapAsset.getQuoteTokenIcon(): ImageResource =
    when (this) {
        is DynamicSwapAsset -> this.tokenIcon
        is ZecSwapAsset -> this.alternativeTokenIcon
    }
