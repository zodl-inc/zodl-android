package co.electriccoin.zcash.ui.common.usecase

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [GetReloadableSwapQuoteUseCase] wraps [GetSwapStatusUseCase.observe] into a [SwapData] that also
 * carries a [ReloadHandle]; calling `requestReload()` re-subscribes to the status flow, and identical
 * consecutive emissions are de-duplicated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetReloadableSwapQuoteUseCaseTest {
    private val depositAddress = "deposit"

    @Test
    fun wrapsSwapStatusIntoSwapData() =
        runTest {
            val statusData = SwapQuoteStatusData(isLoading = false)
            val getSwapStatus = mockk<GetSwapStatusUseCase> { every { observe(any()) } returns flowOf(statusData) }

            val result = GetReloadableSwapQuoteUseCase(getSwapStatus).observe(depositAddress).first()

            assertEquals(statusData, result.data)
            assertEquals(statusData.status, result.status)
            assertEquals(statusData.isLoading, result.isLoading)
            assertEquals(statusData.error, result.error)
            verify { getSwapStatus.observe(depositAddress) }
        }

    @Test
    fun requestReloadReSubscribesToTheStatusFlow() =
        runTest {
            val first = SwapQuoteStatusData(isLoading = true)
            val second = SwapQuoteStatusData(isLoading = false)
            val getSwapStatus =
                mockk<GetSwapStatusUseCase> {
                    every { observe(any()) } returnsMany listOf(flowOf(first), flowOf(second))
                }

            val useCase = GetReloadableSwapQuoteUseCase(getSwapStatus)
            val emissions = mutableListOf<SwapData>()
            val job = launch { useCase.observe(depositAddress).collect { emissions += it } }
            advanceUntilIdle()

            emissions.first().handle.requestReload()
            advanceUntilIdle()

            verify(exactly = 2) { getSwapStatus.observe(depositAddress) }
            assertEquals(listOf(first, second), emissions.map { it.data })
            job.cancelAndJoin()
        }

    @Test
    fun deduplicatesConsecutiveIdenticalEmissions() =
        runTest {
            val statusData = SwapQuoteStatusData(isLoading = false)
            val getSwapStatus =
                mockk<GetSwapStatusUseCase> { every { observe(any()) } returns flowOf(statusData, statusData) }

            val useCase = GetReloadableSwapQuoteUseCase(getSwapStatus)
            val emissions = mutableListOf<SwapData>()
            val job = launch { useCase.observe(depositAddress).collect { emissions += it } }
            advanceUntilIdle()

            assertEquals(1, emissions.size)
            job.cancelAndJoin()
        }
}
