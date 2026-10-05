package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The account metadata key, round-tripped through the encrypted store with its preference
 * order intact.
 */
class MetadataKeyStorageProviderTest {
    private val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })

    @Test
    fun aThreeKeyMetadataKeyRoundTripsWithItsOrderIntact() =
        runTest {
            val store = FakeMetadataPreferenceProvider()
            val key = metadataKey(0, 1, 2)

            provider(store).store(accountUuid, key)
            val roundTripped = provider(store).get(accountUuid)

            assertEquals(key.rawBytes(), roundTripped?.rawBytes())
        }

    @Test
    fun aLegacySetWithoutIndexPrefixesDecodesToNull() =
        runTest {
            val store = FakeMetadataPreferenceProvider()
            store.putStringSet(PreferenceKey(metadataPreferenceKey), setOf("QUJD", "REVG"))

            assertNull(provider(store).get(accountUuid))
        }

    @Test
    fun aClearedEntryDecodesToNull() =
        runTest {
            val store = FakeMetadataPreferenceProvider()
            provider(store).store(accountUuid, metadataKey(0, 1))
            assertNotNull(provider(store).get(accountUuid))

            store.putStringSet(PreferenceKey(metadataPreferenceKey), null)

            assertNull(provider(store).get(accountUuid))
        }

    @Test
    fun anEmptySetDecodesToNull() =
        runTest {
            assertNull(storedSet(emptySet()))
        }

    @Test
    fun anEntryWithInvalidBase64DecodesToNull() =
        runTest {
            assertNull(storedSet(setOf("0:QUJD", "1:not base64!")))
        }

    @Test
    fun aNegativeIndexDecodesToNull() =
        runTest {
            assertNull(storedSet(setOf("-1:QUJD", "0:REVG")))
        }

    @Test
    fun aGapInTheIndicesDecodesToNull() =
        runTest {
            assertNull(storedSet(setOf("0:QUJD", "2:REVG")))
        }

    @Test
    fun aDuplicateIndexDecodesToNull() =
        runTest {
            assertNull(storedSet(setOf("0:QUJD", "0:REVG")))
        }

    private suspend fun storedSet(value: Set<String>): MetadataKey? {
        val store = FakeMetadataPreferenceProvider()
        store.putStringSet(PreferenceKey(metadataPreferenceKey), value)
        return provider(store).get(accountUuid)
    }

    private val metadataPreferenceKey get() = "metadata_key_${accountUuid.hex()}"

    @OptIn(ExperimentalStdlibApi::class)
    private fun AccountUuid.hex() = value.toHexString()

    private fun metadataKey(vararg seeds: Int) =
        MetadataKey(
            bytes =
                seeds.map { seed ->
                    SecretBytes.copyFrom(ByteArray(SEED_SIZE) { (it + seed).toByte() }, InsecureSecretKeyAccess.get())
                }
        )

    private fun MetadataKey.rawBytes() = bytes.map { it.toByteArray(InsecureSecretKeyAccess.get()).toList() }

    private fun provider(store: PreferenceProvider) =
        MetadataKeyStorageProviderImpl(
            encryptedPreferenceProvider =
                mockk<EncryptedPreferenceProvider> {
                    coEvery { this@mockk.invoke() } returns store
                }
        )

    private companion object {
        const val SEED_SIZE = 32
    }
}

/**
 * A hand-written in-memory [PreferenceProvider] whose [getStringSet] hands entries back in
 * whatever order it pleases, scrambling insertion order, to prove that
 * [MetadataKeyStorageProviderImpl] restores [MetadataKey.bytes]' original order itself rather
 * than relying on the store's.
 */
private class FakeMetadataPreferenceProvider : PreferenceProvider {
    private val stringSets = mutableMapOf<String, Set<String>?>()

    override suspend fun hasKey(key: PreferenceKey) = stringSets[key.key] != null

    override suspend fun putString(
        key: PreferenceKey,
        value: String?
    ) = Unit

    override suspend fun putStringSet(
        key: PreferenceKey,
        value: Set<String>?
    ) {
        stringSets[key.key] = value
    }

    override suspend fun putLong(
        key: PreferenceKey,
        value: Long?
    ) = Unit

    override suspend fun getLong(key: PreferenceKey): Long? = null

    override suspend fun getString(key: PreferenceKey): String? = null

    override suspend fun getStringSet(key: PreferenceKey): Set<String>? =
        stringSets[key.key]?.sortedDescending()?.toSet()

    override fun observe(key: PreferenceKey): Flow<String?> = flowOf(null)

    override suspend fun remove(key: PreferenceKey) {
        stringSets.remove(key.key)
    }

    override suspend fun clearPreferences(): Boolean {
        stringSets.clear()
        return true
    }
}
