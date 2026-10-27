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
 * Tracks the one-time Home notice that the NU7 network upgrade stopped an account's migration: its
 * remaining transactions were built for the consensus branch before the upgrade, so the run was
 * cancelled (confirmed transfers are unaffected, unconfirmed ones can stay reserved until they expire
 * and the wallet syncs) and the user is offered a new migration of what is left.
 *
 * Keyed per account, like [HasSeenMigrationCompleteStorageProvider]: the drive loop marks the
 * account it ran for, which need not be the selected one, and the notice is shown for the selected
 * account only. Regular app storage, so an uninstall wipes it.
 */
interface MigrationUpgradeNoticeStorageProvider {
    /** Raises the notice for the account with [accountKeyId]. */
    suspend fun markPending(accountKeyId: String)

    /** Acknowledges the notice of the selected account. */
    suspend fun clearSelected()

    /** Whether the selected account has a pending notice. */
    fun observePendingForSelected(): Flow<Boolean>
}

class MigrationUpgradeNoticeStorageProviderImpl(
    private val preferenceHolder: StandardPreferenceProvider,
    private val accountDataSource: AccountDataSource,
) : MigrationUpgradeNoticeStorageProvider {
    override suspend fun markPending(accountKeyId: String) = default(accountKeyId).putValue(preferenceHolder(), true)

    override suspend fun clearSelected() =
        default(
            accountDataSource
                .getSelectedAccount()
                .sdkAccount.accountUuid
                .toStorageKeyId()
        ).clear(preferenceHolder())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePendingForSelected(): Flow<Boolean> =
        accountDataSource.selectedAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(false)
            } else {
                flow { emitAll(default(account.sdkAccount.accountUuid.toStorageKeyId()).observe(preferenceHolder())) }
            }
        }

    private fun default(accountKeyId: String) =
        BooleanPreferenceDefault(
            key = PreferenceKey("migration_upgrade_notice_pending_$accountKeyId"),
            defaultValue = false,
        )
}
