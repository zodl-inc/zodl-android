package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.test.Test

/**
 * [CancelSwapQuoteUseCase] tears down a pending swap quote: it clears both proposal repositories and
 * the cached quote, then pops the quote screen — and must do so in that order (clear state before
 * navigating away).
 */
class CancelSwapQuoteUseCaseTest {
    private val swapRepository = mockk<SwapRepository>(relaxed = true)
    private val zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true)
    private val keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val useCase =
        CancelSwapQuoteUseCase(
            swapRepository = swapRepository,
            zashiProposalRepository = zashiProposalRepository,
            keystoneProposalRepository = keystoneProposalRepository,
            navigationRouter = navigationRouter
        )

    @Test
    fun clearsProposalsAndQuoteThenNavigatesBack() {
        useCase()

        verify(exactly = 1) { zashiProposalRepository.clear() }
        verify(exactly = 1) { keystoneProposalRepository.clear() }
        verify(exactly = 1) { swapRepository.clearQuote() }
        verify(exactly = 1) { navigationRouter.back() }

        // State is torn down before the screen is popped.
        verifyOrder {
            zashiProposalRepository.clear()
            keystoneProposalRepository.clear()
            swapRepository.clearQuote()
            navigationRouter.back()
        }
    }
}
