package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapAssetsUnavailableException
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.TransactionSwapMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * [GetSwapStatusUseCase.invoke] emits the first non-loading [SwapQuoteStatusData]: it looks up the
 * stored metadata for the deposit address and — when present — hands it to
 * [SwapRepository.checkSwapStatus] (which validates the returned assets against it), then persists the
 * latest status to metadata and surfaces it. A missing metadata entry or a status-lookup failure is
 * surfaced as an error state with metadata left untouched. The stored-metadata/returned-asset mismatch
 * itself is covered by `SwapRepositoryImplTest`.
 */
class GetSwapStatusUseCaseTest {
    private val btc = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")
    private val zec = SwapAssetTestFixture.zecAsset()

    @Test
    fun emitsStatusAndPersistsMetadataOnSuccess() =
        runTest {
            val metadataRepository =
                mockk<MetadataRepository>(relaxed = true) {
                    coEvery { getSwapMetadata("deposit") } returns mockk<TransactionSwapMetadata>()
                }
            val status = swapStatusResult()
            val swapRepository =
                mockk<SwapRepository> {
                    coEvery { checkSwapStatus(any()) } returns status
                }

            val result = GetSwapStatusUseCase(metadataRepository, swapRepository).invoke("deposit")

            assertFalse(result.isLoading)
            assertNull(result.error)
            assertEquals(status, result.status)
            verify(exactly = 1) {
                metadataRepository.updateSwap(
                    depositAddress = "deposit",
                    amountOutFormatted = BigDecimal("1.23"),
                    status = SwapStatus.SUCCESS,
                    mode = SwapMode.EXACT_INPUT,
                    origin = btc,
                    destination = zec
                )
            }
        }

    @Test
    fun surfacesErrorAndSkipsMetadataWhenStatusLookupFails() =
        runTest {
            val metadataRepository =
                mockk<MetadataRepository>(relaxed = true) {
                    coEvery { getSwapMetadata("deposit") } returns mockk<TransactionSwapMetadata>()
                }
            val failure = SwapAssetsUnavailableException()
            val swapRepository =
                mockk<SwapRepository> {
                    coEvery { checkSwapStatus(any()) } throws failure
                }

            val result = GetSwapStatusUseCase(metadataRepository, swapRepository).invoke("deposit")

            assertFalse(result.isLoading)
            assertEquals(failure, result.error)
            assertNull(result.status)
            verify(exactly = 0) { metadataRepository.updateSwap(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun surfacesErrorAndSkipsStatusLookupWhenNoStoredMetadata() =
        runTest {
            val metadataRepository =
                mockk<MetadataRepository>(relaxed = true) {
                    coEvery { getSwapMetadata("deposit") } returns null
                }
            val swapRepository = mockk<SwapRepository>()

            val result = GetSwapStatusUseCase(metadataRepository, swapRepository).invoke("deposit")

            assertFalse(result.isLoading)
            assertIs<IllegalStateException>(result.error)
            assertNull(result.status)
            coVerify(exactly = 0) { swapRepository.checkSwapStatus(any()) }
            verify(exactly = 0) { metadataRepository.updateSwap(any(), any(), any(), any(), any(), any()) }
        }

    private fun swapStatusResult(
        origin: SwapAsset = btc,
        destination: SwapAsset = zec,
        swapStatus: SwapStatus = SwapStatus.SUCCESS
    ): SwapQuoteStatus =
        mockk {
            every { originAsset } returns origin
            every { destinationAsset } returns destination
            every { status } returns swapStatus
            every { amountOutFormatted } returns BigDecimal("1.23")
            every { mode } returns SwapMode.EXACT_INPUT
        }
}
