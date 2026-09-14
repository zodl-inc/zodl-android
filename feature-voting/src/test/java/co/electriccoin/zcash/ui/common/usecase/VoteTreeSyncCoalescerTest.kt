package co.electriccoin.zcash.ui.common.usecase

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VoteTreeSyncCoalescerTest {
    @Test
    fun concurrentChainsShareOneSync() =
        runTest {
            var syncs = 0
            val coalescer =
                VoteTreeSyncCoalescer {
                    syncs += 1
                    SYNCED_HEIGHT
                }

            val heights =
                listOf(
                    async { coalescer.sync(storeTicket = 0) },
                    async { coalescer.sync(storeTicket = 0) }
                ).awaitAll()

            assertEquals(listOf(SYNCED_HEIGHT, SYNCED_HEIGHT), heights)
            assertEquals(1, syncs)
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun treeSyncRunsWhileASiblingChainAwaitsConfirmation() =
        runTest {
            var syncs = 0
            val confirming = CompletableDeferred<Unit>()
            val coalescer =
                VoteTreeSyncCoalescer {
                    syncs += 1
                    SYNCED_HEIGHT
                }
            // One chain sits between its post and its confirmation; the tree it leaves behind may
            // need a rebuild, but a sibling chain must not wait for it.
            val sibling = async { confirming.await() }

            val pending = async { coalescer.sync(storeTicket = 0) }
            runCurrent()

            assertEquals(1, syncs)
            assertEquals(SYNCED_HEIGHT, pending.await())

            confirming.complete(Unit)
            sibling.await()
        }

    @Test
    fun treeSyncRerunsForPositionStoredAfterCachedSync() =
        runTest {
            var syncs = 0
            val coalescer =
                VoteTreeSyncCoalescer {
                    syncs += 1
                    SYNCED_HEIGHT
                }

            coalescer.sync(storeTicket = 0)
            assertEquals(1, syncs)

            // No newer position was stored, so the cached sync is still good for this chain.
            coalescer.sync(storeTicket = 0)
            assertEquals(1, syncs)

            // A chain that stored its VAN position after that sync takes a newer ticket, which no
            // longer matches the cached sync.
            val ticket = coalescer.nextTicket()
            coalescer.sync(storeTicket = ticket)

            assertEquals(2, syncs)
        }

    @Test
    fun failedSyncDoesNotPoisonLaterCallers() =
        runTest {
            var syncs = 0
            val coalescer =
                VoteTreeSyncCoalescer {
                    syncs += 1
                    if (syncs == 1) error("sync failed") else SYNCED_HEIGHT
                }

            assertFailsWith<IllegalStateException> { coalescer.sync(storeTicket = 0) }

            assertEquals(SYNCED_HEIGHT, coalescer.sync(storeTicket = 0))
            assertEquals(2, syncs)
        }

    private companion object {
        const val SYNCED_HEIGHT = 10L
    }
}
