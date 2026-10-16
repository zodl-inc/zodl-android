package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.GetCMCFiatMapResponse
import co.electriccoin.zcash.ui.common.model.GetCMCQuoteResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.DefaultJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface CMCApiProvider {
    @Throws(ResponseException::class)
    suspend fun getExchangeRateQuote(fiat: String): GetCMCQuoteResponse

    /**
     * Returns every fiat currency the exchange-rate provider supports, in the provider's own order.
     */
    @Throws(ResponseException::class)
    suspend fun getFiatMap(): GetCMCFiatMapResponse
}

class CMCApiProviderImpl(
    private val httpClientProvider: HttpClientProvider
) : CMCApiProvider {
    // Privacy: the user's IP must never reach CMC, so always via OHTTP (over Tor when Tor is on).
    // The gateway injects X-CMC_PRO_API_KEY; the app holds no key. Never the clearnet client.

    override suspend fun getExchangeRateQuote(fiat: String): GetCMCQuoteResponse =
        execute("https://$CMC_API_HOST/v1/cryptocurrency/quotes/latest?symbol=ZEC&convert=$fiat")

    override suspend fun getFiatMap(): GetCMCFiatMapResponse =
        execute("https://$CMC_API_HOST/v1/fiat/map")

    /**
     * Executes a CMC API request through OHTTP. The gateway injects X-CMC_PRO_API_KEY server-side;
     * the app sends no key. With Tor ON, the relay leg goes over
     * Tor; with Tor OFF, the relay sees the app's clearnet IP (but CMC never does).
     */
    @Throws(ResponseException::class)
    private suspend inline fun <reified T> execute(url: String): T =
        withContext(Dispatchers.IO) {
            val ohttpClient = httpClientProvider.createOhttp()
            if (!ohttpClient.canHandle(url)) {
                error("No OHTTP proxy mapping for $url")
            }
            val response =
                ohttpClient.execute(
                    OhttpHttpClient.Request(url = url, headers = listOf("Accept" to "application/json"))
                )
            Twig.debug { "OHTTP GET $url -> HTTP ${response.status} (${response.body.size} bytes)" }
            if (response.status !in OHTTP_HTTP_SUCCESS) {
                error("CMC via OHTTP returned HTTP ${response.status} for $url")
            }
            CMC_JSON.decodeFromString<T>(response.body.decodeToString())
        }
}

// MOB-1378: single source of truth for the exchange-rate provider host. Used both to build the request
// URL above and by [HttpClientProvider] to refuse the same host on the direct (clearnet) client.
internal const val CMC_API_HOST = "pro-api.coinmarketcap.com"

private const val OHTTP_HTTP_SUCCESS_MIN = 200
private const val OHTTP_HTTP_SUCCESS_MAX = 299
private val OHTTP_HTTP_SUCCESS = OHTTP_HTTP_SUCCESS_MIN..OHTTP_HTTP_SUCCESS_MAX

/** Same lenient defaults as Ktor's `ContentNegotiation { json() }` on the Tor path (BigDecimal prices). */
private val CMC_JSON = Json(from = DefaultJson) { ignoreUnknownKeys = true }
