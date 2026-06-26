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
) : ViewModel() {
    private val contactAddress = MutableStateFlow(args.address.orEmpty())
    private val contactName = MutableStateFlow("")
    private val isSavingContact = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val contactAddressError =
        contactAddress
            .mapLatest { address ->
                if (address.isEmpty()) {
                    null
                } else {
                    when (validateContactAddress(address)) {
                        ContactAddressValidationResult.Invalid -> {
                            stringRes(R.string.send_error_invalidAddress)
                        }

                        ContactAddressValidationResult.NotUnique -> {
                            stringRes(R.string.contact_address_error_not_unique)
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
        combine(contactAddress, contactAddressError) { address, contactAddressError ->
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
        combine(contactAddressState, contactNameState, isSavingContact) { address, name, isSavingContact ->
            ButtonState(
                text = stringRes(R.string.general_save),
                isEnabled =
                    address.error == null &&
                        name.error == null &&
                        contactAddress.value.isNotEmpty() &&
                        contactName.value.isNotEmpty(),
                onClick = ::onSaveButtonClick,
                isLoading = isSavingContact,
                hapticFeedbackType = HapticFeedbackType.Confirm
            )
        }

    val state =
        combine(contactAddressState, contactNameState, saveButtonState) { address, name, saveButton ->
            ABContactState(
                info = null,
                title = stringRes(R.string.addressBook_addNewContact),
                walletAddress = address,
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
            isSavingContact.update { true }
            saveContact(
                name = contactName.value,
                address = contactAddress.value,
                chain = null
            )
            isSavingContact.update { false }
        }
}
