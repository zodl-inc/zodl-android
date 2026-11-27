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
 * Tracks whether the one-time "Migration complete" home banner has already been shown, so it
 * doesn't reappear on every app open once acknowledged. Backed by regular (non-encrypted) app
 * storage, so it's wiped on uninstall along with everything else — a fresh install never shows
 * a stale completion banner it never actually earned.
 *
 * Keyed per-account (see [AccountDataSource]) — the app supports a Zodl and a Keystone account
 * migrating independently, and completing migration on one account must never mark the other as
 * "seen" too.
 */
interface HasSeenMigrationCompleteStorageProvider : BooleanStorageProvider

class HasSeenMigrationCompleteStorageProviderImpl(
    private val preferenceHolder: StandardPreferenceProvider,
    private val accountDataSource: AccountDataSource,
) : HasSeenMigrationCompleteStorageProvider {
    override suspend fun get(): Boolean = default(currentAccountUuid()).getValue(preferenceHolder())

    override suspend fun store(value: Boolean) = default(currentAccountUuid()).putValue(preferenceHolder(), value)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<Boolean> =
        accountDataSource.selectedAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(false)
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
        BooleanPreferenceDefault(key = PreferenceKey("has_seen_migration_complete_$accountUuid"), defaultValue = false)
}
