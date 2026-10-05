package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.common.repository.GiftCardRepository
import co.electriccoin.zcash.ui.screen.redeemgift.RedeemGiftArgs
import co.electriccoin.zcash.ui.screen.redeemgift.ScanGiftCardArgs

/**
 * Routes gift card links scanned, picked from an image or pasted inside the app into the redeem flow. The link itself
 * is stashed in [GiftCardLinkStore] and only its id travels in the navigation route.
 */
class NavigateToRedeemGiftCardUseCase(
    private val giftCardRepository: GiftCardRepository,
    private val giftCardLinkStore: GiftCardLinkStore,
    private val navigationRouter: NavigationRouter,
) {
    fun isGiftCardLink(value: String): Boolean = giftCardRepository.isGiftCardLink(value)

    /**
     * Replaces the current screen (a scanner) with the redeem screen for [link].
     */
    fun replaceWithRedeem(link: String) = navigationRouter.replace(RedeemGiftArgs(giftCardLinkStore.stash(link)))

    /**
     * Opens the gift card scanner, asking the user to scan the card in the app, if a gift card link arrived from
     * outside the app. That link itself is never redeemed, see [HandleExternalLinkUseCase].
     */
    fun openRequestedInAppScan() {
        if (giftCardLinkStore.consumeInAppScanRequest()) {
            navigationRouter.forward(ScanGiftCardArgs(isFromExternalLink = true))
        }
    }
}
