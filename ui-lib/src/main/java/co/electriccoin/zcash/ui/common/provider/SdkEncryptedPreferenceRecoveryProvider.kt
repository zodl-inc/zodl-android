package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import co.electriccoin.zcash.preference.AndroidPreferenceProvider

interface SdkEncryptedPreferenceRecoveryProvider {
    /**
     * The SDK's encrypted store (`cash.z.ecc.android.sdk.encrypted`) shares this app's Keystore
     * master key, but the SDK has no corruption recovery of its own, and both
     * `Synchronizer.erase` and `Synchronizer.new` open it unguarded. So after the app's own
     * encrypted store had to be recreated, the SDK store must be repaired the same way, or a
     * restore attempt that reaches it crash-loops instead of self-healing. Callers must await this
     * before anything else opens the SDK store.
     */
    suspend fun ensureReadable()
}

internal class SdkEncryptedPreferenceRecoveryProviderImpl(
    private val context: Context
) : SdkEncryptedPreferenceRecoveryProvider {
    override suspend fun ensureReadable() =
        AndroidPreferenceProvider.ensureEncryptedReadable(context, SDK_ENCRYPTED_PREFERENCES_FILENAME)
}

private const val SDK_ENCRYPTED_PREFERENCES_FILENAME = "cash.z.ecc.android.sdk.encrypted"
