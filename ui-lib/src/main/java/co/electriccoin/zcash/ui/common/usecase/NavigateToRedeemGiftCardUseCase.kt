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
     * Whether [value] could still become a gift card link as the user keeps typing, see
     * [GiftCardRepository.isGiftCardLinkStart].
     */
    fun isGiftCardLinkStart(value: String): Boolean = giftCardRepository.isGiftCardLinkStart(value)

    /**
     * Replaces the current screen (an address scanner that was handed a gift card link) with the redeem screen for
     * [link].
     */
    fun replaceWithRedeem(link: String) = navigationRouter.replace(redeemArgs(link))

    /**
     * Replaces the gift card flow's own screens, the gift card scanner and the paste link screen above it, with the
     * redeem screen for [link], so back from the redeem screen returns to the screen the scanner was opened from.
     */
    fun replaceGiftCardScanWithRedeem(link: String) =
        navigationRouter.replaceFrom(ScanGiftCardArgs::class, redeemArgs(link))

    private fun redeemArgs(link: String) = RedeemGiftArgs(giftCardLinkStore.stash(link))

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
