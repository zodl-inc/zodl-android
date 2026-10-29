package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/**
 * True when the user runs the automatic server selection on public (clearnet) servers instead of the
 * OHTTP gateway ("Public" in Choose Server). Only meaningful while server selection is automatic.
 */
interface IsPublicConnectionProvider : BooleanStorageProvider

class IsPublicConnectionProviderImpl(
    override val preferenceHolder: EncryptedPreferenceProvider
) : BaseBooleanStorageProvider(PreferenceKey("is_public_connection")),
    IsPublicConnectionProvider
