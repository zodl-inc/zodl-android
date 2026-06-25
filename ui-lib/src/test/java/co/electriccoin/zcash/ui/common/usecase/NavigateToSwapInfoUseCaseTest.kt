package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.swap.info.SwapInfoArgs
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/**
 * [NavigateToSwapInfoUseCase] simply forwards to the swap-info screen.
 */
class NavigateToSwapInfoUseCaseTest {
    @Test
    fun navigatesToSwapInfo() {
        val router = mockk<NavigationRouter>(relaxed = true)

        NavigateToSwapInfoUseCase(router).invoke()

        verify(exactly = 1) { router.forward(SwapInfoArgs) }
    }
}
