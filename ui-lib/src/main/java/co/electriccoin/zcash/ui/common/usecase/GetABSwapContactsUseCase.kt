package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import kotlinx.coroutines.flow.map

class GetABSwapContactsUseCase(
    private val addressBookRepository: AddressBookRepository,
    simpleSwapAssetProvider: SimpleSwapAssetProvider,
) {
    private val curatedChainTickers =
        simpleSwapAssetProvider
            .getCuratedSwapAssets()
            .map { it.chainTicker.lowercase() }
            .toSet()

    fun observe() =
        addressBookRepository.contacts
            .map { contacts ->
                contacts?.filter { contact ->
                    contact.blockchain?.chainTicker?.lowercase() in curatedChainTickers
                }
            }
}
