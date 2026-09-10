package co.electriccoin.zcash.ui.common.usecase

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
    fun treeSyncWaitsForInFlightConfirmationsBeforeSyncing() =
        runTest {
            var syncs = 0
            val coalescer =
                VoteTreeSyncCoalescer {
                    syncs += 1
                    SYNCED_HEIGHT
                }
            // A chain whose leaf is on chain but whose position is not stored yet would make the
            // crate rebuild the whole tree, so no sync may start while it is in that window.
            coalescer.enterConfirmation()

            val pending = async { coalescer.sync(storeTicket = 0) }
            runCurrent()
            assertEquals(0, syncs)

            coalescer.exitConfirmation()

            assertEquals(SYNCED_HEIGHT, pending.await())
            assertEquals(1, syncs)
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
