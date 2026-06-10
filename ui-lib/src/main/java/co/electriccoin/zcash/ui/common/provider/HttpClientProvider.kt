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

    suspend fun supportsKtorTimeouts(): Boolean = true
}

class HttpClientProviderImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider
) : HttpClientProvider {
    override suspend fun create(): HttpClient =
        if (isTorEnabledStorageProvider.get() == true) createTor() else createDirect()

    override suspend fun supportsKtorTimeouts(): Boolean = isTorEnabledStorageProvider.get() != true

    private suspend fun createTor() =
        synchronizerProvider
            .getSynchronizer()
            .getTorHttpClient {
                configureHttpClient(installTimeouts = false)
            }

    @Suppress("MagicNumber")
    private fun createDirect() =
        HttpClient(OkHttp) {
            configureHttpClient(installTimeouts = true)
            install(HttpRequestRetry) {
                maxRetries = MAX_RETRIES
                retryIf { request, response ->
                    !request.url.toString().isVotingHelperPath() &&
                        response.status.value in 500..599
                }
                retryOnExceptionIf { request, _ ->
                    !request.url.toString().isVotingHelperPath()
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

private const val MAX_RETRIES = 4
private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 120_000L
private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000L

// Credential-bearing headers redacted from logs even in debug. The shared client also serves the
// CMC quote API (X-CMC_PRO_API_KEY) and the voting helper (X-Helper-Token).
private val SANITIZED_HEADERS = setOf(HttpHeaders.Authorization, "X-CMC_PRO_API_KEY", "X-Helper-Token")
