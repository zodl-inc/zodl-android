package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitQuoteRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitSwapRequestDto
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * LIVE integration test against the real SwapKit / Maya (`MAYACHAIN_STREAMING`) API. Skipped unless
 * `ZCASH_RUN_LIVE_SWAP_API_TESTS=true` (see [assumeLiveApiTestsEnabled]); never runs in CI. Maya quotes vary
 * with pool depth, so assertions only check the response shape. `/v3/swap` requires a **production** key and
 * a transparent `t1…` on the ZEC side (see `docs/SwapKit API (Maya DEX).md` §3/§7).
 */
class KtorSwapkitApiProviderLiveTest {
    private val provider = KtorSwapkitApiProvider(realHttpClientProvider())

    @Test
    fun getSupportedTokensReturnsZec() =
        runBlocking {
            assumeLiveApiTestsEnabled()

            val response = provider.getSupportedTokens("MAYACHAIN")

            assertTrue(response.tokens.isNotEmpty(), "token list must not be empty")
            assertTrue(response.tokens.any { it.identifier == "ZEC.ZEC" }, "ZEC.ZEC must be supported")
        }

    @Test
    fun getPricesReturnsPositiveZecPrice() =
        runBlocking {
            assumeLiveApiTestsEnabled()

            val prices = provider.getPrices(listOf("ZEC.ZEC", "BTC.BTC"))

            val zec = prices.first { it.identifier == "ZEC.ZEC" }
            assertTrue((zec.priceUsd ?: BigDecimal.ZERO).signum() > 0, "ZEC price must be positive")
        }

    @Test
    fun requestQuoteZecToBtcReturnsMayaRoute() =
        runBlocking {
            assumeLiveApiTestsEnabled()

            val response = provider.requestQuote(zecToBtcQuoteRequest())

            assertTrue(response.routes.isNotEmpty(), "expected a Maya route; root error=${response.error}")
            assertTrue(
                response.routes
                    .first()
                    .expectedBuyAmount
                    .signum() > 0,
                "expectedBuyAmount must be positive"
            )
        }

    /**
     * Quotes first because the route binds the source/destination addresses (`/v3/swap` rejects a route with
     * placeholder addresses as `invalidRoute`); the routeId is valid ~60s, so the swap is built right after.
     */
    @Test
    fun buildSwapZecToBtcReturnsTargetAddressAndMemo() =
        runBlocking {
            assumeLiveApiTestsEnabled()

            val routeId =
                provider
                    .requestQuote(
                        SwapkitQuoteRequestDto(
                            sellAsset = "ZEC.ZEC",
                            buyAsset = "BTC.BTC",
                            sellAmount = "0.1",
                            slippage = 2.0,
                            providers = listOf("MAYACHAIN_STREAMING"),
                            sourceAddress = T_ADDRESS,
                            destinationAddress = BTC_ADDRESS
                        )
                    ).routes
                    .first()
                    .routeId

            val swap =
                provider.buildSwap(
                    SwapkitSwapRequestDto(
                        routeId = routeId,
                        sourceAddress = T_ADDRESS,
                        destinationAddress = BTC_ADDRESS
                    )
                )

            assertTrue(swap.targetAddress.isNotBlank(), "a Maya vault deposit address must be returned")
            assertTrue(!swap.memo.isNullOrBlank(), "the Maya swap memo must be returned")
        }

    private fun zecToBtcQuoteRequest() =
        SwapkitQuoteRequestDto(
            sellAsset = "ZEC.ZEC",
            buyAsset = "BTC.BTC",
            sellAmount = "0.1",
            slippage = 2.0,
            providers = listOf("MAYACHAIN_STREAMING")
        )
}
