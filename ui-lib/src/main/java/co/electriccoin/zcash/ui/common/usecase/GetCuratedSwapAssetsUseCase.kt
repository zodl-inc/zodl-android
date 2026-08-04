package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.isSame
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import kotlinx.coroutines.flow.map

class GetCuratedSwapAssetsUseCase(
    private val swapRepository: SwapRepository,
    private val simpleSwapAssetProvider: SimpleSwapAssetProvider,
) {
    /**
     * Synchronous snapshot of the currently loaded assets, curated. Returns [SwapAssetsData] with
     * `data == null` when assets haven't loaded yet — callers (e.g. `SwapVM.preselectChain`) treat
     * that as "nothing selectable". Prefer [observe] for reactive UI; use this only for one-off reads.
     */
    operator fun invoke() = curate(swapRepository.assets.value)

    /** Reactive curated stream. */
    fun observe() = swapRepository.assets.map(::curate)

    private fun curate(data: SwapAssetsData): SwapAssetsData {
        val inclusionList = simpleSwapAssetProvider.getCuratedSwapAssets()
        return data.copy(
            data =
                data.data
                    ?.filter { token ->
                        inclusionList.any {
                            it.isSame(token.tokenTicker, token.blockchain.chainTicker)
                        }
                    }
        )
    }
}
