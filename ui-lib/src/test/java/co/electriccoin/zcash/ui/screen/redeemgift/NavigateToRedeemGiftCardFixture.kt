package co.electriccoin.zcash.ui.screen.redeemgift

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.common.repository.GiftCardRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.fixture.FakeGiftCardDataSource

/**
 * The real use case over [store] and [router]. Its repository only ever checks the shape of links here, so no session
 * is started.
 */
internal fun navigateToRedeemGiftCard(
    store: GiftCardLinkStore,
    router: NavigationRouter
) = NavigateToRedeemGiftCardUseCase(
    giftCardRepository = GiftCardRepositoryImpl(FakeGiftCardDataSource(), store),
    giftCardLinkStore = store,
    navigationRouter = router
)
