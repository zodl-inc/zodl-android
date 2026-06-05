package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.FiatCurrencyConversion
import cash.z.ecc.android.sdk.model.ObserveFiatCurrencyResult
import co.electriccoin.zcash.ui.BuildConfig
import co.electriccoin.zcash.ui.common.provider.CMCApiProvider
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

interface ExchangeRateDataSource {
    /**
     * Returns the exchange rate for [fiatCurrency] from CMC if [BuildConfig.ZCASH_CMC_KEY] is available.
     *
     * @throws ExchangeRateUnavailable when exchange rate is not found or server was down
     */
    @Throws(ExchangeRateUnavailable::class, ResponseException::class)
    suspend fun getExchangeRate(fiatCurrency: FiatCurrency): FiatCurrencyConversion

    fun observeSynchronizerRoute(): Flow<ObserveFiatCurrencyResult>
}

class ExchangeRateUnavailable(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class ExchangeRateDataSourceImpl(
    private val cmcApiProvider: CMCApiProvider,
    private val synchronizerProvider: SynchronizerProvider,
) : ExchangeRateDataSource {
    override suspend fun getExchangeRate(fiatCurrency: FiatCurrency): FiatCurrencyConversion {
        @Suppress("ThrowsCount")
        suspend fun getCMCExchangeRate(apiKey: String): FiatCurrencyConversion {
            val exchangeRate =
                try {
                    cmcApiProvider
                        .getExchangeRateQuote(apiKey = apiKey, fiat = fiatCurrency.code)
                        .data["ZEC"]
                        ?.quote
                        ?.get(fiatCurrency.code)
                        ?.price
                } catch (e: ResponseException) {
                    throw ExchangeRateUnavailable(cause = e)
                }
            val price = exchangeRate ?: throw ExchangeRateUnavailable(message = "Exchange rate not found in response")
            return FiatCurrencyConversion(
                timestamp = Clock.System.now(),
                priceOfZec = price.toDouble(),
                fiatCurrency = fiatCurrency
            )
        }

        val cmcKey = BuildConfig.ZCASH_CMC_KEY.takeIf { it.isNotBlank() }

        return if (cmcKey != null) {
            getCMCExchangeRate(cmcKey)
        } else {
            throw ExchangeRateUnavailable(message = "CMC token not present")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSynchronizerRoute(): Flow<ObserveFiatCurrencyResult> =
        channelFlow {
            val exchangeRate =
                synchronizerProvider
                    .synchronizer
                    .flatMapLatest { synchronizer ->
                        synchronizer?.exchangeRateUsd ?: flowOf(ObserveFiatCurrencyResult())
                    }.stateIn(this)

            launch {
                synchronizerProvider
                    .synchronizer
                    .flatMapLatest { it?.status ?: flowOf(null) }
                    .flatMapLatest {
                        when (it) {
                            null -> flowOf(ObserveFiatCurrencyResult())
                            Synchronizer.Status.STOPPED -> emptyFlow()
                            else -> exchangeRate
                        }
                    }.collect { send(it) }
            }

            awaitClose()
        }
}
