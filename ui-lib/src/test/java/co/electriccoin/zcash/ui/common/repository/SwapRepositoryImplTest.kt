package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_ADDRESS
import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [SwapRepositoryImpl] owns the shared `assets`/`quote` state (the selected asset + slippage now
 * live in the ViewModels and are passed into the quote methods). The repository's coroutine scope is
 * injected with an [UnconfinedTestDispatcher] so the fire-and-forget refresh/quote jobs run eagerly
 * and deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapRepositoryImplTest {
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private val zec = SwapAssetTestFixture.zecAsset()
    private val btc = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    /** Builds the repository with its background scope swapped for the eager test scope. */
    private fun repository(dataSource: SwapDataSource): SwapRepositoryImpl =
        SwapRepositoryImpl(dataSource).apply { scope = testScope }

    @Test
    fun refreshDropsZecAndNonPricedAssetsAndExtractsZec() =
        runTest {
            val zeroPriced =
                SwapAssetTestFixture.asset(tokenTicker = "zer", chainTicker = "eth", usdPrice = BigDecimal.ZERO)
            val nullPriced =
                SwapAssetTestFixture.asset(tokenTicker = "nul", chainTicker = "eth", usdPrice = null)
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc, zeroPriced, nullPriced)
                }
            val repository = repository(dataSource)

            repository.requestRefreshAssetsOnce()

            val data = repository.assets.value
            assertEquals(listOf(btc), data.data)
            assertEquals(zec, data.zecAsset)
            assertFalse(data.isLoading)
            assertNull(data.error)
        }

    @Test
    fun refreshSetsErrorWhenNoExistingData() =
        runTest {
            val failure = RuntimeException("boom")
            val dataSource = mockk<SwapDataSource> { coEvery { getSupportedTokens() } throws failure }
            val repository = repository(dataSource)

            repository.requestRefreshAssetsOnce()

            val data = repository.assets.value
            assertEquals(failure, data.error)
            assertNull(data.data)
            assertFalse(data.isLoading)
        }

    @Test
    fun refreshErrorKeepsPreviouslyLoadedDataAndDoesNotSurfaceError() =
        runTest {
            var call = 0
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } answers {
                        if (call++ == 0) listOf(zec, btc) else error("boom")
                    }
                }
            val repository = repository(dataSource)

            repository.requestRefreshAssetsOnce() // success: data loaded
            repository.requestRefreshAssetsOnce() // failure: must not wipe data or surface an error

            val data = repository.assets.value
            assertEquals(listOf(btc), data.data)
            assertNull(data.error)
            assertFalse(data.isLoading)
        }

    @Test
    fun exactInputQuoteUsesZecOriginSelectedDestinationAndSlippage() =
        runTest {
            val quote = mockk<SwapQuote>()
            val dataSource = dataSourceReturning(quote)
            val repository = loadedRepository(dataSource)

            repository.requestExactInputQuote(
                amount = BigDecimal("1"),
                address = "destination",
                refundAddress = "refund",
                destinationAsset = btc,
                slippage = BigDecimal("3")
            )

            val result = repository.quote.value
            assertIs<SwapQuoteData.Success>(result)
            assertEquals(quote, result.quote)
            coVerify(exactly = 1) {
                dataSource.requestQuote(
                    swapMode = SwapMode.EXACT_INPUT,
                    amount = BigDecimal("1"),
                    refundAddress = "refund",
                    originAsset = zec,
                    destinationAddress = "destination",
                    destinationAsset = btc,
                    slippage = BigDecimal("3"),
                    affiliateAddress = AFFILIATE_ADDRESS
                )
            }
        }

    @Test
    fun exactOutputQuoteRequestsExactOutputModeFromZec() =
        runTest {
            val dataSource = dataSourceReturning(mockk())
            val repository = loadedRepository(dataSource)

            repository.requestExactOutputQuote(
                amount = BigDecimal("1"),
                address = "destination",
                refundAddress = "refund",
                destinationAsset = btc,
                slippage = BigDecimal("2")
            )

            coVerify(exactly = 1) {
                dataSource.requestQuote(
                    swapMode = SwapMode.EXACT_OUTPUT,
                    amount = BigDecimal("1"),
                    refundAddress = "refund",
                    originAsset = zec,
                    destinationAddress = "destination",
                    destinationAsset = btc,
                    slippage = BigDecimal("2"),
                    affiliateAddress = AFFILIATE_ADDRESS
                )
            }
        }

    @Test
    fun flexInputQuoteUsesSelectedOriginAndZecDestination() =
        runTest {
            val dataSource = dataSourceReturning(mockk())
            val repository = loadedRepository(dataSource)

            repository.requestFlexInputIntoZec(
                amount = BigDecimal("2"),
                refundAddress = "refund",
                destinationAddress = "destination",
                originAsset = btc,
                slippage = BigDecimal("2")
            )

            assertIs<SwapQuoteData.Success>(repository.quote.value)
            coVerify(exactly = 1) {
                dataSource.requestQuote(
                    swapMode = SwapMode.FLEX_INPUT,
                    amount = BigDecimal("2"),
                    refundAddress = "refund",
                    originAsset = btc,
                    destinationAddress = "destination",
                    destinationAsset = zec,
                    slippage = BigDecimal("2"),
                    affiliateAddress = AFFILIATE_ADDRESS
                )
            }
        }

    @Test
    fun flexQuoteFailureProducesFlexErrorState() =
        runTest {
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc)
                    coEvery {
                        requestQuote(any(), any(), any(), any(), any(), any(), any(), any())
                    } throws RuntimeException("quote failed")
                }
            val repository = loadedRepository(dataSource)

            repository.requestFlexInputIntoZec(
                amount = BigDecimal("2"),
                refundAddress = "refund",
                destinationAddress = "destination",
                originAsset = btc,
                slippage = BigDecimal("2")
            )

            val result = repository.quote.value
            assertIs<SwapQuoteData.Error>(result)
            assertEquals(SwapMode.FLEX_INPUT, result.mode)
        }

    @Test
    fun exactInputQuoteFailureProducesExactInputErrorState() =
        runTest {
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc)
                    coEvery {
                        requestQuote(any(), any(), any(), any(), any(), any(), any(), any())
                    } throws RuntimeException("quote failed")
                }
            val repository = loadedRepository(dataSource)

            repository.requestExactInputQuote(
                amount = BigDecimal("1"),
                address = "destination",
                refundAddress = "refund",
                destinationAsset = btc,
                slippage = BigDecimal("2")
            )

            val result = repository.quote.value
            assertIs<SwapQuoteData.Error>(result)
            // The error must carry the request's own mode, not a hardcoded one.
            assertEquals(SwapMode.EXACT_INPUT, result.mode)
        }

    @Test
    fun quoteIsNoOpWhenAssetsNotLoaded() =
        runTest {
            // Without a prior refresh there is no ZEC asset, so the request short-circuits: the quote is
            // left in Loading and the data source is never queried.
            val dataSource = mockk<SwapDataSource>(relaxed = true)
            val repository = repository(dataSource)

            repository.requestExactInputQuote(
                amount = BigDecimal("1"),
                address = "destination",
                refundAddress = "refund",
                destinationAsset = btc,
                slippage = BigDecimal("2")
            )

            assertEquals(SwapQuoteData.Loading, repository.quote.value)
            coVerify(exactly = 0) { dataSource.requestQuote(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun clearResetsAssetsWhenNoDataAndClearsQuote() =
        runTest {
            val dataSource = mockk<SwapDataSource> { coEvery { getSupportedTokens() } throws RuntimeException("boom") }
            val repository = repository(dataSource)
            repository.requestRefreshAssetsOnce() // leaves an error and no data
            assertNotNull(repository.assets.value.error)

            repository.clear()

            assertEquals(SwapAssetsData(), repository.assets.value) // error cleared
            assertNull(repository.quote.value)
        }

    @Test
    fun clearKeepsLoadedAssetsButClearsQuote() =
        runTest {
            val dataSource = dataSourceReturning(mockk())
            val repository = loadedRepository(dataSource)
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertIs<SwapQuoteData.Success>(repository.quote.value)

            repository.clear()

            assertEquals(listOf(btc), repository.assets.value.data)
            assertEquals(zec, repository.assets.value.zecAsset)
            assertNull(repository.quote.value)
        }

    @Test
    fun clearQuoteResetsQuoteToNull() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(mockk()))
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertIs<SwapQuoteData.Success>(repository.quote.value)

            repository.clearQuote()

            assertNull(repository.quote.value)
        }

    @Test
    fun continuousRefreshPopulatesAssets() {
        val repository = repository(dataSourceReturning(mockk()))

        repository.requestRefreshAssets() // first iteration runs eagerly; the 30s delay parks the rest

        assertEquals(listOf(btc), repository.assets.value.data)
    }

    private fun dataSourceReturning(quote: SwapQuote) =
        mockk<SwapDataSource> {
            coEvery { getSupportedTokens() } returns listOf(zec, btc)
            coEvery { requestQuote(any(), any(), any(), any(), any(), any(), any(), any()) } returns quote
        }

    /** Builds a repository on the test scope and loads its assets (so a ZEC asset is available). */
    private suspend fun loadedRepository(dataSource: SwapDataSource): SwapRepositoryImpl =
        repository(dataSource).also { it.requestRefreshAssetsOnce() }
}
