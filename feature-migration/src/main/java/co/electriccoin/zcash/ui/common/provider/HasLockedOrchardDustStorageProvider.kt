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
 * Tracks whether the user has locked the dust balance left behind in Orchard after migration, so
 * Migration Complete shows the "locked" confirmation instead of the "lock balance" prompt on
 * re-entry. Backed by regular (non-encrypted) app storage, wiped on uninstall.
 *
 * Keyed per-account (see [AccountDataSource]) — a Zodl and a Keystone account can each lock their
 * own residual dust independently; locking one must never mark the other as locked too.
 */
interface HasLockedOrchardDustStorageProvider : BooleanStorageProvider

class HasLockedOrchardDustStorageProviderImpl(
    private val preferenceHolder: StandardPreferenceProvider,
    private val accountDataSource: AccountDataSource,
) : HasLockedOrchardDustStorageProvider {
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
        BooleanPreferenceDefault(key = PreferenceKey("has_locked_orchard_dust_$accountUuid"), defaultValue = false)
}
