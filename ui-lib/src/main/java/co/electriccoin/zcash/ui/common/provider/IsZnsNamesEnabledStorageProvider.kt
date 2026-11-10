package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/**
 * Whether the user turned on Zcash names (ZNS). Off by default: while it is off, the wallet makes no request to a ZNS
 * resolver.
 */
interface IsZnsNamesEnabledStorageProvider : BooleanStorageProvider

class IsZnsNamesEnabledStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider
) : BaseBooleanStorageProvider(key = PreferenceKey("is_zns_names_enabled")),
    IsZnsNamesEnabledStorageProvider
