package co.electriccoin.zcash.ui.screen.contact

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.ABContactSaveResult
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * The address a contact screen's save was last refused for as a gift card link
 * ([ABContactSaveResult.GiftCardLinkRefused]), shown with that error until it is edited: the screen's own validation
 * of a pasted link can still be running when Save is tapped.
 */
class RefusedGiftCardLinkAddress {
    private val refused = MutableStateFlow<String?>(null)

    /**
     * Combines [address] with its [addressError] into [transform], the error replaced by the gift link error while
     * [address] is the refused one.
     */
    fun <T> withAddressError(
        address: Flow<String>,
        addressError: Flow<StringResource?>,
        transform: (address: String, error: StringResource?) -> T
    ): Flow<T> =
        combine(address, addressError, refused) { current, error, refusedAddress ->
            transform(current, if (current == refusedAddress) stringRes(R.string.contact_error_giftCardLink) else error)
        }

    /** Remembers [address] as refused when the save [result] says so. */
    fun onSaveResult(
        address: String,
        result: ABContactSaveResult
    ) {
        if (result == ABContactSaveResult.GiftCardLinkRefused) refused.update { address }
    }
}
