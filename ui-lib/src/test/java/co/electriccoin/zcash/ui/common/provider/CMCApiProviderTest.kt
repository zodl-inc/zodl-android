package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CMC requests always route through OHTTP (with Tor ON the relay leg goes over Tor).
 * The app never sends X-CMC_PRO_API_KEY — the gateway injects it server-side. These tests pin
 * that contract: create() and createTor() must never be called, and the OHTTP path is used.
 */
class CMCApiProviderTest {
    @Test
    fun getExchangeRateQuoteUsesOhttpAndNeverSendsApiKey() =
        runTest {
            val httpClientProvider = RecordingHttpClientProvider()
            val provider = CMCApiProviderImpl(httpClientProvider)

            val response = provider.getExchangeRateQuote(fiat = "USD")

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
            httpClientProvider.capturedRequests.forEach(::assertNoCmcApiKeyHeader)
        }

    @Test
    fun aNon2xxAnswerCarriesItsStatusCode() =
        runTest {
            val provider = CMCApiProviderImpl(RecordingHttpClientProvider(status = 403))

            val failure = assertFailsWith<CmcHttpStatusException> { provider.getExchangeRateQuote(fiat = "USD") }

            assertEquals(403, failure.status)
        }

    @Test
    fun getFiatMapUsesOhttpAndNeverSendsApiKey() =
        runTest {
            val httpClientProvider = RecordingHttpClientProvider()
            val provider = CMCApiProviderImpl(httpClientProvider)

            val response = provider.getFiatMap()

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
            httpClientProvider.capturedRequests.forEach(::assertNoCmcApiKeyHeader)
        }
}

/**
 * The app must never send X-CMC_PRO_API_KEY; the gateway injects it server-side. Asserted against
 * the actual request headers (not the URL) so a regression that reintroduces the header fails the test.
 */
private fun assertNoCmcApiKeyHeader(request: OhttpHttpClient.Request) {
    assertFalse(
        request.headers.any { it.first.equals("X-CMC_PRO_API_KEY", ignoreCase = true) },
        "CMC key must not be sent by the app (gateway injects it)"
    )
}

private const val RATE = 42.0

private const val FIAT_MAP_JSON =
    """{"status":{"credit_count":1},"data":[{"id":2781,"name":"United States Dollar","sign":"$","symbol":"USD"}]}"""

private const val FIAT_MAP_PATH = "/v1/fiat/map"

private class RecordingHttpClientProvider(
    private val status: Int = 200
) : HttpClientProvider {
    override suspend fun isTorEnabled(): Boolean = false

    var createCalls = 0
        private set
    var createTorCalls = 0
        private set
    var createOhttpCalls = 0
        private set
    val requestedUrls = mutableListOf<String>()
    val capturedRequests = mutableListOf<OhttpHttpClient.Request>()

    override suspend fun create(): HttpClient {
        createCalls++
        error("CMC must never be fetched over the direct client (OHTTP only)")
    }

    override suspend fun createTor(): HttpClient {
        createTorCalls++
        error("CMC must never be fetched over the Tor client (OHTTP handles routing)")
    }

    override suspend fun create(tor: Boolean): HttpClient =
        if (tor) createTor() else create()

    override suspend fun createOhttp(): OhttpHttpClient {
        createOhttpCalls++
        return mockk<OhttpHttpClient> {
            every { canHandle(any()) } returns true
            coEvery { execute(any()) } answers {
                val request = firstArg<OhttpHttpClient.Request>()
                requestedUrls += request.url
                capturedRequests += request
                val body =
                    if (request.url.contains(FIAT_MAP_PATH)) {
                        FIAT_MAP_JSON
                    } else {
                        """{"data":{"ZEC":{"quote":{"USD":{"price":$RATE}}}}}"""
                    }
                OhttpHttpClient.Response(
                    status = status,
                    body = body.toByteArray()
                )
            }
        }
    }
}
