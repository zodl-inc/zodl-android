package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/**
 * True while Automatic mode is running on a clearnet lightwalletd because the OHTTP gateway was
 * unreachable. Cleared when the wallet returns to the gateway or the user switches to Manual mode.
 * Drives the periodic "is OHTTP back?" re-check and the Automatic subtitle in Choose Server.
 */
interface IsOhttpFallbackActiveProvider : BooleanStorageProvider

class IsOhttpFallbackActiveProviderImpl(
    override val preferenceHolder: EncryptedPreferenceProvider
) : BaseBooleanStorageProvider(PreferenceKey("is_ohttp_fallback_active")),
    IsOhttpFallbackActiveProvider
