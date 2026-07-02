package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteArgs
import kotlinx.coroutines.flow.first

class NavigateToSwapQuoteIfAvailableUseCase(
    private val swapRepository: SwapRepository,
    private val navigationRouter: NavigationRouter
) {
    suspend operator fun invoke(hideBottomSheet: suspend () -> Unit) {
        val value = swapRepository.quote.first()

        val isQuoteAvailable = value is SwapQuoteData.Success || value is SwapQuoteData.Error
        if (isQuoteAvailable) {
            hideBottomSheet()
            navigationRouter.forward(SwapQuoteArgs)
        }
    }
}
