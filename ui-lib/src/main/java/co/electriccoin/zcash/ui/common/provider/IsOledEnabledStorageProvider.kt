package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/**
 * Whether pure black (OLED) should be used whenever the resolved [AppearanceMode][co.electriccoin.zcash.ui.design.theme.AppearanceMode]
 * is dark. Independent of the mode itself - a null (never chosen) value resolves to false.
 */
interface IsOledEnabledStorageProvider : NullableBooleanStorageProvider

class IsOledEnabledStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseNullableBooleanStorageProvider(
        key = PreferenceKey("is_oled_enabled"),
    ),
    IsOledEnabledStorageProvider
