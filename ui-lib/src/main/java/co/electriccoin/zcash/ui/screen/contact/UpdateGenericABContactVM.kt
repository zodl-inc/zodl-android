package co.electriccoin.zcash.ui.screen.contact

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.usecase.ContactAddressValidationResult
import co.electriccoin.zcash.ui.common.usecase.DeleteABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.GetABContactByIdUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectSwapBlockchainUseCase
import co.electriccoin.zcash.ui.common.usecase.UpdateABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateContactNameResult
import co.electriccoin.zcash.ui.common.usecase.ValidateGenericABContactNameUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateSwapABContactAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateZashiABContactAddressUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.PickerState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.combine
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.error.ErrorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateGenericABContactVM(
    blockchainProvider: BlockchainProvider,
    private val args: UpdateGenericABContactArgs,
    private val validateGenericABContactName: ValidateGenericABContactNameUseCase,
    private val updateContact: UpdateABContactUseCase,
    private val deleteContact: DeleteABContactUseCase,
    private val getContactByAddress: GetABContactByIdUseCase,
    private val navigationRouter: NavigationRouter,
    private val navigateToSelectSwapBlockchain: NavigateToSelectSwapBlockchainUseCase,
    private val validateZashiABContactAddress: ValidateZashiABContactAddressUseCase,
    private val validateSwapABContactAddress: ValidateSwapABContactAddressUseCase,
) : ViewModel() {
    private val zcashBlockchain = blockchainProvider.getZcashBlockchain()

    private val originalContact = MutableStateFlow<EnhancedABContact?>(null)
    private val contactAddress = MutableStateFlow("")
    private val contactName = MutableStateFlow("")
    private val selectedBlockchain = MutableStateFlow<SwapBlockchain?>(null)

    private val isUpdatingContact = MutableStateFlow(false)
    private val isDeletingContact = MutableStateFlow(false)
    private val isLoadingContact = MutableStateFlow(true)

    private val _dialogState = MutableStateFlow<ErrorState?>(null)
    val dialogState = _dialogState.asStateFlow()

    private val blockChainPickerState =
        selectedBlockchain
            .map {
                PickerState(
                    bigIcon = it?.chainIcon,
                    smallIcon = null,
                    text = it?.chainName,
                    isEnabled = it != zcashBlockchain,
                    placeholder = stringRes(co.electriccoin.zcash.ui.design.R.string.general_select),
                    onClick = ::onBlockchainClick
                )
            }

    private val addressZashiValidation =
        combine(contactAddress, originalContact) { address, contact ->
            if (address.isEmpty()) null else validateZashiABContactAddress(address, contact)
        }

    private val addressSwapValidation =
        combine(
            contactAddress,
            selectedBlockchain,
            originalContact
        ) { address, blockchain, contact ->
            if (address.isEmpty()) null else validateSwapABContactAddress(address, blockchain, contact)
        }

    private val addressValidation =
        combine(
            addressZashiValidation,
            addressSwapValidation,
            selectedBlockchain
        ) {
            zashiValidation,
            swapValidation,
            blockchain
            ->
            val validation =
                if (blockchain == null || blockchain == zcashBlockchain) {
                    zashiValidation
                } else {
                    swapValidation
                }
            when (validation) {
                ContactAddressValidationResult.Invalid -> stringRes(R.string.contact_address_error_invalid)
                ContactAddressValidationResult.NotUnique ->
                    if (blockchain == null || blockchain == zcashBlockchain) {
                        stringRes(R.string.contact_address_error_not_unique)
                    } else {
                        stringRes(R.string.contact_chain_address_error_not_unique)
                    }
                ContactAddressValidationResult.Valid -> null
                null -> null
            }
        }

    private val addressState =
        combine(contactAddress, addressValidation) { address, contactAddressError ->
            TextFieldState(
                value = stringRes(address),
                error = contactAddressError,
                onValueChange = ::onAddressChange
            )
        }

    private val nameError =
        combine(contactName, originalContact) { name, contact ->
            if (name.isEmpty() || contact == null) {
                null
            } else {
                when (validateGenericABContactName(name = name, exclude = contact)) {
                    ValidateContactNameResult.TooLong ->
                        stringRes(R.string.contact_name_error_too_long)

                    ValidateContactNameResult.NotUnique ->
                        stringRes(R.string.contact_name_error_not_unique)

                    ValidateContactNameResult.Valid -> null
                }
            }
        }

    private val nameState =
        combine(contactName, nameError) { name, contactNameError ->
            TextFieldState(
                value = stringRes(name),
                error = contactNameError,
                onValueChange = { newValue ->
                    contactName.update { newValue }
                }
            )
        }

    private val updateButtonState =
        combine(
            addressState,
            nameState,
            isUpdatingContact,
            originalContact,
            selectedBlockchain
        ) {
            address,
            name,
            isUpdatingContact,
            contact,
            blockchain
            ->
            val nameChanged = contactName.value.trim() != contact?.name
            val addressChanged = contactAddress.value.trim() != contact?.address
            val blockchainChanged =
                if (contact?.blockchain == null) {
                    blockchain != zcashBlockchain
                } else {
                    blockchain != contact.blockchain
                }
            ButtonState(
                text = stringRes(R.string.update_contact_primary_btn),
                isEnabled =
                    address.error == null &&
                        name.error == null &&
                        contactAddress.value.isNotEmpty() &&
                        contactName.value.isNotEmpty() &&
                        (nameChanged || addressChanged || blockchainChanged),
                onClick = ::onUpdateButtonClick,
                isLoading = isUpdatingContact,
                hapticFeedbackType = HapticFeedbackType.Confirm
            )
        }

    private val deleteButtonState =
        isDeletingContact.map { isDeletingContact ->
            ButtonState(
                text = stringRes(R.string.update_contact_secondary_btn),
                onClick = ::onDeleteButtonClick,
                isLoading = isDeletingContact,
                hapticFeedbackType = HapticFeedbackType.Confirm
            )
        }

    val state =
        combine(
            addressState,
            nameState,
            updateButtonState,
            deleteButtonState,
            isLoadingContact,
            blockChainPickerState,
        ) { address, name, saveButton, deleteButton, isLoadingContact, blockchainPicker ->
            ABContactState(
                info = null,
                title = stringRes(R.string.update_contact_title),
                walletAddress = address,
                contactName = name,
                chain = blockchainPicker,
                negativeButton = deleteButton,
                positiveButton = saveButton,
                onBack = ::onBack
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            val contact = getContactByAddress(address = args.address, chain = args.chain)
            contactAddress.update { contact?.address.orEmpty() }
            contactName.update { contact?.name.orEmpty() }
            originalContact.update { contact }
            selectedBlockchain.update { contact?.blockchain ?: zcashBlockchain }
            isLoadingContact.update { false }
        }
    }

    private fun onAddressChange(newValue: String) =
        viewModelScope.launch {
            contactAddress.update { newValue.trim() }

            val validation = validateZashiABContactAddress(newValue, originalContact.value)
            if (validation == ContactAddressValidationResult.Valid ||
                validation == ContactAddressValidationResult.NotUnique
            ) {
                selectedBlockchain.update { zcashBlockchain }
            } else if (selectedBlockchain.value == zcashBlockchain) {
                selectedBlockchain.update { null }
            }
        }

    private fun onBack() = navigationRouter.back()

    private fun onUpdateButtonClick() =
        viewModelScope.launch {
            val selectedBlockchain = selectedBlockchain.value
            if (isDeletingContact.value || isUpdatingContact.value || selectedBlockchain == null) return@launch
            originalContact.value?.let { original ->
                isUpdatingContact.update { true }
                updateContact(
                    contact = original,
                    name = contactName.value,
                    address = contactAddress.value,
                    chain = selectedBlockchain.takeIf { it != zcashBlockchain }?.chainTicker
                )
                isUpdatingContact.update { false }
            }
        }

    private fun onDeleteButtonClick() {
        if (_dialogState.value != null) {
            _dialogState.value = null
        } else {
            _dialogState.value =
                ErrorState(
                    title = stringRes(R.string.delete_contact_confirmation_title),
                    message = stringRes(R.string.delete_contact_confirmation_message),
                    positive =
                        ButtonState(
                            text = stringRes(R.string.delete_contact_confirmation_confirm),
                            onClick = ::onDeleteConfirmed
                        ),
                    negative =
                        ButtonState(
                            text = stringRes(R.string.delete_contact_confirmation_cancel),
                            onClick = ::onDeleteCancelled
                        ),
                    onBack = { _dialogState.value = null }
                )
        }
    }

    private fun onDeleteConfirmed() =
        viewModelScope.launch {
            _dialogState.value = null
            if (isDeletingContact.value || isUpdatingContact.value) return@launch
            originalContact.value?.let {
                isDeletingContact.update { true }
                deleteContact(it)
                isDeletingContact.update { false }
            }
        }

    private fun onDeleteCancelled() {
        _dialogState.value = null
    }

    private fun onBlockchainClick() =
        viewModelScope.launch {
            val result = navigateToSelectSwapBlockchain()
            if (result != null) {
                selectedBlockchain.update { result }
            }
        }
}
