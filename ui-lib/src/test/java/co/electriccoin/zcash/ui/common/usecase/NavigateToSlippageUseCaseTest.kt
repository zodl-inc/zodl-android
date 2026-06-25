package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.screen.swap.slippage.SwapSlippageArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [NavigateToSlippageUseCase] returns the picked slippage to the caller (a dismissal resolves to
 * null), opens the screen seeded with the current slippage, and ignores results from a different
 * request via the requestId filter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToSlippageUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }
    private val useCase = NavigateToSlippageUseCase(router)

    @Test
    fun selectionResolvesToPickedSlippageAndForwardsCurrentValue() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase(BigDecimal("2"), fiatAmount = null, mode = SwapMode.EXACT_INPUT) }

            val args = forwarded.single() as SwapSlippageArgs
            assertEquals("2", args.currentSlippage)
            assertEquals(SwapMode.EXACT_INPUT, args.mode)

            useCase.onSelected(BigDecimal("5"), args)

            assertEquals(BigDecimal("5"), result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun cancellationResolvesToNull() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase(BigDecimal("2"), fiatAmount = null, mode = SwapMode.EXACT_OUTPUT) }

            val args = forwarded.single() as SwapSlippageArgs
            useCase.onSelectionCancelled(args)

            assertNull(result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun ignoresResultFromADifferentRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase(BigDecimal("2"), fiatAmount = null, mode = SwapMode.FLEX_INPUT) }
            val args = forwarded.single() as SwapSlippageArgs

            // A result tagged with a stale requestId must not resolve this call.
            useCase.onSelected(BigDecimal("9"), args.copy(requestId = "stale"))
            assertTrue(result.isActive)

            useCase.onSelected(BigDecimal("3"), args)
            assertEquals(BigDecimal("3"), result.await())
        }
}
