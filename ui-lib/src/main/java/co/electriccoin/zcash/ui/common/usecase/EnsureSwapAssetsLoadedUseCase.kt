package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.SwapRepository

/**
 * Ensures swap assets are loaded from the backend.
 * If assets are not loaded yet, triggers a one-time refresh.
 * This is useful for screens that need swap data but may be accessed
 * before the main swap screen (e.g., blockchain picker from address book).
 */
class EnsureSwapAssetsLoadedUseCase(
    private val swapRepository: SwapRepository,
) {
    suspend operator fun invoke() {
        if (swapRepository.assets.value?.data == null) {
            swapRepository.requestRefreshAssetsOnce()
        }
    }
}
