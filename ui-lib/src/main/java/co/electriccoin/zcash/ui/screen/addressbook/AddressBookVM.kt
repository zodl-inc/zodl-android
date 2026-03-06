package co.electriccoin.zcash.ui.screen.addressbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.usecase.GetABContactsUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.listitem.ContactListItemState
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.screen.contact.AddGenericABContactArgs
import co.electriccoin.zcash.ui.screen.contact.UpdateGenericABContactArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddressBookVM(
    getAddressBookContacts: GetABContactsUseCase,
    private val navigationRouter: NavigationRouter,
    private val navigateToScanGenericAddressUseCase: NavigateToScanGenericAddressUseCase
) : ViewModel() {
    val state =
        getAddressBookContacts
            .observe(zcashContactsOnly = false)
            .map { contacts -> createState(contacts = contacts) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(contacts = null)
            )

    private fun createState(contacts: List<EnhancedABContact>?) =
        AddressBookState(
            isLoading = contacts == null,
            items =
                contacts
                    ?.map { contact ->
                        AddressBookItem.Contact(
                            ContactListItemState(
                                bigIcon = getContactInitials(contact),
                                smallIcon = contact.blockchain?.chainIcon,
                                isShielded = false,
                                name = stringRes(contact.name),
                                address = stringResByAddress(value = contact.address),
                                onClick = { onContactClick(contact) },
                            )
                        )
                    }.orEmpty(),
            onBack = ::onBack,
            manualButton =
                ButtonState(
                    onClick = ::onAddContactManuallyClick,
                    text = stringRes(R.string.address_book_manual_btn)
                ),
            scanButton =
                ButtonState(
                    onClick = ::onScanContactClick,
                    text = stringRes(R.string.address_book_scan_btn)
                ),
            title = stringRes(R.string.address_book_title),
            info = null
        )

    private fun getContactInitials(contact: EnhancedABContact) =
        imageRes(
            contact.name
                .split(" ")
                .mapNotNull { part ->
                    part.takeIf { it.isNotEmpty() }?.first()?.toString()
                }.take(2)
                .joinToString(separator = "")
        )

    private fun onBack() = navigationRouter.back()

    private fun onContactClick(contact: EnhancedABContact) {
        navigationRouter.forward(
            UpdateGenericABContactArgs(
                address = contact.address,
                chain = contact.blockchain?.chainTicker
            )
        )
    }

    private fun onAddContactManuallyClick() = navigationRouter.forward(AddGenericABContactArgs(null))

    private fun onScanContactClick() =
        viewModelScope.launch {
            val contact = navigateToScanGenericAddressUseCase()
            if (contact != null) {
                navigationRouter.replace(AddGenericABContactArgs(address = contact.address))
            }
        }
}

internal const val ADDRESS_MAX_LENGTH = 20
