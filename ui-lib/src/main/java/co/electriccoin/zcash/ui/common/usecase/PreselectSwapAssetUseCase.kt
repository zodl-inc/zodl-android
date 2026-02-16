package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.ZecSimpleSwapAsset
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PreselectSwapAssetUseCase(
    private val swapRepository: SwapRepository,
    private val metadataRepository: MetadataRepository,
    private val simpleSwapAssetProvider: SimpleSwapAssetProvider
) {
    fun observe() =
        channelFlow<Unit> {
            launch {
                swapRepository
                    .assets
                    .onEach { data ->
                        if (swapRepository.selectedAsset.value == null && data.data != null) {
                            val assetToSelect = getAssetFromHistory() ?: getHardCodedAsset()
                            val foundAssetToSelect =
                                data.data
                                    .find {
                                        it.tokenTicker.equals(assetToSelect.tokenTicker, ignoreCase = true) &&
                                            it.chainTicker.equals(assetToSelect.chainTicker, ignoreCase = true)
                                    }

                            if (foundAssetToSelect != null) {
                                swapRepository.select(foundAssetToSelect)
                            }
                        }
                    }.launchIn(this)
            }

            awaitClose()
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
