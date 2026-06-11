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
    suspend fun getExchangeRateQuote(apiKey: String): GetCMCQuoteResponse
}

class CMCApiProviderImpl(
    private val httpClientProvider: HttpClientProvider
) : CMCApiProvider {
    override suspend fun getExchangeRateQuote(apiKey: String): GetCMCQuoteResponse =
        execute {
            get("https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest") {
                parameter("symbol", "ZEC")
                parameter("convert", "USD")
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
