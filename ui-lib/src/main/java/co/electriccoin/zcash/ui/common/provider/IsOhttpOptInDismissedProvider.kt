package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/** True once the user chose "Not now" on the private connection opt-in: the home widget never returns. */
interface IsOhttpOptInDismissedProvider : BooleanStorageProvider

class IsOhttpOptInDismissedProviderImpl(
    override val preferenceHolder: EncryptedPreferenceProvider
) : BaseBooleanStorageProvider(PreferenceKey("is_ohttp_opt_in_dismissed")),
    IsOhttpOptInDismissedProvider
