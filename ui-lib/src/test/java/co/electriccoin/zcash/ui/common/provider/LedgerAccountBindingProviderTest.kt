package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The binding a Ledger account is signed with, round-tripped through the encrypted store.
 */
class LedgerAccountBindingProviderTest {
    private val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val otherAccountUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })

    @Test
    fun aSavedBindingComesBackOutOfTheStore() =
        runTest {
            val provider = provider()

            provider.save(accountUuid, deviceIdentityEncoding = "tpk0-deadbeef", zip32AccountIndex = 7L)

            val binding = provider.observe(accountUuid).first()
            assertEquals("tpk0-deadbeef", binding?.deviceIdentityEncoding)
            assertEquals(Zip32AccountIndex.new(7L), binding?.zip32AccountIndex)
        }

    @Test
    fun anAccountWithNoSavedBindingObservesNull() =
        runTest {
            val provider = provider()

            provider.save(accountUuid, deviceIdentityEncoding = "tpk0-deadbeef", zip32AccountIndex = 7L)

            assertNull(provider.observe(otherAccountUuid).first())
        }

    @Test
    fun clearingDropsTheBinding() =
        runTest {
            val provider = provider()
            provider.save(accountUuid, deviceIdentityEncoding = "tpk0-deadbeef", zip32AccountIndex = 7L)

            provider.clear(accountUuid)

            assertNull(provider.observe(accountUuid).first())
        }

    @Test
    fun aHalfWrittenOrUnparsableValueReadsAsNoBindingAtAll() =
        runTest {
            listOf(
                "tpk0-deadbeef",
                "tpk0-deadbeef|",
                "|4",
                "",
                "|",
                "tpk0-deadbeef|notanumber",
                "tpk0-deadbeef|-1",
                "tpk0-deadbeef|4294967296",
            ).forEach { encoded ->
                val store = FakePreferenceProvider()
                store.putString(PreferenceKey(bindingKey), encoded)

                assertNull(provider(store).observe(accountUuid).first(), "encoded=$encoded")
            }
        }

    @Test
    fun theBindingIsStoredUnderOneKeySoItIsNeverHalfPresent() =
        runTest {
            val store = FakePreferenceProvider()

            provider(store).save(accountUuid, deviceIdentityEncoding = "tpk0-deadbeef", zip32AccountIndex = 4L)

            assertEquals("tpk0-deadbeef|4", store.getString(PreferenceKey(bindingKey)))
        }

    private val bindingKey get() = "ledger_account_binding_${accountUuid.hex()}"

    @OptIn(ExperimentalStdlibApi::class)
    private fun AccountUuid.hex() = value.toHexString()

    private fun provider(store: PreferenceProvider = FakePreferenceProvider()) =
        LedgerAccountBindingProviderImpl(
            encryptedPreferenceProvider =
                mockk<EncryptedPreferenceProvider> {
                    coEvery { this@mockk.invoke() } returns store
                }
        )
}

/**
 * A hand-written in-memory [PreferenceProvider], not a MockK proxy: its methods take the
 * [PreferenceKey] value class, and MockK's reflection-based call recorder throws on that
 * combination for suspend members. Each key gets its own flow so `observe` re-emits on every
 * write, which is what the provider's observation is built on.
 */
private class FakePreferenceProvider : PreferenceProvider {
    private val values = mutableMapOf<String, MutableStateFlow<String?>>()

    private fun flowFor(key: PreferenceKey) = values.getOrPut(key.key) { MutableStateFlow(null) }

    override suspend fun hasKey(key: PreferenceKey) = flowFor(key).value != null

    override suspend fun putString(
        key: PreferenceKey,
        value: String?
    ) {
        flowFor(key).value = value
    }

    override suspend fun putStringSet(
        key: PreferenceKey,
        value: Set<String>?
    ) = Unit

    override suspend fun putLong(
        key: PreferenceKey,
        value: Long?
    ) {
        flowFor(key).value = value?.toString()
    }

    override suspend fun getLong(key: PreferenceKey): Long? = flowFor(key).value?.toLongOrNull()

    override suspend fun getString(key: PreferenceKey): String? = flowFor(key).value

    override suspend fun getStringSet(key: PreferenceKey): Set<String>? = null

    override fun observe(key: PreferenceKey): Flow<String?> = flowFor(key)

    override suspend fun remove(key: PreferenceKey) {
        flowFor(key).value = null
    }

    override suspend fun clearPreferences(): Boolean {
        values.clear()
        return true
    }
}
