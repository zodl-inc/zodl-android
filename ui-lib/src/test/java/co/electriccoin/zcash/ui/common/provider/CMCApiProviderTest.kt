package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MOB-1520: CMC requests always route through OHTTP (with Tor ON the relay leg goes over Tor).
 * The app never sends X-CMC_PRO_API_KEY — the gateway injects it server-side. These tests pin
 * that contract: create() and createTor() must never be called, and the OHTTP path is used.
 */
class CMCApiProviderTest {
    @Test
    fun getExchangeRateQuoteUsesOhttpAndNeverSendsApiKey() =
        runTest {
            val httpClientProvider = RecordingHttpClientProvider()
            val provider = CMCApiProviderImpl(httpClientProvider)

            val response = provider.getExchangeRateQuote(apiKey = "key", fiat = "USD")

            assertEquals(1, httpClientProvider.createOhttpCalls, "CMC must use OHTTP")
            assertEquals(
                0,
                httpClientProvider.createCalls,
                "CMC must never use create() (OHTTP only)"
            )
            assertEquals(
                0,
                httpClientProvider.createTorCalls,
                "CMC must never use createTor() (OHTTP handles routing)"
            )
            assertEquals(
                RATE.toBigDecimal(),
                response.data["ZEC"]
                    ?.quote
                    ?.get("USD")
                    ?.price
            )
            assertTrue(httpClientProvider.requestedUrls.all { it.contains(CMC_API_HOST) })
            // MOB-1520: the app must never send the CMC key; the gateway injects it.
            assertFalse(
                httpClientProvider.requestedUrls.any { it.contains("X-CMC_PRO_API_KEY") },
                "CMC key must not be sent by the app (gateway injects it)"
            )
        }

    @Test
    fun getFiatMapUsesOhttpAndNeverSendsApiKey() =
        runTest {
            val httpClientProvider = RecordingHttpClientProvider()
            val provider = CMCApiProviderImpl(httpClientProvider)

            val response = provider.getFiatMap(apiKey = "key")

            assertEquals(1, httpClientProvider.createOhttpCalls, "CMC must use OHTTP")
            assertEquals(
                0,
                httpClientProvider.createCalls,
                "CMC must never use create() (OHTTP only)"
            )
            assertEquals(
                0,
                httpClientProvider.createTorCalls,
                "CMC must never use createTor() (OHTTP handles routing)"
            )
            assertEquals(listOf("USD"), response.data.map { it.symbol })
            assertTrue(httpClientProvider.requestedUrls.all { it.contains(CMC_API_HOST) })
            // MOB-1520: the app must never send the CMC key; the gateway injects it.
            assertFalse(
                httpClientProvider.requestedUrls.any { it.contains("X-CMC_PRO_API_KEY") },
                "CMC key must not be sent by the app (gateway injects it)"
            )
        }
}

private const val RATE = 42.0

private const val FIAT_MAP_JSON =
    """{"status":{"credit_count":1},"data":[{"id":2781,"name":"United States Dollar","sign":"$","symbol":"USD"}]}"""

private const val FIAT_MAP_PATH = "/v1/fiat/map"

private class RecordingHttpClientProvider : HttpClientProvider {
    var createCalls = 0
        private set
    var createTorCalls = 0
        private set
    var createOhttpCalls = 0
        private set
    val requestedUrls = mutableListOf<String>()

    override suspend fun create(): HttpClient {
        createCalls++
        error("CMC must never be fetched over the direct client (OHTTP only)")
    }

    override suspend fun createTor(): HttpClient {
        createTorCalls++
        error("CMC must never be fetched over the Tor client (OHTTP handles routing)")
    }

    override suspend fun createOhttp(): OhttpHttpClient {
        createOhttpCalls++
        return FakeOhttpHttpClient { request ->
            requestedUrls += request.url
            val body =
                if (request.url.contains(FIAT_MAP_PATH)) {
                    FIAT_MAP_JSON
                } else {
                    """{"data":{"ZEC":{"quote":{"USD":{"price":$RATE}}}}}"""
                }
            OhttpHttpClient.Response(
                status = 200,
                body = body.toByteArray()
            )
        }
    }
}

/**
 * Minimal fake [OhttpHttpClient] for testing. Invokes [handler] for each request and returns
 * the result. Production [OhttpHttpClient] lives in the SDK; tests only need the request/response
 * interface, not the full HPKE stack.
 */
private class FakeOhttpHttpClient(
    private val handler: (OhttpHttpClient.Request) -> OhttpHttpClient.Response
) : OhttpHttpClient {
    override fun canHandle(url: String): Boolean = true

    override fun execute(request: OhttpHttpClient.Request): OhttpHttpClient.Response =
        handler(request)
}
