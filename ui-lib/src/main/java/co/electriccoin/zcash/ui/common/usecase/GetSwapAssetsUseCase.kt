package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import kotlinx.coroutines.flow.filterNotNull

class GetSwapAssetsUseCase(
    private val swapRepository: SwapRepository,
) {
    fun observe() = swapRepository.assets.filterNotNull()

    fun get() = swapRepository.assets.value ?: SwapAssetsData()
}
