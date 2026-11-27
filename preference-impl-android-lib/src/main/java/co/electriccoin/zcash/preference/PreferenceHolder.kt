package co.electriccoin.zcash.preference

import co.electriccoin.zcash.preference.api.PreferenceProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Double-checked locking: once [preferenceProvider] is set, [invoke] is a lock-free volatile
 * read for the remaining lifetime of the process. The [mutex] is only ever taken until the first
 * successful [create] call. If [create] throws, nothing is cached and a later [invoke] call
 * retries.
 */
abstract class PreferenceHolder {
    @Volatile
    private var preferenceProvider: PreferenceProvider? = null

    private val mutex = Mutex()

    suspend operator fun invoke(): PreferenceProvider =
        preferenceProvider ?: mutex.withLock {
            preferenceProvider ?: create().also { preferenceProvider = it }
        }

    protected abstract suspend fun create(): PreferenceProvider
}
