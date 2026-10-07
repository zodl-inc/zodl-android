package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretDetector
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class ValidateSwapABContactAddressUseCase(
    private val addressBookRepository: AddressBookRepository,
) {
    suspend operator fun invoke(
        address: String,
        blockchain: SwapBlockchain?,
        exclude: EnhancedABContact? = null
    ): ContactAddressValidationResult =
        when {
            GiftCardSecretDetector.mayContainGiftCardSecret(address) -> ContactAddressValidationResult.GiftCardLink

            addressBookRepository.contacts
                .filterNotNull()
                .first()
                .filter { it.blockchain != null }
                .filter {
                    if (exclude == null) true else it != exclude
                }.any {
                    it.address == address.trim() &&
                        it.blockchain?.chainTicker?.lowercase() == blockchain?.chainTicker?.lowercase()
                } -> ContactAddressValidationResult.NotUnique

            else -> ContactAddressValidationResult.Valid
        }
}
