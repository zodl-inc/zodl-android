package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlin.test.Test

/**
 * [CancelSwapUseCase] tears down the in-flight swap: it clears the shared swap state and then pops
 * back to the root, in that order.
 */
class CancelSwapUseCaseTest {
    @Test
    fun clearsSwapStateThenReturnsToRoot() {
        val swapRepository = mockk<SwapRepository>(relaxed = true)
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        CancelSwapUseCase(swapRepository, navigationRouter)()

        verifyOrder {
            swapRepository.clear()
            navigationRouter.backToRoot()
        }
    }
}
