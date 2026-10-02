package co.electriccoin.zcash.ui.common.provider

import android.util.Log
import cash.z.ecc.android.sdk.util.OhttpCodecFactory
import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.ui.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface HttpClientProvider {
    suspend fun create(): HttpClient

    /**
     * Returns a client that always routes over Tor, regardless of the user's Tor preference. Used for
     * requests that must never touch clearnet (e.g. exchange-rate fetching, MOB-1378).
     */
    suspend fun createTor(): HttpClient

    /**
     * Returns an [OhttpHttpClient] for routing external API requests through the OHTTP gateway.
     * CMC and NEAR always use it, with Tor on or off; the voting config and vote-server calls use it
     * only with Tor OFF (with Tor ON they go Tor-direct). With Tor ON, the relay leg goes over Tor
     * and the gateway injects secrets server-side (the app never sends them). With Tor OFF, the
     * relay sees the app's clearnet IP (but the gateway, which injects secrets, does not).
     */
    suspend fun createOhttp(): OhttpHttpClient

    /**
     * Whether the user has opted into Tor. Callers that choose between a direct and an OHTTP path
     * (voting config, vote servers) must consult this: with Tor ON they go Tor-direct via [create];
     * with Tor OFF they go through the OHTTP gateway via [createOhttp]. OHTTP on top of Tor would be a
     * pointless double hop. Abstract on purpose, so an implementation cannot silently report "off".
     */
    suspend fun isTorEnabled(): Boolean

    suspend fun supportsKtorTimeouts(): Boolean = true
}

class HttpClientProviderImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider
) : HttpClientProvider {
    // Lazy OHTTP client — created on first use, respecting the Tor preference at that moment
    @Volatile
    private var cachedOhttpClient: OhttpHttpClient? = null

    @Volatile
    private var cachedForTorEnabled: Boolean? = null

    @Volatile
    private var policyGeneration = 0
    private val lock = Mutex()

    // Private coroutine scope for OHTTP client disposal. Uses SupervisorJob so a disposal
    // failure doesn't cancel other disposals, and Dispatchers.IO for the blocking native
    // cleanup operations OHTTP disposal may trigger.
    private val disposalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun create(): HttpClient =
        if (isTorEnabledStorageProvider.get() == true) createTor() else createDirect()

    override suspend fun supportsKtorTimeouts(): Boolean = isTorEnabledStorageProvider.get() != true

    override suspend fun isTorEnabled(): Boolean = isTorEnabledStorageProvider.get() == true

    override suspend fun createOhttp(): OhttpHttpClient =
        lock.withLock {
            // OHTTP-06: Atomically read Tor state, fetch sender, and publish client to ensure
            // a concurrent Tor toggle cannot leave a stale-policy sender cached or returned.
            val torNow = isTorEnabled()
            // Invalidate cache if the Tor preference changed
            if (cachedOhttpClient != null && cachedForTorEnabled != torNow) {
                val outgoing = cachedOhttpClient
                cachedOhttpClient = null
                policyGeneration++
                // Dispose the outgoing client to clean up its Tor sender. Launch in a background
                // coroutine with a grace period to allow in-flight requests to complete. OHTTP
                // over Tor can take tens of seconds, so use a 120s window.
                outgoing?.let { client ->
                    disposalScope.launch {
                        delay(OHTTP_CLIENT_DISPOSE_GRACE_MS)
                        client.dispose()
                    }
                }
            }
            if (cachedOhttpClient == null) {
                // The native OHTTP codec (shared zodl-ohttp-client core) comes from the SDK. Obtain it
                // before the Tor sender so a load failure cannot leak a freshly created sender.
                val codec = OhttpCodecFactory.create()
                val senderForTor = if (torNow) synchronizerProvider.getSynchronizer().ohttpSenderOverTor() else null
                // DEBUG-only: pass trace prefix for request tracking in Grafana
                val tracePrefix =
                    if (BuildConfig.DEBUG && BuildConfig.OHTTP_CLIENT_TRACE_PREFIX.isNotBlank()) {
                        BuildConfig.OHTTP_CLIENT_TRACE_PREFIX
                    } else {
                        null
                    }
                cachedOhttpClient =
                    if (torNow) {
                        OhttpHttpClient.create(
                            codec = codec,
                            sender = senderForTor!!,
                            ohttpClientTraceIdPrefix = tracePrefix
                        )
                    } else {
                        OhttpHttpClient.create(
                            codec = codec,
                            ohttpClientTraceIdPrefix = tracePrefix
                        )
                    }
                cachedForTorEnabled = torNow
            }
            cachedOhttpClient!!
        }

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
            install(HttpRequestRetry) { configureClearnetRetry() }
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

/**
 * Retry policy of the direct (clearnet) client: 5xx responses and exceptions are retried with exponential
 * backoff, except for voting paths (ciphertext that must be sent exactly once) and the exchange-rate host.
 * Shared with the retry behaviour tests so they exercise the production policy, not a copy of it.
 */
internal fun HttpRequestRetryConfig.configureClearnetRetry() {
    maxRetries = MAX_RETRIES
    retryIf { request, response ->
        !request.url.toString().isVotingPath() &&
            !request.url.host.isExchangeRateHost() &&
            response.status.value in HTTP_SERVER_ERRORS
    }
    retryOnExceptionIf { request, _ ->
        !request.url.toString().isVotingPath() &&
            !request.url.host.isExchangeRateHost()
    }
    exponentialDelay()
}

// C-08 fix: Exclude all voting POSTs from retry — they carry OHTTP-encapsulated ciphertext
// that must be sent exactly once (gateway replay cache returns 409 on duplicate nonce).
// Covers both helper endpoints (/shares, /share-status) and direct voting POSTs
// (/delegate-vote, /cast-vote) which can route through OHTTP when Tor is OFF.
internal fun String.isVotingPath(): Boolean =
    contains("/shielded-vote/v1/shares") ||
        contains("/shielded-vote/v1/share-status/") ||
        contains("/shielded-vote/v1/delegate-vote") ||
        contains("/shielded-vote/v1/cast-vote")

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

private const val HTTP_SERVER_ERROR_FIRST = 500
private const val HTTP_SERVER_ERROR_LAST = 599
private val HTTP_SERVER_ERRORS = HTTP_SERVER_ERROR_FIRST..HTTP_SERVER_ERROR_LAST

// Grace period before disposing an OHTTP client replaced on a Tor toggle (see createOhttp).
private const val OHTTP_CLIENT_DISPOSE_GRACE_MS = 120_000L
