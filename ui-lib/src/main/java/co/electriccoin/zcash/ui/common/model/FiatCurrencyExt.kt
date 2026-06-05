package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.FiatCurrency

/**
 * Fiat currencies supported for currency conversion, in display order.
 *
 * Defined app-side (rather than in the SDK) since this is app-specific product data.
 */
val supportedFiatCurrencies: List<FiatCurrency> =
    listOf(
        "USD",
        "EUR",
        "GBP",
        "JPY",
        "CAD",
        "AUD",
        "CHF",
        "CNY",
        "KRW",
        "BRL",
        "INR",
        "MXN",
        "SGD",
        "HKD",
        "NOK",
        "SEK",
        "DKK",
        "NZD",
        "NGN",
        "ZAR",
        "TRY",
        "PLN",
        "THB"
    ).map { FiatCurrency(it) }
