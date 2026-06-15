package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Mock Orchard balance backing [co.electriccoin.zcash.ui.common.migration.OrchardMigrationSdkMock].
 *
 * Persisted independently of the real wallet balance so migration transfers can actually
 * deplete it as they execute, letting the whole plan → execute → balance-drops cycle be
 * exercised end to end before the real Rust-bridge SDK is ready.
 */
interface MockOrchardBalanceRepository {
    fun observe(): Flow<Long>

    suspend fun get(): Long

    suspend fun decrease(amountZatoshi: Long)

    suspend fun reset()
}

class MockOrchardBalanceRepositoryImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : MockOrchardBalanceRepository {
    private val key = PreferenceKey("mock_orchard_balance_zatoshi")

    override fun observe(): Flow<Long> =
        flow {
            emit(get())
            emitAll(
                encryptedPreferenceProvider()
                    .observe(key = key)
                    .map { it.toBalance() }
            )
        }

    override suspend fun get(): Long = encryptedPreferenceProvider().getString(key).toBalance()

    override suspend fun decrease(amountZatoshi: Long) {
        val updated = (get() - amountZatoshi).coerceAtLeast(0L)
        encryptedPreferenceProvider().putString(key, updated.toString())
    }

    override suspend fun reset() {
        encryptedPreferenceProvider().remove(key)
    }

    private fun String?.toBalance(): Long = this?.toLongOrNull() ?: DEFAULT_BALANCE_ZATOSHI

    companion object {
        const val DEFAULT_BALANCE_ZATOSHI = 1_000_000_000L
    }
}
