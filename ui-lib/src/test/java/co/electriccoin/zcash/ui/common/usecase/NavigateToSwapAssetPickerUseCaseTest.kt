package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.screen.swap.picker.SwapAssetPickerArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [NavigateToSwapAssetPickerUseCase] returns the picked asset to the caller (dismissal resolves to
 * null), forwards the chain-ticker filter, and ignores results from a different request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToSwapAssetPickerUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }
    private val useCase = NavigateToSwapAssetPickerUseCase(router)

    @Test
    fun selectionResolvesToPickedAssetAndForwardsFilter() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase(onlyChainTicker = "eth") }

            val args = forwarded.single() as SwapAssetPickerArgs
            assertEquals("eth", args.onlyChainTicker)

            val asset = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
            useCase.onSelected(asset, args)

            assertEquals(asset, result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun cancellationResolvesToNull() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase(onlyChainTicker = null) }

            val args = forwarded.single() as SwapAssetPickerArgs
            useCase.onSelectionCancelled(args)

            assertNull(result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun ignoresResultFromADifferentRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase(onlyChainTicker = null) }
            val args = forwarded.single() as SwapAssetPickerArgs

            useCase.onSelected(SwapAssetTestFixture.asset(tokenTicker = "sol"), args.copy(requestId = "stale"))
            assertTrue(result.isActive)

            val asset = SwapAssetTestFixture.asset(tokenTicker = "btc")
            useCase.onSelected(asset, args)
            assertEquals(asset, result.await())
        }
}
