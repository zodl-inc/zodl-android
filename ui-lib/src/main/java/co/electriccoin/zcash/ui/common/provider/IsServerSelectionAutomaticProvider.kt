package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

interface IsServerSelectionAutomaticProvider : NullableBooleanStorageProvider

class IsServerSelectionAutomaticProviderImpl(
    override val preferenceHolder: EncryptedPreferenceProvider
) : BaseNullableBooleanStorageProvider(PreferenceKey("is_server_selection_automatic")),
    IsServerSelectionAutomaticProvider
