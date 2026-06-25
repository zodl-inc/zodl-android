package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.ZecSimpleSwapAsset
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

class GetPreselectedSwapAssetUseCase(
    private val swapRepository: SwapRepository,
    private val metadataRepository: MetadataRepository,
    private val simpleSwapAssetProvider: SimpleSwapAssetProvider
) {
    /**
     * Resolves the asset that should be preselected — the most recently used asset from history,
     * falling back to a hardcoded default — and suspends until the loaded asset list actually
     * contains it, then returns that [SwapAsset]. The caller (ViewModel) owns the selection state
     * and decides whether to apply this result.
     */
    suspend operator fun invoke(): SwapAsset {
        val assetToSelect = getAssetFromHistory() ?: getHardCodedAsset()
        return swapRepository.assets
            .mapNotNull { assets ->
                assets.data?.firstOrNull {
                    it.tokenTicker.equals(assetToSelect.tokenTicker, ignoreCase = true) &&
                        it.chainTicker.equals(assetToSelect.chainTicker, ignoreCase = true)
                }
            }.first()
    }

    private fun getHardCodedAsset(): SimpleSwapAsset =
        simpleSwapAssetProvider
            .get(tokenTicker = "btc", chainTicker = "btc")

    private suspend fun getAssetFromHistory(): SimpleSwapAsset? =
        metadataRepository
            .observeLastUsedAssetHistory()
            .filterNotNull()
            .first()
            .firstOrNull()
            ?.takeIf { it !is ZecSimpleSwapAsset }
}
