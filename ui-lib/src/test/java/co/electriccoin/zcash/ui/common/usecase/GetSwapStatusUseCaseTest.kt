package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
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
 * [GetSwapStatusUseCase.invoke] emits the first non-loading [SwapQuoteStatusData] for the swap
 * identified by the passed [TransactionSwapMetadata] (no metadata IO of its own): on success it
 * persists the latest status to metadata and surfaces it; a status-lookup failure or a stored
 * metadata/returned-asset mismatch (via `requireMatchingAsset`) is surfaced as an error state with
 * metadata left untouched.
 */
class GetSwapStatusUseCaseTest {
    private val btc = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")
    private val zec = SwapAssetTestFixture.zecAsset()

    @Test
    fun emitsStatusAndPersistsMetadataOnSuccess() =
        runTest {
            val metadataRepository = mockk<MetadataRepository>(relaxed = true)
            val status = swapStatusResult()
            val swapRepository =
                mockk<SwapRepository> {
                    coEvery { checkSwapStatus(any()) } returns status
                }

            val result = GetSwapStatusUseCase(metadataRepository, swapRepository).invoke(swapMetadata())

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
            val metadataRepository = mockk<MetadataRepository>(relaxed = true)
            val failure = SwapAssetsUnavailableException()
            val swapRepository =
                mockk<SwapRepository> {
                    coEvery { checkSwapStatus(any()) } throws failure
                }

            val result = GetSwapStatusUseCase(metadataRepository, swapRepository).invoke(swapMetadata())

            assertFalse(result.isLoading)
            assertEquals(failure, result.error)
            assertNull(result.status)
            verify(exactly = 0) { metadataRepository.updateSwap(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun surfacesErrorWhenStoredMetadataDoesNotMatchTheReturnedAssets() =
        runTest {
            val metadataRepository = mockk<MetadataRepository>(relaxed = true)
            val swapRepository =
                mockk<SwapRepository> {
                    coEvery { checkSwapStatus(any()) } returns swapStatusResult()
                }
            // Stored origin is ETH but the server returns a BTC origin -> requireMatchingAsset rejects it.
            val metadata =
                swapMetadata(from = SwapAssetTestFixture.simpleAsset(tokenTicker = "eth", chainTicker = "eth"))

            val result = GetSwapStatusUseCase(metadataRepository, swapRepository).invoke(metadata)

            assertFalse(result.isLoading)
            assertIs<IllegalArgumentException>(result.error)
            assertNull(result.status)
            verify(exactly = 0) { metadataRepository.updateSwap(any(), any(), any(), any(), any(), any()) }
        }

    private fun swapMetadata(
        address: String = "deposit",
        from: SimpleSwapAsset = SwapAssetTestFixture.simpleAsset(tokenTicker = "btc", chainTicker = "btc"),
        to: SimpleSwapAsset = SwapAssetTestFixture.zecSimpleAsset()
    ): TransactionSwapMetadata =
        mockk {
            every { depositAddress } returns address
            every { origin } returns from
            every { destination } returns to
        }

    private fun swapStatusResult(
        origin: SwapAsset = btc,
        destination: SwapAsset = zec,
        swapStatus: SwapStatus = SwapStatus.SUCCESS
    ): SwapQuoteStatus =
        mockk {
            every { quote } returns
                mockk {
                    every { originAsset } returns origin
                    every { destinationAsset } returns destination
                }
            every { status } returns swapStatus
            every { amountOutFormatted } returns BigDecimal("1.23")
            every { mode } returns SwapMode.EXACT_INPUT
        }
}
