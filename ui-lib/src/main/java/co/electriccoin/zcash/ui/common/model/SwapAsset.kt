package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import java.math.BigDecimal

interface SwapAsset {
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

val SwapAsset.isZCashAsset: Boolean
    get() = isSame(token = "zec", chain = "zec")
