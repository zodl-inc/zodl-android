package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.design.util.getString

class FilterSwapBlockchainsUseCase(
    private val context: Context,
    private val simpleSwapAssetProvider: SimpleSwapAssetProvider,
) {
    operator fun invoke(assets: SwapAssetsData, text: String): SwapBlockchainData {
        val blockchains =
            assets.data
                ?.map { it.blockchain }
                ?.distinctBy { it.chainName.getString(context) } ?: getCuratedBlockchainsFallback()
        val sorted = blockchains.sortedBy { it.chainTicker }
        val filtered =
            buildSet {
                addAll(sorted.filter { it.chainTicker.startsWith(text, ignoreCase = true) })
                addAll(sorted.filter { it.chainTicker.contains(text, ignoreCase = true) })
                addAll(sorted.filter { it.chainName.getString(context).startsWith(text, ignoreCase = true) })
                addAll(sorted.filter { it.chainName.getString(context).contains(text, ignoreCase = true) })
            }.toList()

        return SwapBlockchainData(
            data = filtered,
            isLoading = assets.isLoading,
        )
    }

    // Loading/error fallback: the swap blockchain picker never offers ZEC (steady-state assets strip it),
    // so derive the fallback from the curated inclusion list minus the ZEC chain to stay consistent.
    private fun getCuratedBlockchainsFallback(): List<SwapBlockchain> =
        simpleSwapAssetProvider
            .getCuratedSwapAssets()
            .map { it.blockchain }
            .filterNot { it.chainTicker.equals("zec", ignoreCase = true) }
            .distinctBy { it.chainTicker.lowercase() }
}

data class SwapBlockchainData(
    val data: List<SwapBlockchain>?,
    val isLoading: Boolean,
)
