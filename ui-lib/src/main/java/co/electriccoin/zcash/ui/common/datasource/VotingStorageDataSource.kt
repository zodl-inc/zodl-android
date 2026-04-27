package co.electriccoin.zcash.ui.common.datasource

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists voting round state across process restarts.
 *
 * Stores the minimum data needed to recover after interruptions during long-running ZKP generation
 * or share submission. The stored hotkey secret must be treated as sensitive.
 */
interface VotingStorageDataSource {
    suspend fun getHotkeySecret(roundIdHex: String): ByteArray?

    suspend fun storeHotkeySecret(roundIdHex: String, secret: ByteArray)

    suspend fun getPhase(roundIdHex: String): String?

    suspend fun storePhase(roundIdHex: String, phase: String)

    suspend fun getSubmittedShares(roundIdHex: String): Set<Int>

    suspend fun markShareSubmitted(roundIdHex: String, shareIndex: Int)

    suspend fun clearRound(roundIdHex: String)
}

/**
 * In-memory implementation. Suitable for initial development; replace with encrypted DataStore
 * for production use so state survives process death.
 */
class InMemoryVotingStorageDataSource : VotingStorageDataSource {
    private val mutex = Mutex()
    private val hotkeySecrets = mutableMapOf<String, ByteArray>()
    private val phases = mutableMapOf<String, String>()
    private val submittedShares = mutableMapOf<String, MutableSet<Int>>()

    override suspend fun getHotkeySecret(roundIdHex: String): ByteArray? =
        mutex.withLock { hotkeySecrets[roundIdHex] }

    override suspend fun storeHotkeySecret(roundIdHex: String, secret: ByteArray) =
        mutex.withLock { hotkeySecrets[roundIdHex] = secret }

    override suspend fun getPhase(roundIdHex: String): String? =
        mutex.withLock { phases[roundIdHex] }

    override suspend fun storePhase(roundIdHex: String, phase: String) =
        mutex.withLock { phases[roundIdHex] = phase }

    override suspend fun getSubmittedShares(roundIdHex: String): Set<Int> =
        mutex.withLock { submittedShares[roundIdHex]?.toSet() ?: emptySet() }

    override suspend fun markShareSubmitted(roundIdHex: String, shareIndex: Int) {
        mutex.withLock {
            submittedShares.getOrPut(roundIdHex) { mutableSetOf() }.add(shareIndex)
        }
    }

    override suspend fun clearRound(roundIdHex: String) {
        mutex.withLock {
            hotkeySecrets.remove(roundIdHex)
            phases.remove(roundIdHex)
            submittedShares.remove(roundIdHex)
        }
    }
}
