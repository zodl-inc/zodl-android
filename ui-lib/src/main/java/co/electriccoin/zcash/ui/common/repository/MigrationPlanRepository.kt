package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.migration.MigrationPlan
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferStatus
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

interface MigrationPlanRepository {
    fun observe(): Flow<MigrationPlan?>

    suspend fun save(plan: MigrationPlan)

    /**
     * Saves [plan] keyed by the passed [accountKeyId], independent of whichever account is
     * currently selected. Mirrors the [load] accountKeyId overload — see its kdoc.
     */
    suspend fun save(
        accountKeyId: String,
        plan: MigrationPlan
    )

    suspend fun load(): MigrationPlan?

    /**
     * Loads the migration plan keyed by the passed [accountKeyId] (hex storage key derived from the
     * account's UUID via [co.electriccoin.zcash.ui.common.model.toStorageKeyId]), independent of
     * whichever account is currently selected. Returns `null` when no plan has been stored for
     * that key. Used by [co.electriccoin.zcash.work.MigrationWorker] to act on the account it was
     * enqueued for rather than the currently-selected account.
     */
    suspend fun load(accountKeyId: String): MigrationPlan?

    suspend fun updateTransfer(
        index: Int,
        status: MigrationTransferStatus
    )

    suspend fun clear()
}

/**
 * Keyed per-account (see [AccountDataSource]) — a Zodl and a Keystone account can each run their
 * own migration plan independently; without this, completing/clearing one account's plan would
 * clobber the other's in-progress plan (they'd share a single global key).
 */
class MigrationPlanRepositoryImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val accountDataSource: AccountDataSource,
) : MigrationPlanRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<MigrationPlan?> =
        accountDataSource.selectedAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(null)
            } else {
                val key = key(account)
                flow {
                    emit(loadByKey(key))
                    emitAll(
                        encryptedPreferenceProvider()
                            .observe(key = key)
                            .map { encoded -> encoded?.toMigrationPlan() }
                    )
                }
            }
        }

    override suspend fun save(plan: MigrationPlan) {
        encryptedPreferenceProvider().putString(
            key = currentKey(),
            value = json.encodeToString(MigrationPlan.serializer(), plan)
        )
    }

    override suspend fun save(
        accountKeyId: String,
        plan: MigrationPlan
    ) {
        encryptedPreferenceProvider().putString(
            key = PreferenceKey("migration_plan_$accountKeyId"),
            value = json.encodeToString(MigrationPlan.serializer(), plan)
        )
    }

    override suspend fun load(): MigrationPlan? = loadByKey(currentKey())

    override suspend fun load(accountKeyId: String): MigrationPlan? =
        loadByKey(PreferenceKey("migration_plan_$accountKeyId"))

    override suspend fun updateTransfer(
        index: Int,
        status: MigrationTransferStatus
    ) {
        val current = load() ?: return
        val updatedTransfers =
            current.transfers.map { transfer ->
                if (transfer.index == index) transfer.copy(status = status) else transfer
            }
        save(current.copy(transfers = updatedTransfers))
    }

    override suspend fun clear() {
        encryptedPreferenceProvider().remove(currentKey())
    }

    private suspend fun loadByKey(key: PreferenceKey): MigrationPlan? =
        encryptedPreferenceProvider()
            .getString(key)
            ?.toMigrationPlan()

    private suspend fun currentKey(): PreferenceKey = key(accountDataSource.getSelectedAccount())

    private fun key(account: WalletAccount): PreferenceKey =
        PreferenceKey("migration_plan_${account.sdkAccount.accountUuid.toStorageKeyId()}")

    private fun String.toMigrationPlan(): MigrationPlan? =
        runCatching { json.decodeFromString<MigrationPlan>(this) }.getOrNull()
}
