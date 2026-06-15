package co.electriccoin.zcash.ui.common.wallet

import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.FiatCurrencyConversion

sealed interface ExchangeRateState {
    data class Data(
        val isLoading: Boolean = true,
        val isStale: Boolean = false,
        val isRefreshEnabled: Boolean = true,
        val currencyConversion: FiatCurrencyConversion? = null,
        val expectedCurrency: FiatCurrency = FiatCurrency.USD,
        val error: ExchangeRateError? = null,
        val onRefresh: () -> Unit,
    ) : ExchangeRateState

    data object OptIn : ExchangeRateState

    data object OptedOut : ExchangeRateState
}

enum class ExchangeRateError {
    /** CMC responded successfully but the quote was missing, or no CMC key is configured. */
    CMC_EXCHANGE_RATE_UNAVAILABLE,

    /** Could not reach/parse CMC: DNS failure, timeout, 4xx (404, auth) or 5xx server error. */
    CMC_SERVER_CONNECTION_ERROR,
}
