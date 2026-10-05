package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.SecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface MetadataKeyStorageProvider {
    suspend fun get(uuid: AccountUuid): MetadataKey?

    suspend fun store(uuid: AccountUuid, key: MetadataKey)
}

class MetadataKeyStorageProviderImpl(
    encryptedPreferenceProvider: EncryptedPreferenceProvider
) : MetadataKeyStorageProvider {
    private val default = MetadataKeyPreferenceDefault(encryptedPreferenceProvider)

    override suspend fun get(uuid: AccountUuid): MetadataKey? = default.getValue(uuid)

    override suspend fun store(uuid: AccountUuid, key: MetadataKey) = default.putValue(uuid, key)
}

private class MetadataKeyPreferenceDefault(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) {
    private val secretKeyAccess: SecretKeyAccess?
        get() = InsecureSecretKeyAccess.get()

    suspend fun getValue(uuid: AccountUuid): MetadataKey? =
        encryptedPreferenceProvider()
            .getStringSet(key = getKey(uuid))
            ?.decode(secretKeyAccess)

    suspend fun putValue(uuid: AccountUuid, newValue: MetadataKey?) {
        encryptedPreferenceProvider().putStringSet(
            key = getKey(uuid),
            value = newValue?.encode(secretKeyAccess)
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getKey(uuid: AccountUuid) = PreferenceKey("metadata_key_${uuid.value.toHexString()}")
}

/**
 * Each entry is encoded as "$index:$base64Bytes" so [decode] can restore [MetadataKey.bytes]'
 * preference order, which a plain [Set] does not preserve on its own.
 */
@OptIn(ExperimentalEncodingApi::class)
private fun MetadataKey?.encode(secretKeyAccess: SecretKeyAccess?): Set<String>? =
    this
        ?.bytes
        ?.mapIndexed { index, secretBytes ->
            "$index:${Base64.encode(secretBytes.toByteArray(secretKeyAccess))}"
        }?.toSet()

/**
 * Returns null, so the caller re-derives the key in canonical order, for an empty set, a legacy
 * set whose entries carry no "$index:" prefix, indices other than exactly 0 until the entry count,
 * or an entry whose bytes are not valid base64.
 */
@OptIn(ExperimentalEncodingApi::class)
private fun Set<String>?.decode(secretKeyAccess: SecretKeyAccess?): MetadataKey? {
    val indexedEntries = this?.map { it.parseIndexedEntry() }
    return if (indexedEntries.isNullOrEmpty() || indexedEntries.any { it == null }) {
        null
    } else {
        val sortedEntries = indexedEntries.filterNotNull().sortedBy { (index, _) -> index }
        if (sortedEntries.map { (index, _) -> index } != sortedEntries.indices.toList()) {
            null
        } else {
            runCatching {
                MetadataKey(
                    sortedEntries.map { (_, encoded) ->
                        SecretBytes.copyFrom(Base64.decode(encoded), secretKeyAccess)
                    }
                )
            }.getOrNull()
        }
    }
}

/**
 * Parses one "$index:$base64Bytes" entry, or null if it carries no "$index:" prefix.
 */
private fun String.parseIndexedEntry(): Pair<Int, String>? {
    val separatorIndex = indexOf(':')
    return if (separatorIndex <= 0) {
        null
    } else {
        substring(0, separatorIndex).toIntOrNull()?.let { index -> index to substring(separatorIndex + 1) }
    }
}
