package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [GetPreselectedSwapAssetUseCase] resolves the asset to preselect from the last-used history, falling
 * back to a hardcoded BTC default, and only returns once the swap assets have loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetPreselectedSwapAssetUseCaseTest {
    @Test
    fun resolvesMostRecentlyUsedAssetFromHistory() =
        runTest {
            val useCase =
                useCase(
                    assets =
                        SwapAssetTestFixture.assetsData(
                            data =
                                listOf(
                                    SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                                    SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth"),
                                )
                        ),
                    history = setOf(SwapAssetTestFixture.simpleAsset(tokenTicker = "eth", chainTicker = "eth"))
                )

            val result = useCase()

            assertEquals("eth", result.tokenTicker)
            assertEquals("eth", result.chainTicker)
        }

    @Test
    fun fallsBackToHardcodedBtcWhenHistoryEmpty() =
        runTest {
            val useCase =
                useCase(
                    assets =
                        SwapAssetTestFixture.assetsData(
                            data = listOf(SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"))
                        ),
                    history = emptySet()
                )

            val result = useCase()

            assertEquals("btc", result.tokenTicker)
            assertEquals("btc", result.chainTicker)
        }

    @Test
    fun fallsBackToHardcodedBtcWhenHistoryHeadIsZec() =
        runTest {
            // A ZEC entry at the head of the history is ignored (you cannot preselect ZEC as the
            // counter-asset), so it falls through to the hardcoded BTC default.
            val useCase =
                useCase(
                    assets =
                        SwapAssetTestFixture.assetsData(
                            data = listOf(SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"))
                        ),
                    history = setOf(SwapAssetTestFixture.zecSimpleAsset())
                )

            val result = useCase()

            assertEquals("btc", result.tokenTicker)
            assertEquals("btc", result.chainTicker)
        }

    @Test
    fun suspendsWhileResolvedAssetAbsentFromData() =
        runTest(UnconfinedTestDispatcher()) {
            val useCase =
                useCase(
                    assets =
                        SwapAssetTestFixture.assetsData(
                            data = listOf(SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"))
                        ),
                    history = setOf(SwapAssetTestFixture.simpleAsset(tokenTicker = "sol", chainTicker = "sol"))
                )

            val result = async { useCase() }

            // The resolved asset (sol) is not in the loaded data, so it keeps waiting.
            assertTrue(result.isActive)
            result.cancel()
        }

    @Test
    fun suspendsUntilResolvableAssetAppears() =
        runTest(UnconfinedTestDispatcher()) {
            val assets = MutableStateFlow(SwapAssetsData(data = null))
            val useCase =
                useCase(
                    assetsFlow = assets,
                    history = setOf(SwapAssetTestFixture.simpleAsset(tokenTicker = "btc", chainTicker = "btc"))
                )

            val result = async { useCase() }
            // Still suspended while data is null.
            assertTrue(result.isActive)

            assets.update {
                SwapAssetTestFixture.assetsData(
                    data = listOf(SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"))
                )
            }

            assertEquals("btc", result.await().tokenTicker)
        }

    private fun useCase(
        assets: SwapAssetsData = SwapAssetTestFixture.assetsData(),
        assetsFlow: MutableStateFlow<SwapAssetsData> = MutableStateFlow(assets),
        history: Set<SimpleSwapAsset> = emptySet()
    ): GetPreselectedSwapAssetUseCase {
        val swapRepository = mockk<SwapRepository> { every { this@mockk.assets } returns assetsFlow }
        val metadataRepository =
            mockk<MetadataRepository> { every { observeLastUsedAssetHistory() } returns flowOf(history) }
        val simpleSwapAssetProvider =
            mockk<SimpleSwapAssetProvider> {
                every { get(any(), any()) } answers {
                    SwapAssetTestFixture.simpleAsset(tokenTicker = firstArg(), chainTicker = secondArg())
                }
            }
        return GetPreselectedSwapAssetUseCase(swapRepository, metadataRepository, simpleSwapAssetProvider)
    }
}
