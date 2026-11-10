package co.electriccoin.zcash.ui.screen.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.type.AddressType
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.CreateProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveABContactPickedUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveContactByAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateAddressUseCase
import co.electriccoin.zcash.ui.common.zns.LookUpZnsRecipientUseCase
import co.electriccoin.zcash.ui.common.zns.ZnsConfirmation
import co.electriccoin.zcash.ui.common.zns.ZnsLookupResult
import co.electriccoin.zcash.ui.common.zns.ZnsName
import co.electriccoin.zcash.ui.common.zns.ZnsSendRecipient
import co.electriccoin.zcash.ui.common.zns.ZnsSendRecipientRepository
import co.electriccoin.zcash.ui.common.zns.ZnsSendStopped
import co.electriccoin.zcash.ui.common.zns.errorMessage
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.contact.AddZashiABContactArgs
import co.electriccoin.zcash.ui.screen.send.model.AmountField
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import co.electriccoin.zcash.ui.screen.send.model.SendAddressBookState
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import co.electriccoin.zcash.ui.screen.send.model.ZnsRecipientStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SendViewModel(
    exchangeRateRepository: ExchangeRateRepository,
    private val observeContactByAddress: ObserveContactByAddressUseCase,
    private val observeContactPicked: ObserveABContactPickedUseCase,
    private val createProposal: CreateProposalUseCase,
    private val observeWalletAccounts: GetWalletAccountsUseCase,
    private val navigateToSelectRecipient: NavigateToSelectRecipientUseCase,
    private val navigationRouter: NavigationRouter,
    private val validateAddress: ValidateAddressUseCase,
    private val lookUpZnsRecipient: LookUpZnsRecipientUseCase,
    private val znsSendRecipientRepository: ZnsSendRecipientRepository,
) : ViewModel() {
    val recipientAddressState = MutableStateFlow(RecipientAddressState.new("", null))

    private var onCreateZecSendClickJob: Job? = null

    private var recipientInputJob: Job? = null

    /** The text the user last typed into the recipient field. Answers for any other text are dropped. */
    private var latestRecipientInput: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val sendAddressBookState =
        recipientAddressState
            .flatMapLatest { recipientAddressState ->
                combine(
                    observeWalletAccounts.require(),
                    observeContactByAddress(recipientAddressState.address)
                ) { accounts, contact -> accounts to contact }
                    .flatMapLatest { (accounts, contact) ->
                        flow {
                            val exists =
                                contact != null ||
                                    accounts.any { it.unifiedAddress == recipientAddressState.address }
                            val isValid = recipientAddressState.type?.isNotValid == false
                            val mode =
                                if (isValid) {
                                    if (exists) {
                                        SendAddressBookState.Mode.PICK_FROM_ADDRESS_BOOK
                                    } else {
                                        SendAddressBookState.Mode.ADD_TO_ADDRESS_BOOK
                                    }
                                } else {
                                    SendAddressBookState.Mode.PICK_FROM_ADDRESS_BOOK
                                }
                            val isHintVisible = !exists && isValid

                            emit(
                                SendAddressBookState(
                                    mode = mode,
                                    isHintVisible = isHintVisible,
                                    onButtonClick = { onAddressBookButtonClicked(mode, recipientAddressState) }
                                )
                            )

                            if (isHintVisible) {
                                delay(3.seconds)
                                emit(
                                    SendAddressBookState(
                                        mode = mode,
                                        isHintVisible = false,
                                        onButtonClick = { onAddressBookButtonClicked(mode, recipientAddressState) }
                                    )
                                )
                            }
                        }
                    }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                SendAddressBookState(
                    mode = SendAddressBookState.Mode.PICK_FROM_ADDRESS_BOOK,
                    isHintVisible = false,
                    onButtonClick = {
                        onAddressBookButtonClicked(
                            mode = SendAddressBookState.Mode.PICK_FROM_ADDRESS_BOOK,
                            recipient = recipientAddressState.value
                        )
                    }
                )
            )

    init {
        viewModelScope.launch {
            observeContactPicked().collect {
                onRecipientAddressChanged(it)
            }
        }
        viewModelScope.launch {
            znsSendRecipientRepository.stopped.collect { stopped ->
                if (stopped != null) {
                    znsSendRecipientRepository.takeStopped()?.let(::onZnsSendStopped)
                }
            }
        }
        exchangeRateRepository.refreshExchangeRateUsd()
    }

    private fun onAddressBookButtonClicked(mode: SendAddressBookState.Mode, recipient: RecipientAddressState) {
        when (mode) {
            SendAddressBookState.Mode.PICK_FROM_ADDRESS_BOOK -> {
                viewModelScope.launch { navigateToSelectRecipient() }
            }

            SendAddressBookState.Mode.ADD_TO_ADDRESS_BOOK -> {
                navigationRouter.forward(
                    AddZashiABContactArgs(
                        address = recipient.address,
                        name = (recipient.zns as? ZnsRecipientStatus.Resolved)?.name?.displayName
                    )
                )
            }
        }
    }

    /** Sets the recipient from a contact, a scan or a payment request. Such a recipient never resolves a name. */
    fun onRecipientAddressChanged(state: RecipientAddressState) {
        // The send screen also calls this on every recomposition while it was opened with a recipient.
        if (recipientAddressState.value == state) return
        recipientInputJob?.cancel()
        latestRecipientInput = null
        znsSendRecipientRepository.setRecipient(null)
        recipientAddressState.update { state }
    }

    /**
     * Handles text the user typed or pasted into the recipient field: an address is validated, and a ZNS name is
     * looked up once the user stops typing. A resolution counts only while the field still holds the text it was
     * resolved from, so a late answer for earlier input cannot change the address a send pays.
     */
    fun onRecipientInput(input: String) {
        recipientInputJob?.cancel()
        latestRecipientInput = input
        znsSendRecipientRepository.setRecipient(null)
        val name = ZnsName.fromRecipientInput(input)
        if (name == null) {
            // As before names: the field takes the text once it is validated.
            recipientInputJob =
                viewModelScope.launch {
                    val type = validateAddress(input)
                    if (latestRecipientInput == input) {
                        recipientAddressState.update { RecipientAddressState.new(input, type) }
                    }
                }
            return
        }
        recipientAddressState.update {
            RecipientAddressState(
                address = input,
                type = AddressType.Invalid(ZNS_PENDING_REASON),
                zns = ZnsRecipientStatus.LookingUp(name = name, input = input)
            )
        }
        recipientInputJob =
            viewModelScope.launch {
                delay(ZNS_LOOKUP_DEBOUNCE)
                val result = lookUpZnsRecipient(input)
                if (latestRecipientInput == input) {
                    recipientAddressState.update { result.toRecipientState(input) }
                }
            }
    }

    private fun ZnsLookupResult.toRecipientState(input: String): RecipientAddressState =
        when (this) {
            ZnsLookupResult.NotAName -> {
                RecipientAddressState.new(input, AddressType.Invalid(ZNS_PENDING_REASON))
            }

            is ZnsLookupResult.Resolved -> {
                RecipientAddressState(
                    address = address,
                    type = type,
                    zns = ZnsRecipientStatus.Resolved(name = name, input = input)
                )
            }

            is ZnsLookupResult.ForName -> {
                RecipientAddressState(
                    address = input,
                    type = AddressType.Invalid(ZNS_PENDING_REASON),
                    zns =
                        ZnsRecipientStatus.Failed(
                            name = name,
                            input = input,
                            message = checkNotNull(errorMessage())
                        )
                )
            }
        }

    /** A send to a name was stopped right before it went out: say why under the field, and block review. */
    private fun onZnsSendStopped(stopped: ZnsSendStopped) {
        recipientInputJob?.cancel()
        val name = stopped.recipient.name
        latestRecipientInput = name.displayName
        recipientAddressState.update {
            RecipientAddressState(
                address = name.displayName,
                type = AddressType.Invalid(ZNS_PENDING_REASON),
                zns =
                    ZnsRecipientStatus.Failed(
                        name = name,
                        input = name.displayName,
                        message =
                            when (stopped.confirmation) {
                                ZnsConfirmation.CHANGED -> stringRes(R.string.zns_error_changed, name.displayName)
                                else -> stringRes(R.string.zns_error_unconfirmed, name.displayName)
                            }
                    )
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun onCreateZecSendClick(
        newZecSend: ZecSend,
        amountState: AmountState,
        setSendStage: (SendStage) -> Unit
    ) {
        if (onCreateZecSendClickJob?.isActive == true) return
        val recipient = recipientAddressState.value
        znsSendRecipientRepository.setRecipient(
            (recipient.zns as? ZnsRecipientStatus.Resolved)
                ?.takeIf { recipient.address == newZecSend.destination.address }
                ?.let { ZnsSendRecipient(name = it.name, address = recipient.address) }
        )
        onCreateZecSendClickJob =
            viewModelScope.launch {
                try {
                    createProposal(newZecSend, amountState.lastFieldChangedByUser == AmountField.FIAT)
                } catch (e: Exception) {
                    setSendStage(SendStage.SendFailure(e.cause?.message ?: e.message ?: ""))
                    Twig.error(e) { "Error creating proposal" }
                }
            }
    }
}

/** The send form waits this long after the last edit before it looks a name up. */
private val ZNS_LOOKUP_DEBOUNCE = 350.milliseconds

/** The invalid-address reason of a recipient that is a name without a usable resolution. */
private const val ZNS_PENDING_REASON = "ZNS name"
