package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretDetector

/**
 * Saves a new contact and goes back.
 *
 * Refuses, independently of the screen's validation, an address that is or may contain a gift card link or key: it
 * then returns [ABContactSaveResult.GiftCardLinkRefused] without saving anything or navigating, so that a screen whose
 * validation has not caught up with a pasted link yet can show why.
 */
class SaveABContactUseCase(
    private val addressBookRepository: AddressBookRepository,
    private val navigationRouter: NavigationRouter,
) {
    operator fun invoke(
        name: String,
        address: String,
        chain: String?,
    ): ABContactSaveResult {
        if (GiftCardSecretDetector.mayContainGiftCardSecret(address)) return ABContactSaveResult.GiftCardLinkRefused
        addressBookRepository.saveContact(name = name, address = address, chain = chain)
        navigationRouter.back()
        return ABContactSaveResult.Saved
    }
}

/** What [SaveABContactUseCase] or [UpdateABContactUseCase] did with a contact. */
sealed interface ABContactSaveResult {
    /** The contact was saved, and the screen was left. */
    data object Saved : ABContactSaveResult

    /**
     * The address is, or may contain, a gift card link or key, which carries the card's spending key: nothing was
     * saved, and the screen stays.
     */
    data object GiftCardLinkRefused : ABContactSaveResult
}
