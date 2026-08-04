package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

interface IsIronwoodAnnouncementShownStorageProvider : NullableBooleanStorageProvider

class IsIronwoodAnnouncementShownStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseNullableBooleanStorageProvider(
        key = PreferenceKey("is_ironwood_announcement_shown"),
    ),
    IsIronwoodAnnouncementShownStorageProvider
