package co.electriccoin.zcash.ui.screen.contact

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.ContactAddressValidationResult
import co.electriccoin.zcash.ui.common.usecase.SaveABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateContactNameResult
import co.electriccoin.zcash.ui.common.usecase.ValidateGenericABContactNameUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateZashiABContactAddressUseCase
import co.electriccoin.zcash.ui.common.zns.LookUpZnsRecipientUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddZashiABContactVM(
    args: AddZashiABContactArgs,
    private val validateContactAddress: ValidateZashiABContactAddressUseCase,
    private val validateContactName: ValidateGenericABContactNameUseCase,
    private val saveContact: SaveABContactUseCase,
    private val navigationRouter: NavigationRouter,
    lookUpZnsRecipient: LookUpZnsRecipientUseCase,
) : ViewModel() {
    private val contactAddress = MutableStateFlow(args.address.orEmpty())
    private val contactName = MutableStateFlow(args.name.orEmpty())
    private val isSavingContact = MutableStateFlow(false)

    private val refusedGiftCardLink = RefusedGiftCardLinkAddress()

    private val znsLookup = ContactZnsLookup(lookUpZnsRecipient, contactAddress, viewModelScope)

    init {
        // A contact saved from a name is labelled with that name, unless the user already typed a label.
        viewModelScope.launch {
            znsLookup.state.collect { zns ->
                if (zns is ContactZnsState.Resolved && contactName.value.isEmpty()) {
                    contactName.update { zns.name.displayName }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val contactAddressError =
        combine(contactAddress, znsLookup.state) { address, zns -> address to zns }
            .mapLatest { (typed, zns) ->
                val address = (zns as? ContactZnsState.Resolved)?.address ?: typed
                if (address.isEmpty()) {
                    null
                } else if (zns is ContactZnsState.Failed) {
                    zns.message
                } else if (zns is ContactZnsState.LookingUp) {
                    null
                } else {
                    when (validateContactAddress(address)) {
                        ContactAddressValidationResult.Invalid -> {
                            stringRes(R.string.send_error_invalidAddress)
                        }

                        ContactAddressValidationResult.NotUnique -> {
                            stringRes(R.string.contact_address_error_not_unique)
                        }

                        ContactAddressValidationResult.GiftCardLink -> {
                            stringRes(R.string.contact_error_giftCardLink)
                        }

                        ContactAddressValidationResult.Valid -> {
                            null
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private val contactAddressState =
        refusedGiftCardLink.withAddressError(contactAddress, contactAddressError) { address, contactAddressError ->
            TextFieldState(
                value = stringRes(address),
                error = contactAddressError,
                onValueChange = { newValue ->
                    contactAddress.update { newValue }
                }
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val contactNameError =
        contactName
            .mapLatest { name ->
                if (name.isEmpty()) {
                    null
                } else {
                    when (validateContactName(name)) {
                        ValidateContactNameResult.TooLong -> {
                            stringRes(R.string.addressBook_error_nameLength)
                        }

                        ValidateContactNameResult.NotUnique -> {
                            stringRes(R.string.addressBook_error_nameExists)
                        }

                        ValidateContactNameResult.Valid -> {
                            null
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private val contactNameState =
        combine(contactName, contactNameError) { name, contactNameError ->
            TextFieldState(
                value = stringRes(name),
                error = contactNameError,
                onValueChange = { newValue ->
                    contactName.update { newValue }
                }
            )
        }

    private val saveButtonState =
        combine(
            contactAddressState,
            contactNameState,
            isSavingContact,
            znsLookup.state
        ) { address, name, isSavingContact, zns ->
            ButtonState(
                text = stringRes(R.string.general_save),
                isEnabled =
                    address.error == null &&
                        zns !is ContactZnsState.LookingUp &&
                        name.error == null &&
                        contactAddress.value.isNotEmpty() &&
                        contactName.value.isNotEmpty(),
                onClick = ::onSaveButtonClick,
                isLoading = isSavingContact,
                hapticFeedbackType = HapticFeedbackType.Confirm
            )
        }

    val state =
        combine(
            contactAddressState,
            contactNameState,
            saveButtonState,
            znsLookup.state
        ) { address, name, saveButton, zns ->
            ABContactState(
                info = null,
                title = stringRes(R.string.addressBook_addNewContact),
                walletAddress = address,
                walletAddressStatus = zns.fieldStatus,
                contactName = name,
                chain = null,
                negativeButton = null,
                positiveButton = saveButton,
                onBack = ::onBack
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun onBack() = navigationRouter.back()

    private fun onSaveButtonClick() =
        viewModelScope.launch {
            if (isSavingContact.value) return@launch
            // A contact saved from a name stores the address the name resolved to, and nothing until it resolved.
            val addressToSave = znsLookup.addressToSave() ?: return@launch
            isSavingContact.update { true }
            val address = contactAddress.value
            val result =
                saveContact(
                    name = contactName.value,
                    address = addressToSave,
                    chain = null
                )
            refusedGiftCardLink.onSaveResult(address, result)
            isSavingContact.update { false }
        }
}
