package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.ObserveFiatCurrencyResult
import co.electriccoin.zcash.ui.common.datasource.ExchangeRateDataSource
import co.electriccoin.zcash.ui.common.datasource.ExchangeRateUnavailable
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.CmcHttpStatusException
import co.electriccoin.zcash.ui.common.provider.IsExchangeRateEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateError
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.common.wallet.RefreshLock
import co.electriccoin.zcash.ui.common.wallet.StaleLock
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.net.UnknownHostException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface ExchangeRateRepository {
    val state: StateFlow<ExchangeRateState>

    fun refreshExchangeRateUsd()
}

class ExchangeRateRepositoryImpl(
    isExchangeRateEnabledStorageProvider: IsExchangeRateEnabledStorageProvider,
    preferredFiatProvider: PreferredFiatProvider,
    private val exchangeRateDataSource: ExchangeRateDataSource,
    private val synchronizerProvider: SynchronizerProvider,
) : ExchangeRateRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val refreshPipeline = MutableSharedFlow<Unit>()

    private val isExchangeRateOptedIn =
        isExchangeRateEnabledStorageProvider
            .observe()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = null
            )

    private val preferredFiat =
        preferredFiatProvider
            .observe()
            .map { it ?: FiatCurrency.USD }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = FiatCurrency.USD
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exchangeRateUsdInternal =
        combine(isExchangeRateOptedIn, preferredFiat) { optedIn, fiat -> optedIn to fiat }
            .flatMapLatest { (optedIn, fiat) ->
                if (optedIn == true) {
                    channelFlow {
                        var cache = ExchangeRateResult(ObserveFiatCurrencyResult(isLoading = true))
                        val hasExchangeRate = MutableStateFlow(false)
                        val isFetching = MutableStateFlow(true)
                        val isLastFailureTransient = MutableStateFlow(true)
                        launch {
                            // The short retries cover a blip. An outage that outlasts them would leave "unavailable" on
                            // screen until the app restarts, so while there is no rate at all keep asking, slowly: the
                            // wait doubles up to a cap, and it stops for a failure that would repeat identically
                            // (a currency CMC has no quote for, a 4xx answer such as a rejected key, an unreadable
                            // answer).
                            combine(hasExchangeRate, isLastFailureTransient) { hasRate, isTransient ->
                                shouldKeepAskingForExchangeRate(hasRate, isTransient)
                            }.distinctUntilChanged()
                                .collectLatest { keepAsking ->
                                    var wait = EXCHANGE_RATE_RETRY_WHILE_MISSING
                                    while (keepAsking) {
                                        delay(wait)
                                        // A lookup that is still running is not cut short by the next tick.
                                        if (!isFetching.value) refreshPipeline.emit(Unit)
                                        wait = nextExchangeRateRetryWait(wait)
                                    }
                                }
                        }
                        launch {
                            refreshPipeline
                                .onStart { emit(Unit) }
                                .flatMapLatest {
                                    flow {
                                        emit(cache.copy(observed = cache.observed.copy(isLoading = true)))
                                        emit(
                                            ExchangeRateResult(
                                                observed =
                                                    cache.observed.copy(
                                                        isLoading = false,
                                                        currencyConversion =
                                                            retryTransientExchangeRateFailure {
                                                                exchangeRateDataSource.getExchangeRate(fiat)
                                                            }
                                                    )
                                            )
                                        )
                                    }.catch { error ->
                                        isLastFailureTransient.value = isTransientExchangeRateFailure(error)
                                        if (shouldFallBackToSynchronizerRoute(VersionInfo.IS_CMC_AVAILABLE)) {
                                            synchronizerProvider.getSynchronizer().refreshExchangeRateUsd()
                                            emitAll(
                                                exchangeRateDataSource
                                                    .observeSynchronizerRoute()
                                                    .map { ExchangeRateResult(observed = it) }
                                            )
                                        } else {
                                            emit(
                                                cache.copy(
                                                    observed = cache.observed.copy(isLoading = false),
                                                    error = classifyExchangeRateError(error)
                                                )
                                            )
                                        }
                                    }
                                }.collect {
                                    cache = it
                                    hasExchangeRate.value = it.observed.currencyConversion != null
                                    isFetching.value = it.observed.isLoading
                                    if (hasExchangeRate.value) isLastFailureTransient.value = true
                                    send(cache)
                                }
                        }
                        awaitClose()
                    }
                } else {
                    flowOf(ExchangeRateResult())
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(USD_EXCHANGE_REFRESH_LOCK_THRESHOLD),
                initialValue = ExchangeRateResult(ObserveFiatCurrencyResult(isLoading = true))
            )

    private val usdExchangeRateTimestamp =
        exchangeRateUsdInternal
            .map {
                it.observed.currencyConversion?.timestamp
            }.distinctUntilChanged()

    private val refreshExchangeRateUsdLock =
        RefreshLock(
            timestampToObserve = usdExchangeRateTimestamp,
            lockDuration = USD_EXCHANGE_REFRESH_LOCK_THRESHOLD
        )

    private val staleExchangeRateUsdLock =
        StaleLock(
            timestampToObserve = usdExchangeRateTimestamp,
            lockDuration = USD_EXCHANGE_STALE_LOCK_THRESHOLD,
            onRefresh = { refreshExchangeRateUsdInternal().join() }
        )

    override val state: StateFlow<ExchangeRateState> =
        combine(
            isExchangeRateOptedIn,
            exchangeRateUsdInternal,
            staleExchangeRateUsdLock.state,
            refreshExchangeRateUsdLock.state,
            preferredFiat,
        ) { isOptedIn, exchangeRate, isStale, isRefreshEnabled, expectedCurrency ->
            createState(isOptedIn, exchangeRate, isStale, isRefreshEnabled, expectedCurrency)
        }.distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5.seconds),
                initialValue =
                    createState(
                        isOptedIn = isExchangeRateOptedIn.value,
                        exchangeRate = exchangeRateUsdInternal.value,
                        isStale = false,
                        isRefreshEnabled = false,
                        expectedCurrency = preferredFiat.value,
                    )
            )

    private fun createState(
        isOptedIn: Boolean?,
        exchangeRate: ExchangeRateResult,
        isStale: Boolean,
        isRefreshEnabled: Boolean,
        expectedCurrency: FiatCurrency,
    ): ExchangeRateState =
        when (isOptedIn) {
            true -> {
                ExchangeRateState.Data(
                    isLoading = exchangeRate.observed.isLoading,
                    isStale = isStale,
                    isRefreshEnabled =
                        isRefreshAllowed(
                            isRefreshEnabled = isRefreshEnabled,
                            hasExchangeRate = exchangeRate.observed.currencyConversion != null
                        ),
                    currencyConversion = exchangeRate.observed.currencyConversion,
                    expectedCurrency = expectedCurrency,
                    error = exchangeRate.error,
                    onRefresh = ::refreshExchangeRateUsd
                )
            }

            false -> {
                ExchangeRateState.OptedOut
            }

            null -> {
                ExchangeRateState.OptIn
            }
        }

    override fun refreshExchangeRateUsd() {
        refreshExchangeRateUsdInternal()
    }

    private fun refreshExchangeRateUsdInternal() =
        scope.launch {
            val value = state.value
            if (value is ExchangeRateState.Data && value.isRefreshEnabled && !value.isLoading) {
                refreshPipeline.emit(Unit)
            }
        }
}

