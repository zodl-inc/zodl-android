package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitQuoteRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitSwapRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitTrackRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [KtorSwapkitApiProvider] drives the SwapKit (Maya) HTTP API. The contract these tests pin down:
 * - every endpoint carries the `x-api-key` credential (SwapKit requires it everywhere, unlike NEAR's public
 *   token list);
 * - the DTOs deserialize SwapKit's mixed shapes — amounts as JSON **strings** (`expectedBuyAmount`) and
 *   prices/slippage as bare JSON **numbers** (`price_usd`, `meta.assets[].price`, `totalSlippageBps`) — and
 *   tolerate unknown keys;
 * - a SwapKit error body is lifted into a typed [ResponseWithSwapkitErrorException], falling back to the raw
 *   [ResponseException] for an unparsable body;
 * - traffic uses `create()` (honors the Tor preference), never `createTor()`.
 */
class SwapkitApiProviderTest {
    private val requests = mutableListOf<HttpRequestData>()

    private var responseStatus = HttpStatusCode.OK
    private var responseBody = "{}"
    private var responseContentType = "application/json"

    private var createCalls = 0
    private var createTorCalls = 0

    private val provider = KtorSwapkitApiProvider(RecordingHttpClientProvider())

    @Test
    fun getSupportedTokensHitsTokensEndpointWithProviderParamAndApiKey() =
        runBlocking {
            responseBody = TOKENS_BODY

            val response = provider.getSupportedTokens("MAYACHAIN")

            val request = requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/tokens", request.url.encodedPath)
            assertEquals("MAYACHAIN", request.url.parameters["provider"])
            assertApiKey(request)
            assertRespectsTorPreference()

            assertEquals(2, response.tokens.size)
            val zec = response.tokens.single { it.identifier == "ZEC.ZEC" }
            assertEquals(8, zec.decimals)
            assertEquals("ZEC", zec.ticker)
            assertEquals("zcash", zec.chainId)
        }

    @Test
    fun getPricesParsesNumericPricesAndSendsLeanRequest() =
        runBlocking {
            responseBody = PRICE_BODY

            val response = provider.getPrices(listOf("ZEC.ZEC", "BTC.BTC"))

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/price", request.url.encodedPath)
            assertApiKey(request)
            // metadata:false yields lean rows (see API doc §12); the identifiers come from the caller.
            val body = request.bodyText()
            assertTrue(body.contains("\"metadata\":false"), "request must send metadata:false: $body")
            assertTrue(body.contains("\"identifier\":\"ZEC.ZEC\""), "request must include identifiers: $body")

            // price_usd arrives as a bare JSON number — LenientBigDecimalSerializer must accept it.
            assertEquals(BigDecimal("381.48"), response.single { it.identifier == "ZEC.ZEC" }.priceUsd)
            assertEquals(BigDecimal("59817"), response.single { it.identifier == "BTC.BTC" }.priceUsd)
            assertRespectsTorPreference()
        }

    @Test
    fun requestQuoteParsesMixedStringAndNumberRouteFields() =
        runBlocking {
            responseBody = QUOTE_BODY

            val response =
                provider.requestQuote(
                    SwapkitQuoteRequestDto(
                        sellAsset = "ZEC.ZEC",
                        buyAsset = "BTC.BTC",
                        sellAmount = "1.0",
                        slippage = 2.0,
                        providers = listOf("MAYACHAIN_STREAMING")
                    )
                )

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v3/quote", request.url.encodedPath)
            assertApiKey(request)
            val body = request.bodyText()
            assertTrue(body.contains("\"sellAsset\":\"ZEC.ZEC\""), "request must echo sellAsset: $body")
            assertTrue(body.contains("MAYACHAIN_STREAMING"), "request must restrict to the Maya provider: $body")

            val route = response.routes.single()
            assertEquals("r1", route.routeId)
            assertEquals(listOf("MAYACHAIN_STREAMING"), route.providers)
            // expectedBuyAmount is a JSON string; totalSlippageBps + meta prices are JSON numbers.
            assertEquals(BigDecimal("99.01"), route.expectedBuyAmount)
            assertEquals(BigDecimal("97.03"), route.expectedBuyAmountMaxSlippage)
            assertEquals(-70.38, route.totalSlippageBps)
            assertEquals(BigDecimal("0.0006"), route.fees.single().amount)
            assertEquals(
                BigDecimal("35.1"),
                route.meta
                    ?.assets
                    ?.single()
                    ?.price
            )
            assertEquals(15.0, route.meta?.affiliateFee)
            assertRespectsTorPreference()
        }

