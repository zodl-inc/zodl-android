package co.electriccoin.zcash.ui.screen.redeemgift

import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.repository.GiftCardRepository
import co.electriccoin.zcash.ui.common.usecase.HandleExternalLinkUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.screen.scan.thirdparty.ThirdPartyScan
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gift card links that come from outside the app are never parsed nor redeemed: like ZIP 321 `zcash:` URIs, they only
 * make the app ask the user to scan the card with the app itself. What the scanner then does with an in-app scan is
 * covered by [ScanGiftCardVMTest].
 */
class ExternalGiftCardLinkTest {
    @Test
    fun externalGiftLinkOpensTheGiftCardScannerWithoutKeepingTheLink() {
        val store = spyk(GiftCardLinkStoreImpl())
        val giftCardRepository = mockk<GiftCardRepository>()
        val router = RecordingNavigationRouter()
        val handleExternalLink = HandleExternalLinkUseCase(store, router)
        val navigateToRedeemGiftCard = NavigateToRedeemGiftCardUseCase(giftCardRepository, store, router)

        assertTrue(handleExternalLink(host = "gift.zodl.com", isRedelivery = false))
        // Nothing is navigated to before the wallet is ready.
        assertTrue(router.forwardedRoutes.isEmpty())
        assertTrue(store.isInAppScanRequested.value)

        navigateToRedeemGiftCard.openRequestedInAppScan()

        assertEquals(listOf<Any>(ScanGiftCardArgs(isFromExternalLink = true)), router.forwardedRoutes)
        assertTrue(router.replacedRoutes.isEmpty())
        assertFalse(store.isInAppScanRequested.value)
        // The link was never stashed, parsed nor checked.
        verify(exactly = 0) { store.stash(any()) }
        verify(exactly = 0) { giftCardRepository.isGiftCardLink(any()) }
        confirmVerified(giftCardRepository)
    }

    @Test
    fun hostIsMatchedIgnoringCase() {
        val store = GiftCardLinkStoreImpl()

        assertTrue(HandleExternalLinkUseCase(store, RecordingNavigationRouter())("GIFT.ZODL.COM", isRedelivery = false))
        assertTrue(store.isInAppScanRequested.value)
    }

    @Test
    fun redeliveredGiftLinkDoesNotAskAgain() {
        val store = GiftCardLinkStoreImpl()
        val router = RecordingNavigationRouter()

        assertTrue(HandleExternalLinkUseCase(store, router)(host = "gift.zodl.com", isRedelivery = true))

        assertFalse(store.isInAppScanRequested.value)
        assertTrue(router.forwardedRoutes.isEmpty())
    }

    @Test
    fun zcashUriStillGoesToTheThirdPartyScanScreen() {
        val store = mockk<GiftCardLinkStore>()
        val router = RecordingNavigationRouter()

        assertFalse(HandleExternalLinkUseCase(store, router)(host = null, isRedelivery = false))

        assertEquals(listOf<Any>(ThirdPartyScan), router.forwardedRoutes)
        confirmVerified(store)
    }

    @Test
    fun otherHostsAreNotTreatedAsGiftLinks() {
        val store = GiftCardLinkStoreImpl()
        val router = RecordingNavigationRouter()

        assertFalse(HandleExternalLinkUseCase(store, router)("gift.zodl.com.evil.example", isRedelivery = false))

        assertFalse(store.isInAppScanRequested.value)
        assertEquals(listOf<Any>(ThirdPartyScan), router.forwardedRoutes)
    }

    @Test
    fun noScannerIsOpenedWithoutARequest() {
        val router = RecordingNavigationRouter()

        NavigateToRedeemGiftCardUseCase(mockk(), GiftCardLinkStoreImpl(), router).openRequestedInAppScan()

        assertTrue(router.forwardedRoutes.isEmpty())
    }
}
