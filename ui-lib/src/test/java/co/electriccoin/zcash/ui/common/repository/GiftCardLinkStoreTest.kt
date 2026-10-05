package co.electriccoin.zcash.ui.common.repository

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
    }
}
