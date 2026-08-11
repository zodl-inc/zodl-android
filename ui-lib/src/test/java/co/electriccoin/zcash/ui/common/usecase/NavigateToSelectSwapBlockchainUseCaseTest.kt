package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.screen.swap.picker.SwapBlockchainPickerArgs
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
 * [NavigateToSelectSwapBlockchainUseCase] opens the blockchain picker and returns the picked chain
 * to the caller (a dismissal resolves to null), ignoring results tagged with a different requestId.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToSelectSwapBlockchainUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }
    private val useCase = NavigateToSelectSwapBlockchainUseCase(router)

    @Test
    fun selectionResolvesToPickedBlockchain() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }

            val args = forwarded.single() as SwapBlockchainPickerArgs
            val blockchain = SwapAssetTestFixture.blockchain("eth")
            useCase.onSelected(blockchain, args)

            assertEquals(blockchain, result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun cancellationResolvesToNull() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }

            val args = forwarded.single() as SwapBlockchainPickerArgs
            useCase.onSelectionCancelled(args)

            assertNull(result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun ignoresResultFromADifferentRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as SwapBlockchainPickerArgs

            useCase.onSelected(SwapAssetTestFixture.blockchain("sol"), args.copy(requestId = "stale"))
            assertTrue(result.isActive)

            val blockchain = SwapAssetTestFixture.blockchain("btc")
            useCase.onSelected(blockchain, args)
            assertEquals(blockchain, result.await())
        }
}
