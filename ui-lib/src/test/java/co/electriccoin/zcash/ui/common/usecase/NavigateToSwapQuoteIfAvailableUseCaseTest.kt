package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteArgs
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [NavigateToSwapQuoteIfAvailableUseCase] opens the quote screen only once a quote has resolved to a
 * terminal state (Success or Error), and when it does, it dismisses the bottom sheet *before*
 * navigating. While the quote is still Loading (or absent) it must do nothing.
 */
class NavigateToSwapQuoteIfAvailableUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val events = mutableListOf<String>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                events += "forward"
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }

    private val hideBottomSheet: suspend () -> Unit = { events += "hide" }

    @Test
    fun successHidesSheetThenNavigates() =
        runTest {
            useCase(SwapQuoteData.Success(mockk<SwapQuote>())).invoke(hideBottomSheet)

            assertContentEquals(listOf("hide", "forward"), events)
            assertEquals(SwapQuoteArgs, forwarded.single())
        }

    @Test
    fun errorHidesSheetThenNavigates() =
        runTest {
            useCase(SwapQuoteData.Error(SwapMode.EXACT_INPUT, RuntimeException("boom")))
                .invoke(hideBottomSheet)

            assertContentEquals(listOf("hide", "forward"), events)
            assertEquals(SwapQuoteArgs, forwarded.single())
        }

    @Test
    fun loadingDoesNothing() =
        runTest {
            useCase(SwapQuoteData.Loading).invoke(hideBottomSheet)

            assertTrue(events.isEmpty())
            assertTrue(forwarded.isEmpty())
        }

    @Test
    fun nullQuoteDoesNothing() =
        runTest {
            useCase(null).invoke(hideBottomSheet)

            assertTrue(events.isEmpty())
            assertTrue(forwarded.isEmpty())
        }

    private fun useCase(quote: SwapQuoteData?): NavigateToSwapQuoteIfAvailableUseCase {
        val swapRepository =
            mockk<SwapRepository>(relaxed = true) {
                every { this@mockk.quote } returns MutableStateFlow(quote)
            }
        return NavigateToSwapQuoteIfAvailableUseCase(swapRepository, router)
    }
}
