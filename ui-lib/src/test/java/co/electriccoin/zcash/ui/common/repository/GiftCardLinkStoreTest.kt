package co.electriccoin.zcash.ui.common.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GiftCardLinkStoreTest {
    @Test
    fun stashedLinkCanBeTakenOnce() {
        val store = GiftCardLinkStoreImpl()
        val id = store.stash(LINK)

        assertFalse(id.contains("zgift"))
        assertEquals(LINK, store.take(id))
        assertNull(store.take(id))
    }

    @Test
    fun eachStashGetsItsOwnId() {
        val store = GiftCardLinkStoreImpl()

        assertNotEquals(store.stash(LINK), store.stash(LINK))
    }

    /**
     * Many threads stash links while others take them, each id from several threads at once: every link is returned
     * exactly once, to exactly one taker, and nothing is lost or left behind.
     */
    @Test
    fun concurrentStashesAndTakesReturnEachLinkExactlyOnce() {
        val store = GiftCardLinkStoreImpl()
        val executor = Executors.newFixedThreadPool(THREADS)
        val start = CountDownLatch(1)
        val ids = ConcurrentHashMap<String, String>()
        val taken = ConcurrentLinkedQueue<Pair<String, String>>()
        try {
            val stashers =
                (0 until THREADS).map { thread ->
                    executor.submit {
                        start.await()
                        repeat(LINKS_PER_THREAD) { index ->
                            val link = "$LINK&n=$thread-$index"
                            ids[store.stash(link)] = link
                        }
                    }
                }
            start.countDown()
            stashers.forEach { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }

            val takeStart = CountDownLatch(1)
            val takers =
                (0 until THREADS).map {
                    executor.submit {
                        takeStart.await()
                        ids.keys.shuffled().forEach { id -> store.take(id)?.let { taken += id to it } }
                    }
                }
            takeStart.countDown()
            takers.forEach { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(THREADS * LINKS_PER_THREAD, ids.size)
        assertEquals(ids.size, taken.size)
        assertEquals(ids, taken.toMap())
        ids.keys.forEach { assertNull(store.take(it)) }
    }

    /** Stashing and taking interleaved on many threads: each link stashed is taken back exactly once. */
    @Test
    fun interleavedStashAndTakeOnManyThreadsLoseNothing() {
        val store = GiftCardLinkStoreImpl()
        val executor = Executors.newFixedThreadPool(THREADS)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Pair<String, String?>>()
        try {
            (0 until THREADS)
                .map { thread ->
                    executor.submit {
                        start.await()
                        repeat(LINKS_PER_THREAD) { index ->
                            val link = "$LINK&n=$thread-$index"
                            val id = store.stash(link)
                            results += link to store.take(id)
                            results += link to store.take(id)
                        }
                    }
                }.also { start.countDown() }
                .forEach { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        val byLink = results.groupBy({ it.first }, { it.second })
        assertEquals(THREADS * LINKS_PER_THREAD, byLink.size)
        byLink.forEach { (link, takes) -> assertEquals(listOf(link, null), takes, link) }
    }

    @Test
    fun inAppScanRequestIsConsumedOnce() {
        val store = GiftCardLinkStoreImpl()
        assertFalse(store.isInAppScanRequested.value)
        assertFalse(store.consumeInAppScanRequest())

        store.requestInAppScan()
        store.requestInAppScan()

        assertTrue(store.isInAppScanRequested.value)
        assertTrue(store.consumeInAppScanRequest())
        assertFalse(store.consumeInAppScanRequest())
        assertFalse(store.isInAppScanRequested.value)
    }

    @Test
    fun prefixCheckAcceptsOnlyGiftCardLinks() {
        assertTrue(GiftCardLinkPrefixes.matches(LINK))
        assertTrue(GiftCardLinkPrefixes.matches("  $LINK "))
        assertTrue(GiftCardLinkPrefixes.matches("https://link.vizor.cash/payment-links/open#v1=abc"))
        assertTrue(GiftCardLinkPrefixes.matches("https://gift.zodl.com#v=1&key=zgift1abc&height=1"))
        assertFalse(GiftCardLinkPrefixes.matches("https://gift.zodl.com/about"))
        assertFalse(GiftCardLinkPrefixes.matches("https://gift.zodl.com.evil.example/#v=1"))
        assertFalse(GiftCardLinkPrefixes.matches("zcash:u1address"))
        assertFalse(GiftCardLinkPrefixes.matches(""))
    }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val THREADS = 8
        const val LINKS_PER_THREAD = 500
        const val TIMEOUT_SECONDS = 30L
    }
}