/**
 * Decides whether to use the SDK synchronizer route (its own USD fetch, over Tor straight to public
 * exchanges) instead of the CMC lookup through the OHTTP gateway.
 *
 * Only when CMC is not available at all, as it is then the only rate source. A failed CMC lookup
 * never falls back around the gateway (ZIP #10): the failure is shown instead.
 */
internal fun shouldFallBackToSynchronizerRoute(isCmcAvailable: Boolean): Boolean = !isCmcAvailable

/**
 * Maps a CMC exchange-rate failure to the user-facing [ExchangeRateError], or `null` when the
 * failure is a transient connectivity problem that shouldn't surface a dedicated error sheet.
 *
 * - [ExchangeRateUnavailable] → CMC answered but the quote was missing (or no CMC key is
 *   configured) → [ExchangeRateError.CMC_EXCHANGE_RATE_UNAVAILABLE].
 * - Definitive server-side problems → [ExchangeRateError.CMC_SERVER_CONNECTION_ERROR]:
 *     - [ResponseException] → the server answered with an HTTP error (5xx, 401/403 auth, 404, …).
 *     - [UnknownHostException] → DNS can't resolve the host.
 *     - [ContentConvertException] (e.g. ktor's JsonConvertException) / [NoTransformationFoundException]
 *       → the server answered but the payload couldn't be parsed into the expected type.
 * - Everything else (socket/connect/request timeouts, generic [java.io.IOException] from a weak or
 *   dropped connection, …) is transient → `null`, so no error sheet is shown.
 */
