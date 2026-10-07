package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretDetector
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class ValidateZashiABContactAddressUseCase(
    private val addressBookRepository: AddressBookRepository,
    private val synchronizerProvider: SynchronizerProvider
) {
    suspend operator fun invoke(
        address: String,
        exclude: EnhancedABContact? = null
    ): ContactAddressValidationResult {
        if (GiftCardSecretDetector.mayContainGiftCardSecret(address)) return ContactAddressValidationResult.GiftCardLink
        val result = synchronizerProvider.getSynchronizer().validateAddress(address)
        return when {
            result.isNotValid -> ContactAddressValidationResult.Invalid

            addressBookRepository.contacts
                .filterNotNull()
                .first()
                .filter { it.blockchain == null }
                .filter {
                    if (exclude == null) true else it != exclude
                }.any { it.address == address.trim() } -> ContactAddressValidationResult.NotUnique

            else -> ContactAddressValidationResult.Valid
        }
    }
}

sealed interface ContactAddressValidationResult {
    data object Valid : ContactAddressValidationResult

    data object Invalid : ContactAddressValidationResult

    data object NotUnique : ContactAddressValidationResult

    /**
     * The address is, or may contain, a gift card link or key. Such a link carries the card's spending key, so it must
     * never be saved as a contact: from there it would be sent to a swap provider, or leave the device with the
     * address book's backup.
     */
    data object GiftCardLink : ContactAddressValidationResult
}
