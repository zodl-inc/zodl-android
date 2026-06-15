package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.migration.MigrationPlan
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

interface MigrationPlanRepository {
    fun observe(): Flow<MigrationPlan?>

    suspend fun save(plan: MigrationPlan)

    suspend fun load(): MigrationPlan?

    suspend fun updateTransfer(
        index: Int,
        status: MigrationTransferStatus
    )

    suspend fun clear()
}

class MigrationPlanRepositoryImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : MigrationPlanRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = PreferenceKey("migration_plan")

    override fun observe(): Flow<MigrationPlan?> =
        flow {
            emit(load())
            emitAll(
                encryptedPreferenceProvider()
                    .observe(key = key)
                    .map { encoded -> encoded?.toMigrationPlan() }
            )
        }

    override suspend fun save(plan: MigrationPlan) {
        encryptedPreferenceProvider().putString(
            key = key,
            value = json.encodeToString(MigrationPlan.serializer(), plan)
        )
    }

    override suspend fun load(): MigrationPlan? =
        encryptedPreferenceProvider()
            .getString(key)
            ?.toMigrationPlan()

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
        encryptedPreferenceProvider().remove(key)
    }

    private fun String.toMigrationPlan(): MigrationPlan? =
        runCatching { json.decodeFromString<MigrationPlan>(this) }.getOrNull()
}
