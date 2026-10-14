package co.electriccoin.zcash.ui.screen.reviewtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.ExactOutputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.SendTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.usecase.CancelProposalFlowUseCase
import co.electriccoin.zcash.ui.common.usecase.GetExchangeRateUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveContactByAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitProposalUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ChipButtonState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.Ellipsize
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.screen.contact.AddZashiABContactArgs
import co.electriccoin.zcash.ui.util.Quintuple
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReviewTransactionVM(
    getWalletAccounts: GetWalletAccountsUseCase,
    observeContactByAddress: ObserveContactByAddressUseCase,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
    observeProposal: ObserveProposalUseCase,
    private val cancelProposalFlow: CancelProposalFlowUseCase,
    private val getExchangeRate: GetExchangeRateUseCase,
    private val navigationRouter: NavigationRouter,
    private val submitProposal: SubmitProposalUseCase,
) : ViewModel() {
    private val isReceiverExpanded = MutableStateFlow(false)

    private val orchardWarningSheet = MutableStateFlow<ZashiConfirmationState?>(null)

    private val exchangeRate =
        flow {
            emit(getExchangeRate())
        }.shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    @OptIn(ExperimentalCoroutinesApi::class)
    private val baseState =
        combine(
            observeSelectedWalletAccount.require(),
            observeProposal.filterSend(),
            isReceiverExpanded,
            exchangeRate,
            getWalletAccounts.observe()
        ) { wallet, zecSend, isReceiverExpanded, exchangeRate, accounts ->
            Quintuple(wallet, zecSend, isReceiverExpanded, exchangeRate, accounts)
        }.flatMapLatest { (selectedWallet, proposal, isReceiverExpanded, exchangeRate, accounts) ->
            observeContactByAddress(
                if (proposal is ExactOutputSwapTransactionProposal) {
                    proposal.quote.destinationAddress.address
                } else {
                    proposal.destination.address
                }
            ).map { addressBookContact ->
                when (proposal) {
                    is Zip321TransactionProposal -> {
                        createZip321State(
                            transactionProposal = proposal,
                            addressBookContact = addressBookContact,
                            selectedWallet = selectedWallet,
                            isReceiverExpanded = isReceiverExpanded,
                            exchangeRateState = exchangeRate
                        )
                    }

                    else -> {
                        createState(
                            transactionProposal = proposal,
                            addressBookContact = addressBookContact,
                            selectedWallet = selectedWallet,
                            exchangeRateState = exchangeRate,
                            accounts = accounts
                        )
                    }
                }
            }
        }

    val state =
        combine(baseState, orchardWarningSheet) { base, sheet ->
            base.copy(orchardWarningSheet = sheet)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private var onConfirmClickJob: Job? = null

    init {
        // Side effect kept in init{} rather than in the state combine to avoid re-triggering on
        // resubscribe (see the LCE/side-effects convention). Suspends until the first send proposal
        // that spends Orchard notes, then shows the warning sheet once for this screen entry.
        viewModelScope.launch {
            observeProposal.filterSend().first { it.proposal.usesOrchardInputs() }
            orchardWarningSheet.value =
                orchardWarningSheetState(
                    usesOrchardInputs = true,
                    onContinue = ::onOrchardWarningContinue,
                    onCancel = ::onOrchardWarningCancel,
                )
        }
    }

    private fun createState(
        selectedWallet: WalletAccount,
        transactionProposal: SendTransactionProposal,
        addressBookContact: EnhancedABContact?,
        exchangeRateState: ExchangeRateState,
        accounts: List<WalletAccount>?
    ) = ReviewTransactionState(
        title =
            when (selectedWallet) {
                is KeystoneAccount -> stringRes(R.string.send_review)
                is LedgerAccount -> stringRes(R.string.send_review)
                is ZashiAccount -> stringRes(R.string.send_confirmationTitle)
            },
        items =
            listOfNotNull(
                AmountState(
                    title = stringRes(R.string.send_amountSummary),
                    amount = transactionProposal.amount + transactionProposal.proposal.totalFeeRequired(),
                    exchangeRate = exchangeRateState,
                ),
                ReceiverState(
                    title = stringRes(R.string.send_to),
                    name = addressBookContact?.name?.let { stringRes(it) },
                    address = stringResByAddress(transactionProposal.destination.address, Ellipsize.NONE)
                ),
                SenderState(
                    title = stringRes(R.string.accounts_sendingFrom),
                    icon = selectedWallet.icon,
                    name = selectedWallet.name
                ).takeIf { (accounts?.size ?: 0) > 1 },
                FinancialInfoState(
                    title = stringRes(R.string.send_amount),
                    amount = transactionProposal.amount
                ),
                FinancialInfoState(
                    title = stringRes(R.string.send_feeSummary),
                    amount = transactionProposal.proposal.totalFeeRequired()
                ),
                transactionProposal.memo
                    .takeIf { it.value.isNotEmpty() }
                    ?.let {
                        MessageState(
                            title = stringRes(R.string.send_message),
                            message = stringRes(it.value)
                        )
                    }?.takeIf { transactionProposal.destination !is WalletAddress.Transparent },
                MessagePlaceholderState(
                    title = stringRes(R.string.send_message),
                    message = stringRes(R.string.send_info_memo),
                    icon = R.drawable.ic_confirmation_message_info,
                ).takeIf { transactionProposal.destination is WalletAddress.Transparent },
                orchardPrivacyWarningState(transactionProposal.proposal.usesOrchardInputs()),
            ),
        primaryButton =
            ButtonState(
                text =
                    when (selectedWallet) {
                        is KeystoneAccount -> stringRes(R.string.keystone_confirm)
                        is LedgerAccount -> stringRes(R.string.ledger_confirm)
                        is ZashiAccount -> stringRes(R.string.tabs_send)
                    },
                style = orchardPrivacyWarningButtonStyle(transactionProposal.proposal.usesOrchardInputs()),
                onClick = ::onConfirmClick
            ),
        onBack = ::onBack,
    )

    private fun createZip321State(
        transactionProposal: SendTransactionProposal,
        addressBookContact: EnhancedABContact?,
        selectedWallet: WalletAccount,
        isReceiverExpanded: Boolean,
        exchangeRateState: ExchangeRateState
    ) = ReviewTransactionState(
        title = stringRes(R.string.send_requestPayment_title),
        items =
            listOfNotNull(
                AmountState(
                    title = null,
                    amount = transactionProposal.amount,
                    exchangeRate = exchangeRateState,
                ),
                SenderState(
                    title = stringRes(R.string.accounts_sendingFrom),
                    icon = selectedWallet.icon,
                    name = selectedWallet.name
                ),
                ReceiverExpandedState(
                    title = stringRes(R.string.send_requestPayment_requestedBy),
                    name = addressBookContact?.name?.let { stringRes(it) },
                    address =
                        stringResByAddress(
                            transactionProposal.destination.address,
                            if (isReceiverExpanded) Ellipsize.NONE else Ellipsize.END
                        ),
                    showButton =
                        ChipButtonState(
                            startIcon =
                                if (isReceiverExpanded) {
                                    co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_up
                                } else {
                                    co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_down
                                },
                            text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_show),
                            onClick = ::onExpandReceiverClick
                        ),
                    saveButton =
                        ChipButtonState(
                            startIcon = R.drawable.ic_user_plus,
                            text = stringRes(R.string.general_save),
                            onClick = { onAddContactClick(transactionProposal.destination.address) }
                        ).takeIf { addressBookContact == null }
                ),
                transactionProposal.memo.takeIf { it.value.isNotEmpty() }?.let {
                    MessageState(
                        title = stringRes(R.string.send_requestPayment_for),
                        message = stringRes(it.value)
                    )
                },
                FinancialInfoState(
                    title = stringRes(R.string.send_feeSummary),
                    amount = transactionProposal.proposal.totalFeeRequired()
                ),
                orchardPrivacyWarningState(transactionProposal.proposal.usesOrchardInputs()),
            ),
        primaryButton =
            ButtonState(
                style = orchardPrivacyWarningButtonStyle(transactionProposal.proposal.usesOrchardInputs()),
                text =
                    when (selectedWallet) {
                        is KeystoneAccount -> stringRes(R.string.keystone_confirm)
                        is LedgerAccount -> stringRes(R.string.ledger_confirm)
                        is ZashiAccount -> stringRes(R.string.tabs_send)
                    },
                onClick = ::onConfirmClick
            ),
        onBack = ::onBack,
    )

    private fun onExpandReceiverClick() = isReceiverExpanded.update { !it }

    private fun onBack() = viewModelScope.launch { cancelProposalFlow(clearSendForm = false) }

    // "Continue anyway": dismiss the sheet and stay on the review screen; the user still has to tap
    // the primary button to broadcast, exactly as without the warning.
    private fun onOrchardWarningContinue() {
        orchardWarningSheet.value = null
    }

    // "Cancel" (and back gesture / scrim tap): dismiss the sheet and leave the review screen back to
    // Send, reusing the screen's existing cancel-proposal-flow action.
    private fun onOrchardWarningCancel() {
        orchardWarningSheet.value = null
        onBack()
    }

    private fun onConfirmClick() {
        if (onConfirmClickJob?.isActive == true) return
        onConfirmClickJob = viewModelScope.launch { submitProposal() }
    }

    private fun onAddContactClick(address: String) = navigationRouter.forward(AddZashiABContactArgs(address))
}

