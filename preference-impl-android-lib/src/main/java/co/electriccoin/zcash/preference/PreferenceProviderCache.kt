package co.electriccoin.zcash.preference

import co.electriccoin.zcash.preference.api.PreferenceProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches [PreferenceProvider] instances by preference filename, guaranteeing at most one instance
 * is ever constructed per filename.
 *
 * Creation is locked per-filename rather than behind one global lock, so a slow or hung creation
 * for one file (e.g. an `EncryptedSharedPreferences.create()` call blocked on the Keystore) cannot
 * block creation of any other file. The map mutex guarding [providers] and [creationLocks] is only
 * ever held for map bookkeeping, never across the suspending [create] call.
 *
 * If [create] throws, nothing is cached and the next [getOrCreate] call for that filename retries.
 *
 * Per-filename creation locks are never evicted; this is bounded by the number of distinct
 * filenames the app uses, which is small and fixed.
 */
internal class PreferenceProviderCache {
    private val mapMutex = Mutex()
    private val providers = mutableMapOf<String, PreferenceProvider>()
    private val creationLocks = mutableMapOf<String, Mutex>()

    @Suppress("ReturnCount")
    suspend fun getOrCreate(
        filename: String,
        create: suspend () -> PreferenceProvider
    ): PreferenceProvider {
        val creationLock =
            mapMutex.withLock {
                providers[filename]?.let { return it }
                creationLocks.getOrPut(filename) { Mutex() }
            }
        return creationLock.withLock {
            mapMutex.withLock { providers[filename] }?.let { return it }
            val provider = create()
            mapMutex.withLock { providers[filename] = provider }
            provider
        }
    }
}
