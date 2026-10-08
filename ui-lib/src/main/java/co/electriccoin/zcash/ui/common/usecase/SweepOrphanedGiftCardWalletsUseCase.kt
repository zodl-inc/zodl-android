package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.GiftCardRepository

/**
 * Erases the temporary gift card wallets that a redemption left on the device without erasing them, e.g. because the
 * process died mid-redemption. Launched once from the application class at startup, independently of any screen.
 */
class SweepOrphanedGiftCardWalletsUseCase(
    private val giftCardRepository: GiftCardRepository,
) {
    suspend operator fun invoke() = giftCardRepository.sweepOrphanedCardWallets()
}
