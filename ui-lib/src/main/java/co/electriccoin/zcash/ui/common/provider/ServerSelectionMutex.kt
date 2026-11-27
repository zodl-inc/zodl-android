package co.electriccoin.zcash.ui.common.provider

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes every write of the server selection: the mode flags and the wallet endpoint. The user's save (Private,
 * Public, Manual) and the automatic server switch both write them, and a switch decided for one mode must never land
 * after the user chose another. Not reentrant: hold it only around the writes, never across a network call.
 */
class ServerSelectionMutex {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
