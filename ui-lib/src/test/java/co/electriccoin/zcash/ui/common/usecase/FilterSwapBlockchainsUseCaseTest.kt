package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import io.mockk.every
import io.mockk.mockk
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FilterSwapBlockchainsUseCase] derives the selectable blockchains from the loaded assets
 * (de-duplicated by name, sorted by ticker), filters them by the search text, and falls back to the
 * hardcoded list when no assets are loaded. Runs under Robolectric because the filter resolves
 * [co.electriccoin.zcash.ui.design.util.StringResource]s against a real [android.content.Context].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterSwapBlockchainsUseCaseTest {
    private val context = RuntimeEnvironment.getApplication()
    private val blockchainProvider = mockk<BlockchainProvider>(relaxed = true)
    private val useCase = FilterSwapBlockchainsUseCase(context, blockchainProvider)

    @Test
    fun mapsAssetsToDistinctBlockchainsSortedByTicker() {
        val assets =
            SwapAssetTestFixture.assetsData(
                data =
                    listOf(
                        SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth"),
                        SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                        // Second asset on the BTC chain must not produce a duplicate blockchain.
                        SwapAssetTestFixture.asset(tokenTicker = "wbtc", chainTicker = "btc"),
                    )
            )

        val result = useCase(assets, text = "")

        assertEquals(listOf("btc", "eth"), result.data?.map { it.chainTicker })
    }

    @Test
    fun filtersBlockchainsByText() {
        val assets =
            SwapAssetTestFixture.assetsData(
                data =
                    listOf(
                        SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                        SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth"),
                    )
            )

        val result = useCase(assets, text = "et")

        assertEquals(listOf("eth"), result.data?.map { it.chainTicker })
    }

    @Test
    fun fallsBackToHardcodedBlockchainsWhenNoData() {
        every { blockchainProvider.getHardcodedBlockchains() } returns
            listOf(SwapAssetTestFixture.blockchain("sol"), SwapAssetTestFixture.blockchain("btc"))

        val result = useCase(SwapAssetsData(data = null), text = "")

        assertEquals(listOf("btc", "sol"), result.data?.map { it.chainTicker })
    }

    @Test
    fun propagatesLoadingFlag() {
        val result = useCase(SwapAssetsData(data = null, isLoading = true), text = "")

        assertTrue(result.isLoading)
    }
}
