package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/**
 * Persists the fiat [FiatCurrency] the user has selected for currency conversion.
 *
 * Modeled on [PersistableWalletProvider], but exposes only the plain [NullableStorageProvider]
 * surface (no `require` accessor) since a missing value is a valid state (defaults to USD).
 */
interface PreferredFiatProvider : NullableStorageProvider<FiatCurrency>

class PreferredFiatProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseNullableStorageProvider<FiatCurrency>(),
    PreferredFiatProvider {
    override val default: PreferenceDefault<FiatCurrency?> =
        PreferredFiatPreferenceDefault(PreferenceKey("preferred_fiat_currency"))
}

private class PreferredFiatPreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<FiatCurrency?> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider): FiatCurrency? =
        preferenceProvider
            .getString(key)
            ?.takeIf { FiatCurrency.isAlpha3Code(it) }
            ?.let { FiatCurrency(it) }

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: FiatCurrency?
    ) {
        preferenceProvider.putString(key, newValue?.code)
    }
}
