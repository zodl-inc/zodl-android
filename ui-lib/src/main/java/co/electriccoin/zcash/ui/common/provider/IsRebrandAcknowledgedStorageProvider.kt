package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

interface IsRebrandAcknowledgedStorageProvider : NullableBooleanStorageProvider

class IsRebrandAcknowledgedStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseNullableBooleanStorageProvider(
        key = PreferenceKey("is_rebrand_acknowledged"),
    ),
    IsRebrandAcknowledgedStorageProvider
