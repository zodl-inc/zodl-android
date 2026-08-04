package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource

sealed interface SimpleSwapAsset {
    val tokenTicker: String
    val tokenName: StringResource
    val tokenIcon: ImageResource
    val blockchain: SwapBlockchain

    val chainTicker: String
        get() = blockchain.chainTicker

    val chainName: StringResource
        get() = blockchain.chainName

    val chainIcon: ImageResource
        get() = blockchain.chainIcon
}

fun SimpleSwapAsset.isSame(
    token: String,
    chain: String
): Boolean = tokenTicker.equals(token, ignoreCase = true) && chainTicker.equals(chain, ignoreCase = true)

data class DynamicSimpleSwapAsset(
    override val tokenTicker: String,
    override val tokenName: StringResource,
    override val tokenIcon: ImageResource,
    override val blockchain: SwapBlockchain,
) : SimpleSwapAsset

data class ZecSimpleSwapAsset(
    override val tokenTicker: String,
    override val tokenName: StringResource,
    override val tokenIcon: ImageResource,
    override val blockchain: SwapBlockchain,
) : SimpleSwapAsset {
    // val alternativeTokenIcon: ImageResource = imageRes(R.drawable.ic_zec_round_full)
}

// fun SimpleSwapAsset.getQuoteTokenIcon(): ImageResource =
//     when (this) {
//         is DynamicSimpleSwapAsset -> this.tokenIcon
//         is ZecSimpleSwapAsset -> this.alternativeTokenIcon
//     }
//
// fun SimpleSwapAsset.getQuoteChainIcon(isOriginAsset: Boolean): ImageResource? =
//     when (this) {
//         is DynamicSimpleSwapAsset -> this.chainIcon
//         is ZecSimpleSwapAsset ->
//             if (isOriginAsset) {
//                 imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_receive_shield)
//             } else {
//                 imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded)
//             }
//     }
