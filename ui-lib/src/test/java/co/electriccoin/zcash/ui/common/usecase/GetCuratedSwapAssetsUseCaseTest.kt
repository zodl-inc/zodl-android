package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MOB-1473: swap/pay selection surfaces must only ever offer curated assets. This use case is the
 * single curation seam over the full repository asset list; [invoke] (sync) and [observe] (flow)
 * must both drop non-curated assets.
 */
class GetCuratedSwapAssetsUseCaseTest {
    private val eth = swapAsset(token = "ETH", chain = "eth")
    private val doge = swapAsset(token = "DOGE", chain = "doge")

    private val swapRepository =
        mockk<SwapRepository> {
            every { assets } returns MutableStateFlow(SwapAssetsData(data = listOf(eth, doge)))
        }
    private val simpleSwapAssetProvider =
        mockk<SimpleSwapAssetProvider> {
            every { getCuratedSwapAssets() } returns listOf(curatedAsset("ETH", "eth"), curatedAsset("BTC", "btc"))
        }

    private val useCase = GetCuratedSwapAssetsUseCase(swapRepository, simpleSwapAssetProvider)

    @Test
    fun invokeKeepsOnlyCuratedAssets() {
        assertEquals(listOf(eth), useCase().data)
    }

    @Test
    fun observeKeepsOnlyCuratedAssets() =
        runTest {
            assertEquals(listOf(eth), useCase.observe().first().data)
        }
}

private fun swapAsset(token: String, chain: String): SwapAsset =
    SwapAssetTestFixture.asset(tokenTicker = token, chainTicker = chain)

private fun curatedAsset(token: String, chain: String): SimpleSwapAsset =
    SwapAssetTestFixture.simpleAsset(tokenTicker = token, chainTicker = chain)
