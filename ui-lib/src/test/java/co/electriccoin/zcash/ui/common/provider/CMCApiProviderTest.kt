package co.electriccoin.zcash.ui.common.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MOB-1378: exchange rates must only ever be fetched over Tor. [CMCApiProvider] must ask for the
 * Tor-only client and must never use create(), which falls back to the direct (clearnet) client when
 * Tor is disabled. The same holds for the supported-currency list added in MOB-1707.
 */
class CMCApiProviderTest {
    @Test
    fun getExchangeRateQuoteUsesTorClientAndNeverTheDirectClient() =
        runTest {
            val httpClientProvider = RecordingHttpClientProvider()
            val provider = CMCApiProviderImpl(httpClientProvider)

            val response = provider.getExchangeRateQuote(apiKey = "key", fiat = "USD")

            assertEquals(1, httpClientProvider.createTorCalls, "CMC must use the Tor-only client")
            assertEquals(
                0,
                httpClientProvider.createCalls,
                "CMC must never use create() (which can fall back to clearnet)"
            )
            assertEquals(
                RATE.toBigDecimal(),
                response.data["ZEC"]
                    ?.quote
                    ?.get("USD")
                    ?.price
            )
            assertTrue(httpClientProvider.requestedHosts.all { it == CMC_API_HOST })
        }

    @Test
    fun getFiatMapUsesTorClientAndNeverTheDirectClient() =
        runTest {
            val httpClientProvider = RecordingHttpClientProvider()
            val provider = CMCApiProviderImpl(httpClientProvider)

            val response = provider.getFiatMap(apiKey = "key")

            assertEquals(1, httpClientProvider.createTorCalls, "CMC must use the Tor-only client")
            assertEquals(
                0,
                httpClientProvider.createCalls,
                "CMC must never use create() (which can fall back to clearnet)"
            )
            assertEquals(listOf("USD"), response.data.map { it.symbol })
            assertTrue(httpClientProvider.requestedHosts.all { it == CMC_API_HOST })
            assertEquals(listOf("key"), httpClientProvider.requestedApiKeys)
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
    val requestedHosts = mutableListOf<String>()
    val requestedApiKeys = mutableListOf<String>()

    override suspend fun create(): HttpClient {
        createCalls++
        error("CMC must never be fetched over the direct (clearnet) client")
    }

    override suspend fun createTor(): HttpClient {
        createTorCalls++
        return HttpClient(
            MockEngine { request ->
                requestedHosts += request.url.host
                request.headers["X-CMC_PRO_API_KEY"]?.let { requestedApiKeys += it }
                respond(
                    content =
                        if (request.url.encodedPath == FIAT_MAP_PATH) {
                            FIAT_MAP_JSON
                        } else {
                            """{"data":{"ZEC":{"quote":{"USD":{"price":$RATE}}}}}"""
                        },
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        ) {
            install(ContentNegotiation) { json() }
        }
    }
}
