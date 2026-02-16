package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.isSame
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.design.util.getString

class FilterSwapAssetsUseCase(
    private val context: Context
) {
    operator fun invoke(
        assets: SwapAssetsData,
        latestUsedAssets: Set<SimpleSwapAsset>?,
        text: String,
        onlyChainTicker: String?,
    ): SwapAssetsData {
        if (assets.data == null) return assets

        val result =
            if (text.isEmpty()) {
                assets.data
                    .filter {
                        if (onlyChainTicker == null) {
                            true
                        } else {
                            it.chainTicker.equals(onlyChainTicker, ignoreCase = true)
                        }
                    }.reorderByTrending()
                    .reorderByLatestAssets(latestUsedAssets)
            } else {
                val sorted =
                    assets.data
                        .filter {
                            if (onlyChainTicker == null) {
                                true
                            } else {
                                it.chainTicker.equals(onlyChainTicker, ignoreCase = true)
                            }
                        }.sortedBy { it.tokenTicker.replace("$", "") }
                        .reorderByTrending()
                        .reorderByLatestAssets(latestUsedAssets)

                buildSet {
                    addAll(sorted.filter { it.tokenTicker.startsWith(text, ignoreCase = true) })
                    addAll(sorted.filter { it.tokenTicker.contains(text, ignoreCase = true) })
                    addAll(sorted.filter { it.tokenName.getString(context).startsWith(text, ignoreCase = true) })
                    addAll(sorted.filter { it.tokenName.getString(context).contains(text, ignoreCase = true) })
                    addAll(sorted.filter { it.chainTicker.startsWith(text, ignoreCase = true) })
                    addAll(sorted.filter { it.chainTicker.contains(text, ignoreCase = true) })
                    addAll(sorted.filter { it.chainName.getString(context).startsWith(text, ignoreCase = true) })
                    addAll(sorted.filter { it.chainName.getString(context).contains(text, ignoreCase = true) })
                }.toList()
            }

        return assets.copy(data = result)
    }

    private fun List<SwapAsset>.reorderByTrending(): List<SwapAsset> {
        val mutable = this.toMutableList()
        listOfNotNull(
            find { it.isSame("btc", "btc") },
            find { it.isSame("eth", "eth") },
            find { it.isSame("sol", "sol") },
            find { it.isSame("usdc", "eth") },
            find { it.isSame("usdt", "eth") },
            find { it.isSame("usdc", "arb") },
            find { it.isSame("usdc", "sol") },
            find { it.isSame("usdt", "sol") },
            find { it.isSame("usdt", "bsc") },
            find { it.isSame("usdt", "tron") },
            find { it.isSame("usdc", "sui") },
            find { it.isSame("usdc", "base") },
        ).forEachIndexed { index, asset -> mutable.move(asset, index) }
        return mutable.toList()
    }

    @Suppress("ReturnCount")
    private fun List<SwapAsset>.reorderByLatestAssets(simpleAssets: Set<SimpleSwapAsset>?): List<SwapAsset> {
        if (simpleAssets.isNullOrEmpty()) return this

        val foundSwapAssets =
            simpleAssets
                .mapNotNull { latest ->
                    this.find { asset ->
                        asset.tokenTicker.equals(latest.tokenTicker, ignoreCase = true) &&
                            asset.chainTicker.equals(latest.chainTicker, ignoreCase = true)
                    }
                }

        if (foundSwapAssets.isEmpty()) return this
        val mutable = this.toMutableList()
        foundSwapAssets.forEachIndexed { index, asset -> mutable.move(asset, index) }
        return mutable.toList()
    }

    private fun MutableList<SwapAsset>.move(asset: SwapAsset, index: Int) {
        remove(asset)
        add(index, asset)
    }
}
