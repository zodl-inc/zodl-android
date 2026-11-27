package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.provider.SwapAssetCacheProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SwapRepositoryAssetRefreshTest {
    private val zec = SwapAssetTestFixture.zecAsset()
    private val btc = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")

    @Test
    fun clearDuringSharedRefreshLetsOneShotCallerRetry() =
        runTest {
            var calls = 0
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } coAnswers {
                        if (calls++ == 0) awaitCancellation() else listOf(zec, btc)
                    }
                }
            val repository = repository(dataSource)

            repository.requestRefreshAssets()
            runCurrent()
            val oneShot = backgroundScope.async { repository.requestRefreshAssetsOnce() }
            runCurrent()

            repository.clear()
            runCurrent()
            oneShot.await()

            assertEquals(2, calls)
            assertEquals(listOf(btc), repository.assets.value.data)
            assertNull(repository.assets.value.error)
        }

    @Test
    fun clearBeforeRefreshDispatchLetsOneShotCallerRetry() =
        runTest {
            val dataSource = mockk<SwapDataSource> { coEvery { getSupportedTokens() } returns listOf(zec, btc) }
            val repository = repository(dataSource)
            val oneShot =
                async(start = CoroutineStart.UNDISPATCHED) {
                    repository.requestRefreshAssetsOnce()
                }

            repository.clear()
            runCurrent()
            oneShot.await()

            assertEquals(listOf(btc), repository.assets.value.data)
            assertNull(repository.assets.value.error)
        }

    @Test
    fun retryDuringPeriodicDelayRefreshesImmediately() =
        runTest {
            var calls = 0
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } answers {
                        if (calls++ == 0) error("offline") else listOf(zec, btc)
                    }
                }
            val repository = repository(dataSource)

            repository.requestRefreshAssets()
            runCurrent()
            assertEquals(1, calls)

            repository.requestRefreshAssets()
            runCurrent()

            assertEquals(2, calls)
            assertEquals(listOf(btc), repository.assets.value.data)
            assertNull(repository.assets.value.error)
        }

    @Test
    fun oneShotDuringPeriodicDelayKeepsPeriodicRefresh() =
        runTest {
            var calls = 0
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } answers {
                        calls++
                        listOf(zec, btc)
                    }
                }
            val repository = repository(dataSource)

            repository.requestRefreshAssets()
            runCurrent()
            repository.requestRefreshAssetsOnce()
            assertEquals(2, calls)

            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(3, calls)
            repository.clear()
        }

    @Test
    fun screenRefreshesPricesEveryThirtySeconds() =
        runTest {
            var calls = 0
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } answers {
                        calls++
                        listOf(zec, btc)
                    }
                }
            val repository = repository(dataSource)

            repository.requestRefreshAssets()
            runCurrent()
            assertEquals(1, calls)

            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, calls)

            repository.clear()
        }

    @Test
    fun cachedMetadataIsReplacedByLivePrices() =
        runTest {
            val cachedBtc = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc", usdPrice = null)
            val cachedZec = SwapAssetTestFixture.asset(tokenTicker = "zec", chainTicker = "zec", usdPrice = null)
            val liveAssets = CompletableDeferred<List<SwapAsset>>()
            val dataSource = mockk<SwapDataSource> { coEvery { getSupportedTokens() } coAnswers { liveAssets.await() } }
            val cache =
                mockk<SwapAssetCacheProvider>(relaxed = true) {
                    coEvery { get() } returns listOf(cachedZec, cachedBtc)
                }
            val repository = repository(dataSource, cache)

            repository.requestRefreshAssets()
            runCurrent()
            assertEquals(listOf(cachedBtc), repository.assets.value.data)
            assertEquals(cachedZec, repository.assets.value.zecAsset)
            assertEquals(true, repository.assets.value.isLoading)

            liveAssets.complete(listOf(zec, btc))
            runCurrent()

            assertEquals(listOf(btc), repository.assets.value.data)
            assertEquals(zec, repository.assets.value.zecAsset)
            assertFalse(repository.assets.value.isLoading)
            assertNull(repository.assets.value.error)
        }

    @Test
    fun successfulRefreshDoesNotCacheAssetsMissingLivePrices() =
        runTest {
            val unpriced =
                SwapAssetTestFixture.asset(
                    tokenTicker = "dash",
                    chainTicker = "dash",
                    usdPrice = null
                )
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc, unpriced)
                }
            val cache = mockk<SwapAssetCacheProvider>(relaxed = true)
            val repository = repository(dataSource, cache)

            repository.requestRefreshAssetsOnce()

            coVerify(exactly = 1) { cache.store(listOf(zec, btc)) }
        }

    private fun TestScope.repository(
        dataSource: SwapDataSource,
        cache: SwapAssetCacheProvider = mockk(relaxed = true)
    ) = SwapRepositoryImpl(dataSource, cache).apply { scope = backgroundScope }
}
