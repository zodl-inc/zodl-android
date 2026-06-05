package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.ObserveFiatCurrencyResult
import co.electriccoin.zcash.ui.common.datasource.ExchangeRateDataSource
import co.electriccoin.zcash.ui.common.datasource.ExchangeRateUnavailable
import co.electriccoin.zcash.ui.common.provider.IsExchangeRateEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.common.wallet.RefreshLock
import co.electriccoin.zcash.ui.common.wallet.StaleLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exchangeRateUsdInternal =
        combine(isExchangeRateOptedIn, preferredFiat) { optedIn, fiat -> optedIn to fiat }
            .flatMapLatest { (optedIn, fiat) ->
                if (optedIn == true) {
                    channelFlow {
                        var cache = ObserveFiatCurrencyResult()
                        launch {
                            refreshPipeline
                                .onStart { emit(Unit) }
                                .flatMapLatest {
                                    flow {
                                        emit(cache.copy(isLoading = true))
                                        try {
                                            emit(
                                                cache.copy(
                                                    isLoading = false,
                                                    currencyConversion =
                                                        exchangeRateDataSource
                                                            .getExchangeRate(fiat)
                                                )
                                            )
                                        } catch (e: ExchangeRateUnavailable) {
                                            throw e
                                        } catch (_: Exception) {
                                            emit(cache.copy(isLoading = false))
                                        }
                                    }.catch {
                                        synchronizerProvider.getSynchronizer().refreshExchangeRateUsd()
                                        emitAll(exchangeRateDataSource.observeSynchronizerRoute())
                                    }
                                }.collect {
                                    cache = it
                                    send(cache)
                                }
                        }
                        awaitClose()
                    }
                } else {
                    flowOf(ObserveFiatCurrencyResult())
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(USD_EXCHANGE_REFRESH_LOCK_THRESHOLD),
                initialValue = ObserveFiatCurrencyResult()
            )

    private val usdExchangeRateTimestamp =
        exchangeRateUsdInternal
            .map {
                it.currencyConversion?.timestamp
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
        ) { isOptedIn, exchangeRate, isStale, isRefreshEnabled ->
            createState(isOptedIn, exchangeRate, isStale, isRefreshEnabled)
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
                    )
            )

    private fun createState(
        isOptedIn: Boolean?,
        exchangeRate: ObserveFiatCurrencyResult,
        isStale: Boolean,
        isRefreshEnabled: Boolean,
    ): ExchangeRateState =
        when (isOptedIn) {
            true -> {
                ExchangeRateState.Data(
                    isLoading = exchangeRate.isLoading,
                    isStale = isStale,
                    isRefreshEnabled = isRefreshEnabled,
                    currencyConversion = exchangeRate.currencyConversion,
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

private val USD_EXCHANGE_REFRESH_LOCK_THRESHOLD = 2.minutes
private val USD_EXCHANGE_STALE_LOCK_THRESHOLD = 15.minutes
