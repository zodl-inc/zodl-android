package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

interface IsOledThemeEnabledStorageProvider : NullableBooleanStorageProvider

class IsOledThemeEnabledStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseNullableBooleanStorageProvider(
        key = PreferenceKey("is_oled_theme_enabled"),
    ),
    IsOledThemeEnabledStorageProvider