    @Test
    fun buildSwapParsesTargetAddressAndMemo() =
        runBlocking {
            responseBody = SWAP_BODY

            val response =
                provider.buildSwap(
                    SwapkitSwapRequestDto(
                        routeId = "r1",
                        sourceAddress = "t1source",
                        destinationAddress = "bc1qdest"
                    )
                )

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v3/swap", request.url.encodedPath)
            assertApiKey(request)
            // disableBuildTx/disableBalanceCheck default to true so SwapKit skips PSBT/balance work.
            val body = request.bodyText()
            assertTrue(body.contains("\"sourceAddress\":\"t1source\""), "must send sourceAddress: $body")
            assertTrue(body.contains("\"destinationAddress\":\"bc1qdest\""), "must send destinationAddress: $body")
            assertTrue(body.contains("\"disableBuildTx\":true"), "must disable tx build: $body")
            assertTrue(body.contains("\"disableBalanceCheck\":true"), "must disable balance check: $body")

            assertEquals("t1RBkNvault", response.targetAddress)
            assertEquals("=:b:bc1qdest:123/3/0:_/nc:15/0", response.memo)
            assertRespectsTorPreference()
        }

    @Test
    fun trackParsesStatusAndRealizedAmounts() =
        runBlocking {
            responseBody = TRACK_BODY

            val response = provider.track(SwapkitTrackRequestDto(depositAddress = "t1deposit"))

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/track", request.url.encodedPath)
            assertApiKey(request)

            assertEquals("completed", response.status)
            assertEquals("ZEC.ZEC", response.fromAsset)
            assertEquals(BigDecimal("0.1297"), response.fromAmount)
            assertEquals(BigDecimal("0.03064351"), response.toAmount)
            assertRespectsTorPreference()
        }

    @Test
    fun errorResponseWithSwapkitErrorBodyMapsToTypedException() {
        runBlocking {
            responseStatus = HttpStatusCode.BadRequest
            responseBody = """{"error":"noRoutesFound"}"""

            val exception =
                assertFailsWith<ResponseWithSwapkitErrorException> {
                    provider.requestQuote(quoteRequest())
                }

            assertEquals("noRoutesFound", exception.error.error)
            assertRespectsTorPreference()
        }
    }

    @Test
    fun errorResponseWithMessageBodyMapsToTypedException() {
        runBlocking {
            responseStatus = HttpStatusCode.BadRequest
            responseBody = """{"message":"sellAssetAmountTooSmall"}"""

            val exception =
                assertFailsWith<ResponseWithSwapkitErrorException> {
                    provider.requestQuote(quoteRequest())
                }

            assertEquals("sellAssetAmountTooSmall", exception.error.message)
            assertRespectsTorPreference()
        }
    }

    @Test
    fun errorResponseWithoutParsableBodyRethrowsRawResponseException() {
        runBlocking {
            responseStatus = HttpStatusCode.BadRequest
            responseBody = "plain text, not a SwapKit error"
            responseContentType = "text/plain"

            val exception =
                assertFailsWith<ResponseException> {
                    provider.requestQuote(quoteRequest())
                }

            // Unparsable body -> the raw transport exception is rethrown, not the typed one.
            assertFalse(exception is ResponseWithSwapkitErrorException)
            assertRespectsTorPreference()
        }
    }

    private fun quoteRequest() =
        SwapkitQuoteRequestDto(
            sellAsset = "ZEC.ZEC",
            buyAsset = "BTC.BTC",
            sellAmount = "1.0",
            providers = listOf("MAYACHAIN_STREAMING")
        )

