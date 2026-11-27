package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.design.theme.AppearanceMode

/**
 * Persists the user's chosen [AppearanceMode]. Modeled on [PreferredFiatProvider] - a missing value is a
 * valid state (resolves to [AppearanceMode.SYSTEM]), not an error.
 */
interface AppearanceModeStorageProvider : NullableStorageProvider<AppearanceMode>

/** The stored [AppearanceMode], resolving a never-chosen preference to [AppearanceMode.SYSTEM]. */
suspend fun AppearanceModeStorageProvider.getOrSystem(): AppearanceMode = get() ?: AppearanceMode.SYSTEM

class AppearanceModeStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseNullableStorageProvider<AppearanceMode>(),
    AppearanceModeStorageProvider {
    override val default: PreferenceDefault<AppearanceMode?> =
        AppearanceModePreferenceDefault(PreferenceKey("appearance_mode"))
}

private class AppearanceModePreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<AppearanceMode?> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider): AppearanceMode? =
        preferenceProvider
            .getString(key)
            ?.let { stored -> runCatching { AppearanceMode.valueOf(stored) }.getOrNull() }

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: AppearanceMode?
    ) {
        preferenceProvider.putString(key, newValue?.name)
    }
}
