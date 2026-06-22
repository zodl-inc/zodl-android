package co.electriccoin.zcash.ui.screen.contact

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.usecase.ContactAddressValidationResult
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectSwapBlockchainUseCase
import co.electriccoin.zcash.ui.common.usecase.SaveABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateContactNameResult
import co.electriccoin.zcash.ui.common.usecase.ValidateGenericABContactNameUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateSwapABContactAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateZashiABContactAddressUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.PickerState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddGenericABContactVM(
    args: AddGenericABContactArgs,
    blockchainProvider: BlockchainProvider,
    private val validateZashiABContactAddress: ValidateZashiABContactAddressUseCase,
    private val validateSwapABContactAddress: ValidateSwapABContactAddressUseCase,
    private val validateGenericABContactName: ValidateGenericABContactNameUseCase,
    private val saveABContact: SaveABContactUseCase,
    private val navigationRouter: NavigationRouter,
    private val navigateToSelectSwapBlockchain: NavigateToSelectSwapBlockchainUseCase,
) : ViewModel() {
    private val zcashBlockchain = blockchainProvider.getZcashBlockchain()

    private val contactAddress = MutableStateFlow(args.address.orEmpty())
    private val contactName = MutableStateFlow("")
    private val selectedBlockchain = MutableStateFlow<SwapBlockchain?>(null)
    private val isSavingContact = MutableStateFlow(false)

    private val addressZashiValidation =
        contactAddress
            .map {
                if (it.isEmpty()) null else validateZashiABContactAddress(it)
            }.onEach {
                if (it == ContactAddressValidationResult.Valid || it == ContactAddressValidationResult.NotUnique) {
                    selectedBlockchain.update { zcashBlockchain }
                } else if (selectedBlockchain.value == zcashBlockchain) {
                    selectedBlockchain.update { null }
                }
            }

    private val addressSwapValidation =
        combine(contactAddress, selectedBlockchain) { address, blockchain ->
            if (address.isEmpty()) null else validateSwapABContactAddress(address, blockchain)
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
                when (blockchain) {
                    null -> null
                    zcashBlockchain -> zashiValidation
                    else -> swapValidation
                }

            when (validation) {
                ContactAddressValidationResult.Invalid -> {
                    stringRes(R.string.contact_address_error_invalid)
                }

                ContactAddressValidationResult.NotUnique -> {
                    if (blockchain == null || blockchain == zcashBlockchain) {
                        stringRes(R.string.contact_address_error_not_unique)
                    } else {
                        stringRes(R.string.contact_chain_address_error_not_unique)
                    }
                }

                ContactAddressValidationResult.Valid -> {
                    null
                }

                null -> {
                    null
                }
            }
        }

    private val blockChainPickerState =
        selectedBlockchain
            .map {
                PickerState(
                    bigIcon = it?.chainIcon,
                    smallIcon = null,
                    text = it?.chainName,
                    placeholder = stringRes(co.electriccoin.zcash.ui.design.R.string.general_select),
                    isEnabled = it != zcashBlockchain,
                    onClick = ::onBlockchainClick
                )
            }

    private val addressState =
        combine(contactAddress, addressValidation) { address, contactAddressError ->
            TextFieldState(
                value = stringRes(address),
                error = contactAddressError,
                onValueChange = ::onAddressChange
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val nameError =
        contactName
            .mapLatest { name ->
                if (name.isEmpty()) {
                    null
                } else {
                    when (validateGenericABContactName(name)) {
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

    private val saveButtonState =
        combine(
            addressState,
            nameState,
            isSavingContact,
        ) { address, name, isSavingContact ->
            ButtonState(
                text = stringRes(R.string.add_new_contact_primary_btn),
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
        combine(
            addressState,
            nameState,
            saveButtonState,
            blockChainPickerState,
        ) { address, name, saveButton, picker ->
            ABContactState(
                info = null,
                title = stringRes(R.string.add_new_contact_title),
                walletAddress = address,
                contactName = name,
                chain = picker,
                negativeButton = null,
                positiveButton = saveButton,
                onBack = ::onBack
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun onBlockchainClick() =
        viewModelScope.launch {
            val result = navigateToSelectSwapBlockchain()
            if (result != null) {
                selectedBlockchain.update { result }
            }
        }

    private fun onBack() = navigationRouter.back()

    private fun onAddressChange(newValue: String) = contactAddress.update { newValue.trim() }

    private fun onSaveButtonClick() =
        viewModelScope.launch {
            if (isSavingContact.value) return@launch
            isSavingContact.update { true }
            saveABContact(
                name = contactName.value,
                address = contactAddress.value,
                chain = selectedBlockchain.value?.takeIf { it != zcashBlockchain }?.chainTicker
            )
            isSavingContact.update { false }
        }
}
