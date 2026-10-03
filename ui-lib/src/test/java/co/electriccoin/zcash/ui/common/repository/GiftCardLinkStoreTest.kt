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
    fun pendingAppLinkIsConsumedOnce() {
        val store = GiftCardLinkStoreImpl()
        store.setPendingAppLink(LINK)

        val id = requireNotNull(store.consumePendingAppLinkId())
        assertNull(store.consumePendingAppLinkId())
        assertNull(store.pendingAppLinkId.value)
        assertEquals(LINK, store.take(id))
    }

    @Test
    fun newerPendingAppLinkReplacesAndForgetsTheOlderOne() {
        val store = GiftCardLinkStoreImpl()
        store.setPendingAppLink(LINK)
        val olderId = requireNotNull(store.pendingAppLinkId.value)

        store.setPendingAppLink(OTHER_LINK)

        assertNull(store.take(olderId))
        assertEquals(OTHER_LINK, store.take(requireNotNull(store.consumePendingAppLinkId())))
    }

    @Test
    fun prefixCheckAcceptsOnlyGiftCardLinks() {
        assertTrue(GiftCardLinkPrefixes.matches(LINK))
        assertTrue(GiftCardLinkPrefixes.matches("  $LINK "))
        assertTrue(GiftCardLinkPrefixes.matches("https://link.vizor.cash/payment-links/open#v1=abc"))
        assertFalse(GiftCardLinkPrefixes.matches("https://gift.zodl.com/about"))
        assertFalse(GiftCardLinkPrefixes.matches("https://gift.zodl.com.evil.example/#v=1"))
        assertFalse(GiftCardLinkPrefixes.matches("zcash:u1address"))
        assertFalse(GiftCardLinkPrefixes.matches(""))
    }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val OTHER_LINK = "https://gift.zodl.com/#v=1&key=zgift1other&height=3100000"
    }
}
