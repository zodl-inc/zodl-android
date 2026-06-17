package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.GetCMCQuoteResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CMCApiProvider {
    @Throws(ResponseException::class)
    suspend fun getExchangeRateQuote(apiKey: String, fiat: String): GetCMCQuoteResponse
}

class CMCApiProviderImpl(
    private val httpClientProvider: HttpClientProvider
) : CMCApiProvider {
    override suspend fun getExchangeRateQuote(apiKey: String, fiat: String): GetCMCQuoteResponse =
        execute {
            get("https://$CMC_API_HOST/v1/cryptocurrency/quotes/latest") {
                parameter("symbol", "ZEC")
                parameter("convert", fiat)
                contentType(ContentType.Application.Json)
                header("X-CMC_PRO_API_KEY", apiKey)
            }.body()
        }

    @Suppress("TooGenericExceptionCaught")
    @Throws(ResponseException::class)
    private suspend inline fun <T> execute(
        crossinline block: suspend HttpClient.() -> T
    ): T =
        // MOB-1378: exchange rates must only ever be fetched over Tor to protect the user's IP, so use
        // the Tor-only client rather than create(), which would fall back to clearnet when Tor is off.
        withContext(Dispatchers.IO) { httpClientProvider.createTor().use { block(it) } }
}

// MOB-1378: single source of truth for the exchange-rate provider host. Used both to build the request
// URL above and by [HttpClientProvider] to refuse the same host on the direct (clearnet) client.
internal const val CMC_API_HOST = "pro-api.coinmarketcap.com"
