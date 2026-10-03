package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.common.repository.GiftCardRepository
import co.electriccoin.zcash.ui.screen.redeemgift.RedeemGiftArgs

/**
 * Routes gift card links into the redeem flow. The link itself is stashed in [GiftCardLinkStore] and only its id
 * travels in the navigation route.
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
     * Opens the redeem screen for the link that arrived through an app link, if one is waiting.
     */
    fun openPendingAppLink() {
        val id = giftCardLinkStore.consumePendingAppLinkId() ?: return
        navigationRouter.forward(RedeemGiftArgs(id))
    }
}
