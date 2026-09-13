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
    suspend fun getExchangeRateQuote(apiKey: String, fiat: String): GetCMCQuoteResponse

    /**
     * Returns every fiat currency the exchange-rate provider supports, in the provider's own order.
     */
    @Throws(ResponseException::class)
    suspend fun getFiatMap(apiKey: String): GetCMCFiatMapResponse
}

class CMCApiProviderImpl(
    private val httpClientProvider: HttpClientProvider
) : CMCApiProvider {
    // Privacy routing for the exchange-rate provider (MOB-1378: the user's IP must never reach CMC):
    //   Tor OFF → OHTTP: the request is HPKE-encapsulated to the relay and the gateway proxies it to
    //             pro-api.coinmarketcap.com (via cmc.zodl.com) and injects the API key server-side.
    //             CMC sees the gateway's IP; the relay sees only ciphertext.
    //   Tor ON  → Tor-only Ktor client, as before (no OHTTP double hop).
    // The direct (clearnet) client is never used for this host — HttpClientProvider keeps its backstop.
    private val ohttpClient: OhttpHttpClient = httpClientProvider.createOhttp()

    override suspend fun getExchangeRateQuote(apiKey: String, fiat: String): GetCMCQuoteResponse =
        execute(
            url = "https://$CMC_API_HOST/v1/cryptocurrency/quotes/latest?symbol=ZEC&convert=$fiat"
        ) {
            get("https://$CMC_API_HOST/v1/cryptocurrency/quotes/latest") {
                parameter("symbol", "ZEC")
                parameter("convert", fiat)
                contentType(ContentType.Application.Json)
                header("X-CMC_PRO_API_KEY", apiKey)
            }.body()
        }

    override suspend fun getFiatMap(apiKey: String): GetCMCFiatMapResponse =
        execute(url = "https://$CMC_API_HOST/v1/fiat/map") {
            get("https://$CMC_API_HOST/v1/fiat/map") {
                contentType(ContentType.Application.Json)
                header("X-CMC_PRO_API_KEY", apiKey)
            }.body()
        }

    @Throws(ResponseException::class)
    private suspend inline fun <reified T> execute(
        url: String,
        crossinline torBlock: suspend HttpClient.() -> T
    ): T =
        withContext(Dispatchers.IO) {
            if (!httpClientProvider.isTorEnabled() && ohttpClient.canHandle(url)) {
                // The gateway adds X-CMC_PRO_API_KEY itself; the app's key is not sent over OHTTP.
                val response =
                    ohttpClient.execute(
                        OhttpHttpClient.Request(url = url, headers = listOf("Accept" to "application/json"))
                    )
                Twig.debug { "OHTTP GET $url -> HTTP ${response.status} (${response.body.size} bytes)" }
                if (response.status !in OHTTP_HTTP_SUCCESS) {
                    error("CMC via OHTTP returned HTTP ${response.status} for $url")
                }
                CMC_JSON.decodeFromString<T>(response.body.decodeToString())
            } else {
                Twig.debug { "CMC GET $url via Tor client" }
                httpClientProvider.createTor().use { torBlock(it) }
            }
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
