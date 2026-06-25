package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FilterSwapAssetsUseCase] filters the swap assets by optional chain and search text, and orders
 * them by a fixed "trending" priority then by the most-recently-used assets. Runs under Robolectric
 * because the search resolves [co.electriccoin.zcash.ui.design.util.StringResource]s against a real
 * [android.content.Context].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterSwapAssetsUseCaseTest {
    private val useCase = FilterSwapAssetsUseCase(RuntimeEnvironment.getApplication())

    @Test
    fun returnsAssetsUnchangedWhenDataIsNull() {
        val assets = SwapAssetsData(data = null)

        val result = useCase(assets, latestUsedAssets = null, text = "", onlyChainTicker = null)

        assertEquals(assets, result)
    }

    @Test
    fun filtersByChainTicker() {
        val assets =
            SwapAssetTestFixture.assetsData(
                data =
                    listOf(
                        SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                        SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth"),
                        SwapAssetTestFixture.asset(tokenTicker = "usdc", chainTicker = "eth"),
                    )
            )

        val result = useCase(assets, latestUsedAssets = null, text = "", onlyChainTicker = "eth")

        val data = result.data.orEmpty()
        assertTrue(data.all { it.chainTicker == "eth" })
        assertEquals(setOf("eth", "usdc"), data.map { it.tokenTicker }.toSet())
    }

    @Test
    fun emptyTextOrdersByTrendingPriority() {
        val assets =
            SwapAssetTestFixture.assetsData(
                data =
                    listOf(
                        SwapAssetTestFixture.asset(tokenTicker = "sol", chainTicker = "sol"),
                        SwapAssetTestFixture.asset(tokenTicker = "usdc", chainTicker = "eth"),
                        SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                        SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth"),
                    )
            )

        val result = useCase(assets, latestUsedAssets = null, text = "", onlyChainTicker = null)

        // Trending priority: btc, eth, sol, usdc(eth).
        assertEquals(listOf("btc", "eth", "sol", "usdc"), result.data?.map { it.tokenTicker })
    }

    @Test
    fun mostRecentlyUsedAssetsTakePrecedenceOverTrending() {
        val assets =
            SwapAssetTestFixture.assetsData(
                data =
                    listOf(
                        SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                        SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth"),
                        SwapAssetTestFixture.asset(tokenTicker = "sol", chainTicker = "sol"),
                    )
            )
        val latest: Set<SimpleSwapAsset> =
            setOf(SwapAssetTestFixture.simpleAsset(tokenTicker = "sol", chainTicker = "sol"))

        val result = useCase(assets, latestUsedAssets = latest, text = "", onlyChainTicker = null)

        // SOL was last used, so it is pulled to the front ahead of the trending order.
        assertEquals("sol", result.data?.first()?.tokenTicker)
    }

    @Test
    fun textSearchMatchesByTokenTickerAndExcludesNonMatches() {
        val assets =
            SwapAssetTestFixture.assetsData(
                data =
                    listOf(
                        SwapAssetTestFixture.asset(tokenTicker = "usdc", chainTicker = "eth"),
                        SwapAssetTestFixture.asset(tokenTicker = "usdt", chainTicker = "eth"),
                        SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                    )
            )

        val result = useCase(assets, latestUsedAssets = null, text = "usd", onlyChainTicker = null)

        assertEquals(listOf("usdc", "usdt"), result.data?.map { it.tokenTicker })
    }
}
