package co.electriccoin.zcash.ui.common.provider

import android.util.Log
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
}

class HttpClientProviderImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider
) : HttpClientProvider {
    override suspend fun create(): HttpClient =
        if (isTorEnabledStorageProvider.get() == true) createTor() else createDirect()

    private suspend fun createTor() =
        synchronizerProvider
            .getSynchronizer()
            .getTorHttpClient {
                configureHttpClient()
            }

    @Suppress("MagicNumber")
    private fun createDirect() =
        HttpClient(OkHttp) {
            configureHttpClient()
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

    private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureHttpClient() {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) {
            requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS
        }
        install(Logging) {
            logger = KtorLogger()
            level = LogLevel.ALL
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
        expectSuccess = true
    }

    private companion object {
        const val MAX_RETRIES = 4
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 120_000L
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000L
    }
}

private class KtorLogger : Logger {
    override fun log(message: String) {
        Log.d("HttpClient", message)
    }
}

private fun String.isVotingHelperPath(): Boolean =
    contains("/shielded-vote/v1/shares") ||
        contains("/shielded-vote/v1/share-status/")
