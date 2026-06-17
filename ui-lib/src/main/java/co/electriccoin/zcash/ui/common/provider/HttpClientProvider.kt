package co.electriccoin.zcash.ui.common.provider

import android.util.Log
import co.electriccoin.zcash.ui.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

interface HttpClientProvider {
    suspend fun create(): HttpClient

    /**
     * Returns a client that always routes over Tor, regardless of the user's Tor preference. Used for
     * requests that must never touch clearnet (e.g. exchange-rate fetching, MOB-1378).
     */
    suspend fun createTor(): HttpClient

    suspend fun supportsKtorTimeouts(): Boolean = true
}

class HttpClientProviderImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider
) : HttpClientProvider {
    override suspend fun create(): HttpClient =
        if (isTorEnabledStorageProvider.get() == true) createTor() else createDirect()

    override suspend fun supportsKtorTimeouts(): Boolean = isTorEnabledStorageProvider.get() != true

    override suspend fun createTor() =
        synchronizerProvider
            .getSynchronizer()
            .getTorHttpClient {
                configureHttpClient(installTimeouts = false)
            }

    @Suppress("MagicNumber")
    private fun createDirect() =
        HttpClient(OkHttp) {
            configureHttpClient(installTimeouts = true)
            engine {
                // MOB-1378: Currency Conversion exchange rates must only ever be fetched over Tor (the
                // in-app copy promises rates are fetched over Tor to protect the user's IP). CMCApiProvider
                // already routes through createTor(), so this clearnet client should never see a CMC
                // request — this interceptor is a backstop that fails loudly if a future caller regresses
                // to create(), rather than silently leaking the user's IP / request timing.
                addInterceptor { chain ->
                    if (chain
                            .request()
                            .url.host
                            .isExchangeRateHost()
                    ) {
                        throw ClearnetExchangeRateBlockedError()
                    }
                    chain.proceed(chain.request())
                }
            }
            install(HttpRequestRetry) {
                maxRetries = MAX_RETRIES
                retryIf { request, response ->
                    !request.url.toString().isVotingHelperPath() &&
                        !request.url.host.isExchangeRateHost() &&
                        response.status.value in 500..599
                }
                retryOnExceptionIf { request, _ ->
                    !request.url.toString().isVotingHelperPath() &&
                        !request.url.host.isExchangeRateHost()
                }
                exponentialDelay()
            }
        }

    private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureHttpClient(
        installTimeouts: Boolean
    ) {
        install(ContentNegotiation) { json() }
        if (installTimeouts) {
            install(HttpTimeout) {
                requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS
                connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS
            }
        }
        install(Logging) {
            logger = KtorLogger()
            // MOB-1346: full request/response bodies (swap recipient + refund addresses + amounts,
            // voting payloads) must not reach logcat/bugreports in release. Bodies only in debug.
            level = if (BuildConfig.DEBUG) LogLevel.ALL else LogLevel.NONE
            sanitizeHeader { header -> header in SANITIZED_HEADERS }
        }
        expectSuccess = true
    }
}

private class KtorLogger : Logger {
    override fun log(message: String) {
        message.chunked(MAX_LOG_CHUNK).forEach { Log.d("HttpClient", it) }
    }

    private companion object {
        const val MAX_LOG_CHUNK = 3900
    }
}

private fun String.isVotingHelperPath(): Boolean =
    contains("/shielded-vote/v1/shares") ||
        contains("/shielded-vote/v1/share-status/")

// MOB-1378: the CMC quote API is the exchange-rate provider; requests to it must never leave over the
// direct (clearnet) client. Matched on the host (not a substring of the full URL) so a path or query
// that happens to echo the host can't trigger a false match. The host literal lives in CMCApiProvider.
private fun String.isExchangeRateHost(): Boolean = this == CMC_API_HOST

/**
 * MOB-1378: raised by the direct (clearnet) client's backstop interceptor when an exchange-rate request
 * would leave over clearnet. This is an unrecoverable invariant violation — a caller regressing from
 * createTor() to create() for the CMC host — not a network condition to recover from. Extends
 * [AssertionError] (an `Error`, not an `Exception`) so it is NOT swallowed by the `catch (Exception)`
 * fallbacks downstream (e.g. ExchangeRateRepository) and instead crashes the app loudly. OkHttp's async
 * dispatch rethrows non-IOException throwables on its dispatcher thread, so this propagates to the
 * uncaught-exception handler rather than corrupting the call.
 */
class ClearnetExchangeRateBlockedError :
    AssertionError(
        "Exchange rate fetching over clearnet is not allowed while Tor is disabled"
    )

private const val MAX_RETRIES = 4
private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 120_000L
private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000L

// Credential-bearing headers redacted from logs even in debug. The shared client also serves the
// CMC quote API (X-CMC_PRO_API_KEY) and the voting helper (X-Helper-Token).
private val SANITIZED_HEADERS = setOf(HttpHeaders.Authorization, "X-CMC_PRO_API_KEY", "X-Helper-Token")
