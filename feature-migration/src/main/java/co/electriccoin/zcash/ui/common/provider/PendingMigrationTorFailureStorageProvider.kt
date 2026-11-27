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
 * Persisted flag remembering that a background migration send attempt failed specifically because
 * of Tor connectivity and hasn't been resolved yet. Keyed per-account (see [AccountDataSource]) so
 * a Keystone account's background Tor failure never routes the Zashi account into recovery. Set to
 * `true` when `MigrationWorker` hits a non-retryable network error while Tor was in use; cleared on
 * a subsequent successful transfer. Backed by regular (non-encrypted) app storage, wiped on uninstall.
 */
interface PendingMigrationTorFailureStorageProvider : BooleanStorageProvider {
    /** Reads the pending Tor-failure flag for the explicitly supplied account key, bypassing the selected account. */
    suspend fun get(accountKeyId: String): Boolean

    /** Writes the pending Tor-failure flag for the explicitly supplied account key, bypassing the selected account. */
    suspend fun store(accountKeyId: String, value: Boolean)
}

class PendingMigrationTorFailureStorageProviderImpl(
    private val preferenceHolder: StandardPreferenceProvider,
    private val accountDataSource: AccountDataSource,
) : PendingMigrationTorFailureStorageProvider {
    override suspend fun get(): Boolean = default(currentAccountUuid()).getValue(preferenceHolder())

    override suspend fun get(accountKeyId: String): Boolean = default(accountKeyId).getValue(preferenceHolder())

    override suspend fun store(value: Boolean) = default(currentAccountUuid()).putValue(preferenceHolder(), value)

    override suspend fun store(accountKeyId: String, value: Boolean) =
        default(accountKeyId).putValue(preferenceHolder(), value)

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
        BooleanPreferenceDefault(
            key = PreferenceKey("pending_migration_tor_failure_$accountUuid"),
            defaultValue = false
        )
}
