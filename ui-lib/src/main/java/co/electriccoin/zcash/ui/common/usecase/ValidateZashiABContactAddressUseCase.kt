package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
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
     * The address is a gift card link. Such a link carries the card's spending key, so it must never be saved as a
     * swap contact, from where it would be sent to the swap provider.
     */
    data object GiftCardLink : ContactAddressValidationResult
}
