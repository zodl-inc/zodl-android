package co.electriccoin.zcash.ui.common.wallet

import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.FiatCurrencyConversion

sealed interface ExchangeRateState {
    data class Data(
        val isLoading: Boolean = true,
        val isStale: Boolean = false,
        val isRefreshEnabled: Boolean = true,
        val currencyConversion: FiatCurrencyConversion? = null,
        val onRefresh: () -> Unit,
    ) : ExchangeRateState

    data object OptIn : ExchangeRateState

    data object OptedOut : ExchangeRateState
}
