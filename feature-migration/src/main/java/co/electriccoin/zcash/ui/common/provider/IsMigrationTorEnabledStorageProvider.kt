package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Migration-scoped Tor setting, distinct from the app's global [IsTorEnabledStorageProvider].
 * Defaults to `true` (privacy-by-default). Keyed per-account (see [AccountDataSource]) — the app
 * supports a Zodl and a Keystone account migrating independently, so one account's Tor choice must
 * not leak into the other's migration. Only ever written by the migration Tor prompt
 * (`MigrationPrivacyVM`) or "Continue without Tor" (`MigrationTorFailureVM`); read by every
 * migration broadcast site (`MigrationSendingVM`, `MigrationWorker`, `MigrationKeystoneScanVM`).
 * Backed by regular (non-encrypted) app storage, wiped on uninstall.
 */
interface IsMigrationTorEnabledStorageProvider : BooleanStorageProvider {
    /** Reads the Tor-enabled flag for the explicitly supplied account key, bypassing the selected account. */
    suspend fun get(accountKeyId: String): Boolean
}

class IsMigrationTorEnabledStorageProviderImpl(
    private val preferenceHolder: StandardPreferenceProvider,
    private val accountDataSource: AccountDataSource,
) : IsMigrationTorEnabledStorageProvider {
    override suspend fun get(): Boolean = default(currentAccountUuid()).getValue(preferenceHolder())

    override suspend fun get(accountKeyId: String): Boolean = default(accountKeyId).getValue(preferenceHolder())

    override suspend fun store(value: Boolean) = default(currentAccountUuid()).putValue(preferenceHolder(), value)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<Boolean> =
        accountDataSource.selectedAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(true)
            } else {
                flow { emitAll(default(account.sdkAccount.accountUuid.toStorageKeyId()).observe(preferenceHolder())) }
            }
        }

    override suspend fun clear() = default(currentAccountUuid()).clear(preferenceHolder())

    override suspend fun flip() = store(!get())

    private suspend fun currentAccountUuid(): String =
        accountDataSource
            .getSelectedAccount()
            .sdkAccount.accountUuid
            .toStorageKeyId()

    private fun default(accountUuid: String) =
        BooleanPreferenceDefault(key = PreferenceKey("is_migration_tor_enabled_$accountUuid"), defaultValue = true)
}
