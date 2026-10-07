package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.screen.scan.thirdparty.ThirdPartyScan

/**
 * Routes links that reach the app from outside (VIEW intents, app links). Their content is never used: another app
 * could have altered it on its way in. As with ZIP 321 `zcash:` URIs, which go to [ThirdPartyScan] and from there to
 * the app's own scanner, the user is asked to scan the code again with the app.
 *
 * A gift card link opens the gift card scanner once the wallet is ready, see
 * [NavigateToRedeemGiftCardUseCase.openRequestedInAppScan]. Only the link's host is looked at to tell the two apart.
 */
class HandleExternalLinkUseCase(
    private val giftCardLinkStore: GiftCardLinkStore,
    private val navigationRouter: NavigationRouter,
) {
    /**
     * @param host the link's host, which is all that is needed to route it.
     * @param isRedelivery whether the intent is one that was already handled (a recreated activity, a relaunch from
     * recents).
     * @return whether the link was a gift card link, which the caller must then drop from the intent.
     */
    operator fun invoke(
        host: String?,
        isRedelivery: Boolean
    ): Boolean =
        if (host.equals(GIFT_CARD_HOST, ignoreCase = true)) {
            if (!isRedelivery) giftCardLinkStore.requestInAppScan()
            true
        } else {
            navigationRouter.forward(ThirdPartyScan)
            false
        }

    companion object {
        /** Must match the app link intent filter in the app module's manifest. */
        const val GIFT_CARD_HOST = "gift.zodl.com"
    }
}
