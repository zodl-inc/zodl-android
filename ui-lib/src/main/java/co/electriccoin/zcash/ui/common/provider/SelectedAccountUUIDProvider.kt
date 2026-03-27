package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface SelectedAccountUUIDProvider {
    val uuid: Flow<AccountUuid?>

    suspend fun getUUID(): AccountUuid?

    suspend fun setUUID(uuid: AccountUuid)

    suspend fun clearUUID()
}

class SelectedAccountUUIDProviderImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : SelectedAccountUUIDProvider {
    private val default = SelectedAccountUUIDPreferenceDefault()

    override val uuid: Flow<AccountUuid?> =
        flow {
            emitAll(default.observe(encryptedPreferenceProvider()))
        }

    override suspend fun getUUID(): AccountUuid? = default.getValue(encryptedPreferenceProvider())

    override suspend fun setUUID(uuid: AccountUuid) {
        default.putValue(encryptedPreferenceProvider(), uuid)
    }

    override suspend fun clearUUID() {
        default.putValue(encryptedPreferenceProvider(), null)
    }
}

private class SelectedAccountUUIDPreferenceDefault : PreferenceDefault<AccountUuid?> {
    override val key: PreferenceKey = PreferenceKey("selected_account_uuid")

    override suspend fun getValue(preferenceProvider: PreferenceProvider) =
        preferenceProvider.getString(key)?.decode()?.let { AccountUuid.new(it) }

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: AccountUuid?
    ) = preferenceProvider.putString(key, newValue?.value?.encode())

    @OptIn(ExperimentalEncodingApi::class)
    private fun ByteArray.encode() = Base64.encode(this)

    @OptIn(ExperimentalEncodingApi::class)
    private fun String?.decode() =
        if (this != null) {
            Base64.decode(this)
        } else {
            null
        }
}
