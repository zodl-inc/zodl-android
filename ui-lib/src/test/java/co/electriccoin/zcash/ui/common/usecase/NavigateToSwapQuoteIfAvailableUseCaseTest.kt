package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatch
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchException
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.screen.swap.mismatch.SwapQuoteMismatchArgs
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [NavigateToSwapQuoteIfAvailableUseCase] opens the quote screen only once a quote has resolved to a
 * terminal state (Success or Error), and when it does, it dismisses the bottom sheet *before*
 * navigating. While the quote is still Loading (or absent) it must do nothing. A rejected quote
 * (MOB-1340) is routed to the mismatch sheet instead of the quote screen.
 */
class NavigateToSwapQuoteIfAvailableUseCaseTest {
    private val zec = SwapAssetTestFixture.zecAsset()
    private val btc = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")

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

    /**
     * A rejected quote (MOB-1340) opens the dedicated mismatch sheet instead of the quote screen, and
     * the error is cleared so the swap screen does not surface it a second time.
     */
    @Test
    fun mismatchErrorHidesSheetThenOpensTheMismatchSheet() =
        runTest {
            val swapRepository = repository(mismatchError())
            NavigateToSwapQuoteIfAvailableUseCase(swapRepository, router).invoke(hideBottomSheet)

            assertContentEquals(listOf("hide", "forward"), events)
            verify(exactly = 1) { swapRepository.clearQuote() }
            val args = forwarded.single()
            assertIs<SwapQuoteMismatchArgs>(args)
            assertEquals(SwapQuoteMismatchType.RECIPIENT_ADDRESS, args.mismatchType)
            assertEquals(SwapMode.EXACT_INPUT, args.mode)
            assertEquals("near", args.provider)
            assertEquals(zec.tokenTicker, args.originTokenTicker)
            assertEquals(btc.tokenTicker, args.destinationTokenTicker)
            assertEquals("deposit-address", args.depositAddress)
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

    private fun useCase(quote: SwapQuoteData?): NavigateToSwapQuoteIfAvailableUseCase =
        NavigateToSwapQuoteIfAvailableUseCase(repository(quote), router)

    private fun repository(quote: SwapQuoteData?): SwapRepository =
        mockk<SwapRepository>(relaxed = true) {
            every { this@mockk.quote } returns MutableStateFlow(quote)
        }

    private fun mismatchError() =
        SwapQuoteData.Error(
            mode = SwapMode.EXACT_INPUT,
            exception =
                SwapQuoteMismatchException.Rejected(
                    type = SwapQuoteMismatchType.RECIPIENT_ADDRESS,
                    message = "mismatch"
                ),
            mismatch =
                SwapQuoteMismatch(
                    type = SwapQuoteMismatchType.RECIPIENT_ADDRESS,
                    provider = "near",
                    depositAddress = "deposit-address",
                    originAsset = zec,
                    destinationAsset = btc
                )
        )
}