// Spec §8 "Orchard Privacy Warning on Regular Send": extracted as pure functions so they're
// directly testable without mocking the whole VM (mirrors GetHomeMessageUseCase.migrationMessageFor).
// Checked once the proposal already exists (rather than eagerly re-proposing on every keystroke in
// the Send form itself) — this is still shown "before they proceed with the send" per the spec,
// since the user still has to tap Confirm/Send on this screen to actually broadcast.
internal fun orchardPrivacyWarningState(usesOrchardInputs: Boolean): OrchardPrivacyWarningState? =
    OrchardPrivacyWarningState(
        title = "This send requires spending Orchard funds",
        body = "We recommend migrating your funds first to avoid leaking the transaction amount on-chain.",
    ).takeIf { usesOrchardInputs }

internal fun orchardPrivacyWarningButtonStyle(usesOrchardInputs: Boolean): ButtonStyle? =
    ButtonStyle.DESTRUCTIVE1.takeIf { usesOrchardInputs }

// Spec §8 bottom sheet shown on entry to Review when the proposal spends Orchard notes: warns the
// user that sending now (pre-migration) leaks the amount at the turnstile. Extracted as a pure
// builder so the copy, styles, and button wiring are testable without the whole VM (mirrors
// orchardPrivacyWarningState above). "Continue anyway" is destructive-outline over the dark
// "Cancel", matching the Figma order (primary rendered first/top by ZashiConfirmationBottomSheet).
internal fun orchardWarningSheetState(
    usesOrchardInputs: Boolean,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
): ZashiConfirmationState? =
    ZashiConfirmationState(
        icon = R.drawable.ic_reset_zashi_warning,
        title = stringRes(R.string.send_orchardWarning_title),
        message = stringRes(R.string.send_orchardWarning_message),
        primaryAction =
            ButtonState(
                text = stringRes(R.string.send_orchardWarning_continue),
                style = ButtonStyle.DESTRUCTIVE1,
                onClick = onContinue,
            ),
        secondaryAction =
            ButtonState(
                text = stringRes(R.string.send_orchardWarning_cancel),
                style = ButtonStyle.PRIMARY,
                onClick = onCancel,
            ),
        onBack = onCancel,
    ).takeIf { usesOrchardInputs }
