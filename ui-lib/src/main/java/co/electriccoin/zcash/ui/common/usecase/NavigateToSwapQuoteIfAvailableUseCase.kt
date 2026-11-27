package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.screen.swap.mismatch.toMismatchArgs
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteArgs

class NavigateToSwapQuoteIfAvailableUseCase(
    private val swapRepository: SwapRepository,
    private val navigationRouter: NavigationRouter
) {
    suspend operator fun invoke(hideBottomSheet: suspend () -> Unit) {
        val value = swapRepository.quote.value
        val mismatchArgs = (value as? SwapQuoteData.Error)?.toMismatchArgs()

        val isQuoteAvailable = value is SwapQuoteData.Success || value is SwapQuoteData.Error
        if (isQuoteAvailable) {
            hideBottomSheet()
            if (mismatchArgs == null) {
                navigationRouter.forward(SwapQuoteArgs)
            } else {
                swapRepository.clearQuote()
                navigationRouter.forward(mismatchArgs)
            }
        }
    }
}
