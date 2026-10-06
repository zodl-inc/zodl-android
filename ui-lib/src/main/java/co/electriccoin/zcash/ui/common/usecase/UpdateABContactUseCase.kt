package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.requireNoGiftCardSecret

/**
 * Updates a contact and goes back.
 *
 * Refuses, independently of the screen's validation, an address that is or may contain a gift card link or key: it
 * throws [co.electriccoin.zcash.ui.common.repository.GiftCardContactNotAllowedException] before anything is saved or
 * any navigation happens.
 */
class UpdateABContactUseCase(
    private val addressBookRepository: AddressBookRepository,
    private val navigationRouter: NavigationRouter
) {
    operator fun invoke(
        contact: EnhancedABContact,
        name: String,
        address: String,
        chain: String?,
    ) {
        requireNoGiftCardSecret(address)
        addressBookRepository.updateContact(contact = contact, name = name, address = address, chain = chain)
        navigationRouter.back()
    }
}
