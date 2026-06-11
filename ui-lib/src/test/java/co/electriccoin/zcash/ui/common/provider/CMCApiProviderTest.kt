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
 * Tor is disabled.
 */
class CMCApiProviderTest {
    @Test
    fun getExchangeRateQuoteUsesTorClientAndNeverTheDirectClient() =
        runTest {
            val httpClientProvider = RecordingHttpClientProvider()
            val provider = CMCApiProviderImpl(httpClientProvider)

            val response = provider.getExchangeRateQuote(apiKey = "key")

            assertEquals(1, httpClientProvider.createTorCalls, "CMC must use the Tor-only client")
            assertEquals(
                0,
                httpClientProvider.createCalls,
                "CMC must never use create() (which can fall back to clearnet)"
            )
            assertEquals(RATE.toBigDecimal(), response.data["ZEC"]?.quote?.get("USD")?.price)
            assertTrue(httpClientProvider.requestedHosts.all { it == CMC_HOST })
        }
}

private const val RATE = 42.0
private const val CMC_HOST = "pro-api.coinmarketcap.com"

private class RecordingHttpClientProvider : HttpClientProvider {
    var createCalls = 0
        private set
    var createTorCalls = 0
        private set
    val requestedHosts = mutableListOf<String>()

    override suspend fun create(): HttpClient {
        createCalls++
        error("CMC must never be fetched over the direct (clearnet) client")
    }

    override suspend fun createTor(): HttpClient {
        createTorCalls++
        return HttpClient(
            MockEngine { request ->
                requestedHosts += request.url.host
                respond(
                    content = """{"data":{"ZEC":{"quote":{"USD":{"price":$RATE}}}}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        ) {
            install(ContentNegotiation) { json() }
        }
    }
}
