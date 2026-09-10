package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.BuildConfig
import co.electriccoin.zcash.ui.common.provider.CMCApiProvider
import io.ktor.client.plugins.ResponseException
import java.util.Currency

interface FiatCurrencyDataSource {
    /**
     * Returns every fiat currency offered for currency conversion, in the provider's own order.
     *
     * @throws ExchangeRateUnavailable when no currency is available or the CMC token is missing
     */
    @Throws(ExchangeRateUnavailable::class, ResponseException::class)
    suspend fun getSupportedFiatCurrencies(): List<FiatCurrency>
}

class FiatCurrencyDataSourceImpl(
    private val cmcApiProvider: CMCApiProvider,
    private val cmcApiKey: String = BuildConfig.ZCASH_CMC_KEY,
) : FiatCurrencyDataSource {
    override suspend fun getSupportedFiatCurrencies(): List<FiatCurrency> {
        val apiKey =
            cmcApiKey.takeIf { it.isNotBlank() }
                ?: throw ExchangeRateUnavailable(message = "CMC token not present")

        val currencies =
            cmcApiProvider
                .getFiatMap(apiKey)
                .data
                .map { it.symbol.uppercase() }
                .toSupportedFiatCurrencies()

        if (currencies.isEmpty()) {
            throw ExchangeRateUnavailable(message = "No supported currencies in response")
        }

        return currencies
    }
}

/** MOB-1707: product decision, never offered. */
internal val EXCLUDED_FIAT_CURRENCY_CODES: Set<String> = setOf("CUP", "IRR", "RUB")

/** Keeps API order; drops excluded, non-alpha-3, ICU-unknown and duplicate codes. */
internal fun List<String>.toSupportedFiatCurrencies(): List<FiatCurrency> =
    distinct()
        .filter { code ->
            code !in EXCLUDED_FIAT_CURRENCY_CODES &&
                FiatCurrency.isAlpha3Code(code) &&
                runCatching { Currency.getInstance(code) }.isSuccess
        }.map { FiatCurrency(it) }
