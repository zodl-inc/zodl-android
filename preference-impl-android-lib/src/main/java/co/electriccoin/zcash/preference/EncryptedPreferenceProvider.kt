package co.electriccoin.zcash.preference

import android.content.Context
import co.electriccoin.zcash.preference.api.PreferenceProvider

class EncryptedPreferenceProvider(
    private val context: Context
) : PreferenceHolder() {
    override suspend fun create(): PreferenceProvider =
        AndroidPreferenceProvider.newEncrypted(context, "co.electriccoin.zcash.encrypted")

    suspend fun purgeQuarantine() = purgeEncryptedPreferencesQuarantine(quarantineDirectory(context))
}