internal fun classifyExchangeRateError(error: Throwable): ExchangeRateError? =
    when (error) {
        is ExchangeRateUnavailable -> ExchangeRateError.CMC_EXCHANGE_RATE_UNAVAILABLE
        is ResponseException -> ExchangeRateError.CMC_SERVER_CONNECTION_ERROR
        is UnknownHostException -> ExchangeRateError.CMC_SERVER_CONNECTION_ERROR
        is ContentConvertException -> ExchangeRateError.CMC_SERVER_CONNECTION_ERROR
        is NoTransformationFoundException -> ExchangeRateError.CMC_SERVER_CONNECTION_ERROR
        else -> null
    }

/**
 * Whether a refresh may start. The time lock only guards a rate that was already loaded (its timestamp starts
 * the lock); with no rate at all there is no timestamp, so the lock reports "disabled" and, if the first lookup
 * failed, nothing could ever start another one. A missing rate must therefore always allow a refresh.
 */
internal fun isRefreshAllowed(
    isRefreshEnabled: Boolean,
    hasExchangeRate: Boolean
): Boolean = isRefreshEnabled || !hasExchangeRate

/**
 * Whether a failed CMC lookup is worth repeating. A missing quote ([ExchangeRateUnavailable]), an unreadable answer
 * and an HTTP answer that says the request itself is wrong ([CmcHttpStatusException] with a 4xx other than 408 and
 * 429, such as a rejected key) repeat identically. Everything else (the OHTTP gateway answering with a 5xx, 429 or
 * 408, a dropped relay connection, a timeout) is the kind of failure a second attempt often survives.
 */
internal fun isTransientExchangeRateFailure(error: Throwable): Boolean =
    when (error) {
        is CancellationException -> false
        is ExchangeRateUnavailable -> false
        is ContentConvertException -> false
        is NoTransformationFoundException -> false
        is SerializationException -> false
        is CmcHttpStatusException -> isTransientHttpStatus(error.status)
        else -> true
    }

private fun isTransientHttpStatus(status: Int): Boolean =
    status >= HTTP_SERVER_ERROR_MIN || status == HTTP_REQUEST_TIMEOUT || status == HTTP_TOO_MANY_REQUESTS

/**
 * Runs [block] and repeats it after each of [retryDelays] when it fails with a transient failure
 * ([isTransientExchangeRateFailure]). The last failure is rethrown, so the caller still classifies it as before.
 */
internal suspend fun <T> retryTransientExchangeRateFailure(
    retryDelays: List<Duration> = EXCHANGE_RATE_RETRY_DELAYS,
    block: suspend () -> T
): T {
    var attempt = 0
    while (true) {
        val result = runCatching { block() }
        val error = result.exceptionOrNull() ?: return result.getOrThrow()
        // Cancellation is not transient, so it is rethrown here like every other failure that is not retried.
        if (attempt >= retryDelays.size || !isTransientExchangeRateFailure(error)) throw error
        delay(retryDelays[attempt])
        attempt++
    }
}

/** Whether the background loop keeps asking: only while there is no rate and the last failure may pass. */
internal fun shouldKeepAskingForExchangeRate(
    hasExchangeRate: Boolean,
    isLastFailureTransient: Boolean
): Boolean = !hasExchangeRate && isLastFailureTransient

/** The wait before the next background lookup: double the last one, up to [EXCHANGE_RATE_RETRY_WHILE_MISSING_MAX]. */
internal fun nextExchangeRateRetryWait(current: Duration): Duration =
    (current * 2).coerceAtMost(EXCHANGE_RATE_RETRY_WHILE_MISSING_MAX)

/**
 * App-side wrapper pairing the SDK's [ObserveFiatCurrencyResult] with an app-level
 * [ExchangeRateError], since the SDK type cannot carry our error classification.
 */
private data class ExchangeRateResult(
    val observed: ObserveFiatCurrencyResult = ObserveFiatCurrencyResult(),
    val error: ExchangeRateError? = null,
)

private val EXCHANGE_RATE_RETRY_DELAYS = listOf(2.seconds, 6.seconds)
private const val HTTP_SERVER_ERROR_MIN = 500
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private val EXCHANGE_RATE_RETRY_WHILE_MISSING = 30.seconds
private val EXCHANGE_RATE_RETRY_WHILE_MISSING_MAX = 5.minutes

private val USD_EXCHANGE_REFRESH_LOCK_THRESHOLD = 2.minutes
private val USD_EXCHANGE_STALE_LOCK_THRESHOLD = 15.minutes
