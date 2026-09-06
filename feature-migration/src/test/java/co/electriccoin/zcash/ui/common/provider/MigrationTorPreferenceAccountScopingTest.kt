package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationTorPreferenceAccountScopingTest {
    /**
     * Build a minimal [WalletAccount] stub whose sdkAccount.accountUuid is stable and
     * distinct from other accounts. We use [AccountFixture.new] with an explicit UUID so that
     * [co.electriccoin.zcash.ui.common.model.toStorageKeyId] returns different hex strings for
     * accountA vs accountB.
     */
    private fun account(uuid: UUID): WalletAccount =
        mockk(relaxed = true) {
            every { sdkAccount } returns AccountFixture.new(accountUuid = uuid)
        }

    @Test
    fun torEnabledFlagIsIsolatedPerAccount() =
        runTest {
            val uuidA = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val uuidB = UUID.fromString("00000000-0000-0000-0000-000000000002")
            val accountA = account(uuidA)
            val accountB = account(uuidB)

            val selected = MutableStateFlow<WalletAccount?>(accountA)
            val accountDataSource = FakeAccountDataSource(selected)
            val prefs = InMemoryPreferenceProvider()
            val holder = mockk<StandardPreferenceProvider> { coEvery { this@mockk() } returns prefs }

            val provider = IsMigrationTorEnabledStorageProviderImpl(holder, accountDataSource)

            // default is true for both accounts
            assertTrue(provider.get())

            // write false while account A is selected
            provider.store(false)
            assertEquals(false, provider.get())

            // switch to account B — unaffected, still default true
            selected.value = accountB
            assertTrue(provider.get())

            // switch back to A — still false
            selected.value = accountA
            assertEquals(false, provider.get())
        }

    @Test
    fun pendingTorFailureFlagIsIsolatedPerAccount() =
        runTest {
            val uuidA = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val uuidB = UUID.fromString("00000000-0000-0000-0000-000000000002")
            val accountA = account(uuidA)
            val accountB = account(uuidB)

            val selected = MutableStateFlow<WalletAccount?>(accountA)
            val accountDataSource = FakeAccountDataSource(selected)
            val prefs = InMemoryPreferenceProvider()
            val holder = mockk<StandardPreferenceProvider> { coEvery { this@mockk() } returns prefs }

            val provider = PendingMigrationTorFailureStorageProviderImpl(holder, accountDataSource)

            assertEquals(false, provider.get()) // default false
            provider.store(true) // account A failed
            assertEquals(true, provider.get())

            selected.value = accountB
            assertEquals(false, provider.get()) // account B unaffected
        }

    @Test
    fun torEnabledGetByAccountKeyIdIgnoresSelectedAccount() =
        runTest {
            val uuidA = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val uuidB = UUID.fromString("00000000-0000-0000-0000-000000000002")
            val accountA = account(uuidA)
            val accountB = account(uuidB)

            val selected = MutableStateFlow<WalletAccount?>(accountA)
            val accountDataSource = FakeAccountDataSource(selected)
            val prefs = InMemoryPreferenceProvider()
            val holder = mockk<StandardPreferenceProvider> { coEvery { this@mockk() } returns prefs }

            val provider = IsMigrationTorEnabledStorageProviderImpl(holder, accountDataSource)
            val keyA = accountA.sdkAccount.accountUuid.toStorageKeyId()

            // write false while A is selected
            provider.store(false)

            // switch selected to B
            selected.value = accountB

            // explicit-account get still reads A's value
            assertEquals(false, provider.get(keyA))

            // unknown key returns the default (true)
            assertTrue(provider.get("ffffffffffffffff"))
        }

    @Test
    fun pendingTorFailureStoreByAccountKeyIdTargetsThatAccount() =
        runTest {
            val uuidA = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val uuidB = UUID.fromString("00000000-0000-0000-0000-000000000002")
            val accountA = account(uuidA)
            val accountB = account(uuidB)

            val selected = MutableStateFlow<WalletAccount?>(accountA)
            val accountDataSource = FakeAccountDataSource(selected)
            val prefs = InMemoryPreferenceProvider()
            val holder = mockk<StandardPreferenceProvider> { coEvery { this@mockk() } returns prefs }

            val provider = PendingMigrationTorFailureStorageProviderImpl(holder, accountDataSource)
            val keyA = accountA.sdkAccount.accountUuid.toStorageKeyId()

            // B selected, but store for A explicitly
            selected.value = accountB
            provider.store(keyA, true)

            // A has the value
            assertEquals(true, provider.get(keyA))

            // selected B has the default (false)
            assertFalse(provider.get())
        }
}

// ---------------------------------------------------------------------------
// Test doubles
// ---------------------------------------------------------------------------

private class InMemoryPreferenceProvider : PreferenceProvider {
    private val map = mutableMapOf<String, String?>()

    override suspend fun hasKey(key: PreferenceKey) = map.containsKey(key.key)

    override suspend fun putString(key: PreferenceKey, value: String?) {
        map[key.key] = value
    }

    override suspend fun putStringSet(key: PreferenceKey, value: Set<String>?) = Unit

    override suspend fun putLong(key: PreferenceKey, value: Long?) {
        map[key.key] = value?.toString()
    }

    override suspend fun getLong(key: PreferenceKey): Long? = map[key.key]?.toLongOrNull()

    override suspend fun getString(key: PreferenceKey): String? = map[key.key]

    override suspend fun getStringSet(key: PreferenceKey): Set<String>? = null

    override fun observe(key: PreferenceKey): Flow<String?> = flow { emit(getString(key)) }

    override suspend fun remove(key: PreferenceKey) {
        map.remove(key.key)
    }

    override suspend fun clearPreferences(): Boolean {
        map.clear()
        return true
    }
}

private class FakeAccountDataSource(
    private val selected: MutableStateFlow<WalletAccount?>
) : AccountDataSource {
    override val allAccounts: StateFlow<List<WalletAccount>?> = MutableStateFlow(null)
    override val selectedAccount: Flow<WalletAccount?> = selected
    override val zashiAccount: Flow<ZashiAccount?> = flowOf(null)

    override suspend fun getAllAccounts(): List<WalletAccount> = listOfNotNull(selected.value)

    override suspend fun getSelectedAccount(): WalletAccount = selected.value!!

    override suspend fun getZashiAccount(): ZashiAccount = error("unsupported")

    override suspend fun selectAccount(account: cash.z.ecc.android.sdk.model.Account) = error("unsupported")

    override suspend fun selectAccount(account: WalletAccount) = error("unsupported")

    override suspend fun importKeystoneAccount(
        ufvk: String,
        seedFingerprint: String,
        index: Long,
        birthday: BlockHeight?
    ): cash.z.ecc.android.sdk.model.Account = error("unsupported")

    override suspend fun requestNextShieldedAddress(): String = error("unsupported")

    override suspend fun deleteAccount(account: WalletAccount) = error("unsupported")
}
