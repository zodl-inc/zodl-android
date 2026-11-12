package co.electriccoin.zcash.ui.screen.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.Spendability
import co.electriccoin.zcash.ui.common.model.isProposalTooLarge
import co.electriccoin.zcash.ui.common.model.maxChipState
import co.electriccoin.zcash.ui.common.model.spendability
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.CreateProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMaxSendAmountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.MaxSendTarget
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveABContactPickedUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveContactByAddressUseCase
import co.electriccoin.zcash.ui.common.viewmodel.MaxAmountRequester
import co.electriccoin.zcash.ui.common.viewmodel.MaxFeedbackController
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxTooManyNotesSheetState
import co.electriccoin.zcash.ui.design.component.MaxVariant
import co.electriccoin.zcash.ui.design.component.SendMaxChipState
import co.electriccoin.zcash.ui.screen.contact.AddZashiABContactArgs
import co.electriccoin.zcash.ui.screen.send.model.AmountField
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import co.electriccoin.zcash.ui.screen.send.model.ResolvedMaxAmount
import co.electriccoin.zcash.ui.screen.send.model.SendAddressBookState
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class SendViewModel(
    exchangeRateRepository: ExchangeRateRepository,
    private val observeContactByAddress: ObserveContactByAddressUseCase,
    private val observeContactPicked: ObserveABContactPickedUseCase,
    private val createProposal: CreateProposalUseCase,
    private val observeWalletAccounts: GetWalletAccountsUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getMaxSendAmount: GetMaxSendAmountUseCase,
    private val navigateToSelectRecipient: NavigateToSelectRecipientUseCase,
    private val navigationRouter: NavigationRouter,
    private val signerCapabilitiesProvider: SignerCapabilitiesProvider,
) : ViewModel() {
    val recipientAddressState = MutableStateFlow(RecipientAddressState.new("", null))

    private var onCreateZecSendClickJob: Job? = null

    /** The memo text typed in the form; the Max proposal is priced with it. */
    private val memoText = MutableStateFlow("")

    /**
     * A channel rather than a plain shared flow: a result that arrives while the screen's collector is being
     * recreated (recomposition, configuration change) waits for the next collector instead of being dropped, and is
     * delivered exactly once, so it can never be replayed over a later edit.
     */
    private val maxAmountResolvedEvents = Channel<ResolvedMaxAmount>(Channel.BUFFERED)
    private val amountClearEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The form's amount lives in the screen, so the sheet's OK asks it to clear the field. */
    private val maxFeedback =
        MaxFeedbackController(
            variant = MaxVariant.SEND,
            onClearAmount = { amountClearEvents.tryEmit(Unit) }
        )

    private val maxAmountRequester =
        MaxAmountRequester(
            scope = viewModelScope,
            getMaxSendAmount = getMaxSendAmount,
            onFailed = maxFeedback::onFailed
        )

    /** The max amount for the recipient it was computed for. */
    val maxAmountResolved: Flow<ResolvedMaxAmount> = maxAmountResolvedEvents.receiveAsFlow()

    /** Emitted by the too-many-notes sheet's OK: the screen clears the amount. */
    val amountClears: SharedFlow<Unit> = amountClearEvents

    /** Emitted after [amountClears]: the screen focuses the amount field once the sheet is hidden. */
    val amountFocusRequests: SharedFlow<Unit> = maxFeedback.focusRequests

    /**
     * The one-line "Max isn't available right now" error above the Review button, or null. A Max retap, an amount
     * edit or an address change clears it.
     */
    val maxFeedbackState: StateFlow<MaxFeedback?> = maxFeedback.error

    /** The blocking too-many-notes sheet while it is shown, null otherwise. */
    val tooManyNotesSheet: StateFlow<MaxTooManyNotesSheetState?> = maxFeedback.sheet

    private val onMaxClickAction: () -> Unit = ::onMaxClick

    /**
     * OK on the proposal-time too-many-notes sheet ([SendStage.TooManyNotes]): clear the amount and focus its field,
     * the same way the Max sheet's OK does.
     */
    fun onTooManyNotesOk() {
        amountClearEvents.tryEmit(Unit)
        maxFeedback.requestAmountFocus()
    }

    /**
     * Started eagerly, unlike the other flows here, because [onMaxClick] reads its current value as the guard for a
     * tap: it must reflect the selected account even before (or between) the view collecting it. This is cheap, as
     * the selected account is already shared eagerly upstream.
     */
    val maxChipState: StateFlow<SendMaxChipState> =
        combine(
            recipientAddressState,
            getSelectedWalletAccount.observe()
        ) { recipient, account ->
            maxChipState(
                spendability = account?.spendability ?: Spendability.Nothing(Spendability.Nothing.Reason.Empty),
                isExtraEnabled = recipient.type?.isNotValid == false,
                onClick = onMaxClickAction
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SendMaxChipState(isEnabled = false, onClick = onMaxClickAction)
        )

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
        exchangeRateRepository.refreshExchangeRateUsd()
    }

    private fun onAddressBookButtonClicked(mode: SendAddressBookState.Mode, recipient: RecipientAddressState) {
        when (mode) {
            SendAddressBookState.Mode.PICK_FROM_ADDRESS_BOOK -> {
                viewModelScope.launch { navigateToSelectRecipient() }
            }

            SendAddressBookState.Mode.ADD_TO_ADDRESS_BOOK -> {
                navigationRouter.forward(AddZashiABContactArgs(recipient.address))
            }
        }
    }

    fun onRecipientAddressChanged(state: RecipientAddressState) {
        if (recipientAddressState.value != state) {
            maxAmountRequester.cancel()
            dropUnappliedMaxAmounts()
            maxFeedback.clearError()
            if (maxAmountRequester.activeBaseline.value != null) {
                // The filled amount was priced for the previous recipient (the fee differs by recipient type, e.g. a
                // TEX recipient needs two steps), so it no longer holds: clear it and end the Max intent. The user
                // taps Max again for the new recipient.
                maxAmountRequester.clearIntent()
                amountClearEvents.tryEmit(Unit)
            }
        }
        recipientAddressState.update { state }
    }

    fun onMemoChanged(text: String) {
        memoText.value = text
    }

    /** The user edited the amount field. */
    fun onAmountEdited() {
        maxFeedback.clearError()
        maxAmountRequester.clearIntent()
    }

    /** Drops a Max result that was resolved but not yet applied by the screen. */
    private fun dropUnappliedMaxAmounts() {
        while (maxAmountResolvedEvents.tryReceive().isSuccess) {
            // drain
        }
    }

    fun onMaxClick() {
        val recipient = recipientAddressState.value
        val type = recipient.type
        val chip = maxChipState.value
        if (type == null || !chip.isEnabled || maxAmountRequester.isInFlight.value) return
        maxFeedback.clearError()
        maxAmountRequester.request(MaxSendTarget.Recipient(recipient.address, type, memoText.value)) { max ->
            // The recipient can change while the request runs; the max is only valid for the one it was computed for.
            if (recipientAddressState.value.address == recipient.address) {
                maxAmountResolvedEvents.send(ResolvedMaxAmount(recipient.address, max.amount))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun onCreateZecSendClick(
        newZecSend: ZecSend,
        amountState: AmountState,
        setSendStage: (SendStage) -> Unit
    ) {
        maxAmountRequester.cancel()
        dropUnappliedMaxAmounts()
        if (onCreateZecSendClickJob?.isActive == true) return
        onCreateZecSendClickJob =
            viewModelScope.launch {
                try {
                    createProposal(newZecSend, amountState.lastFieldChangedByUser == AmountField.FIAT)
                } catch (e: Exception) {
                    setSendStage(
                        if (e.isProposalTooLarge()) {
                            SendStage.TooManyNotes
                        } else {
                            SendStage.SendFailure(e.cause?.message ?: e.message ?: "")
                        }
                    )
                    Twig.error(e) { "Error creating proposal" }
                }
            }
    }

    /**
     * The send form is gone (sent, cancelled or navigated away): the signer capabilities measured for this flow must
     * not be reused by the next send. Review keeps this view model alive, so it does not end the flow early.
     */
    override fun onCleared() {
        signerCapabilitiesProvider.endFlow()
        super.onCleared()
    }
}