    private fun assertApiKey(request: HttpRequestData) {
        val apiKey = request.headers["x-api-key"]
        assertNotNull(apiKey, "x-api-key header must be present on every SwapKit request")
        assertTrue(apiKey.isNotBlank(), "x-api-key header must not be blank")
    }

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private fun assertRespectsTorPreference() {
        // create() routes over Tor or clearnet per the user's Tor setting; createTor() forces Tor regardless.
        assertEquals(1, createCalls, "create() (honors the user's Tor preference) must be called exactly once")
        assertEquals(0, createTorCalls, "createTor() (forces Tor) must never be called by KtorSwapkitApiProvider")
    }

    private inner class RecordingHttpClientProvider : HttpClientProvider {
        override suspend fun createTor(): HttpClient {
            createTorCalls++
            error("KtorSwapkitApiProvider must use create() (honors the Tor preference), never createTor()")
        }

        override suspend fun create(): HttpClient {
            createCalls++
            return HttpClient(
                MockEngine { request ->
                    requests += request
                    respond(
                        content = responseBody,
                        status = responseStatus,
                        headers = headersOf(HttpHeaders.ContentType, responseContentType)
                    )
                }
            ) {
                install(ContentNegotiation) { json() }
                // Mirrors the production client so non-2xx responses surface as ResponseExceptions.
                expectSuccess = true
            }
        }
    }
}

// Real-ish SwapKit response shapes, including unknown keys and the string/number mix the DTOs must tolerate.
private val TOKENS_BODY =
    """
    {
      "provider": "MAYACHAIN",
      "name": "SwapKit MAYACHAIN token list",
      "version": { "major": 1, "minor": 0, "patch": 0 },
      "count": 2,
      "tokens": [
        {
          "chain": "ZEC", "chainId": "zcash", "ticker": "ZEC", "identifier": "ZEC.ZEC",
          "symbol": "ZEC", "name": "Zcash", "decimals": 8, "logoURI": "https://x/zec.png",
          "coingeckoId": "zcash", "futureUnknownField": true
        },
        {
          "chain": "BTC", "chainId": "bitcoin", "ticker": "BTC", "identifier": "BTC.BTC",
          "symbol": "BTC", "name": "Bitcoin", "decimals": 8
        }
      ]
    }
    """.trimIndent()

private val PRICE_BODY =
    """
    [
      { "identifier": "ZEC.ZEC", "provider": "", "price_usd": 381.48, "timestamp": 1782723138776 },
      { "identifier": "BTC.BTC", "provider": "", "price_usd": 59817, "timestamp": 1782723138681 }
    ]
    """.trimIndent()

private val QUOTE_BODY =
    """
    {
      "quoteId": "q1",
      "routes": [
        {
          "routeId": "r1",
          "providers": ["MAYACHAIN_STREAMING"],
          "sellAsset": "ZEC.ZEC",
          "buyAsset": "BTC.BTC",
          "sellAmount": "1.0",
          "expectedBuyAmount": "99.01",
          "expectedBuyAmountMaxSlippage": "97.03",
          "totalSlippageBps": -70.38,
          "fees": [
            { "type": "inbound", "amount": "0.0006", "asset": "ZEC.ZEC", "chain": "ZEC",
              "protocol": "MAYACHAIN_STREAMING" }
          ],
          "expiration": "1782723200",
          "meta": {
            "assets": [ { "asset": "ZEC.ZEC", "price": 35.1, "image": "https://x/zec.png" } ],
            "affiliateFee": 15,
            "tags": ["RECOMMENDED"]
          }
        }
      ]
    }
    """.trimIndent()

private val SWAP_BODY =
    """
    {
      "swapId": "s1",
      "targetAddress": "t1RBkNvault",
      "inboundAddress": "t1RBkNvault",
      "memo": "=:b:bc1qdest:123/3/0:_/nc:15/0",
      "meta": { "affiliateFee": 15 }
    }
    """.trimIndent()

private val TRACK_BODY =
    """
    {
      "chainId": "zcash", "hash": "dfa2039a", "block": 3394634,
      "type": "swap", "status": "completed", "trackingStatus": "completed",
      "fromAsset": "ZEC.ZEC", "fromAmount": "0.1297", "fromAddress": "t1from",
      "toAsset": "ETH.ETH", "toAmount": "0.03064351", "toAddress": "0xdest",
      "finalisedAt": -1
    }
    """.trimIndent()
