package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.common.model.near.NearSwapAsset
import co.electriccoin.zcash.ui.common.model.swapkit.MayaSwapAsset
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import java.math.BigDecimal

/**
 * A [SwapAsset] representing the same token offered by more than one provider (e.g. NEAR + Maya). The picker
 * shows a single row per composite; the aggregator unpacks [assets] to route a quote request to each provider
 * that has a sub-asset for the pair.
 *
 * Every delegated property prioritizes the NEAR sub-asset (falling back to the first) and is computed with a
 * property initializer — not a getter — so the values are stable for the lifetime of the instance. [assetId]
 * concatenates the sub-asset ids so two composites are equal iff they wrap the same underlying assets.
 */
data class CompositeSwapAsset(
    val assets: List<SwapAsset>
) : SwapAsset {
    init {
        require(assets.isNotEmpty()) { "CompositeSwapAsset requires at least one asset" }
    }

    private val primary: SwapAsset = assets.firstOrNull { it is NearSwapAsset } ?: assets.first()

    override val tokenTicker: String = primary.tokenTicker
    override val tokenName: StringResource = primary.tokenName
    override val tokenIcon: ImageResource = primary.tokenIcon
    override val usdPrice: BigDecimal? = primary.usdPrice
    override val assetId: String = assets.joinToString(separator = "|") { it.assetId }
    override val decimals: Int = primary.decimals
    override val blockchain: SwapBlockchain = primary.blockchain
}

/** Returns the sub-asset produced by [provider], or null if this composite has no quote source for it. */
fun CompositeSwapAsset.assetFor(provider: SwapProvider): SwapAsset? =
    assets.firstOrNull {
        when (provider) {
            SwapProvider.NEAR -> it is NearSwapAsset
            SwapProvider.MAYA -> it is MayaSwapAsset
        }
    }
