package co.electriccoin.zcash.ui.screen.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.model.Spendability
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapDirection
import co.electriccoin.zcash.ui.common.model.SwapDirection.SWAP_FROM_ZEC
import co.electriccoin.zcash.ui.common.model.SwapDirection.SWAP_INTO_ZEC
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.canSpend
import co.electriccoin.zcash.ui.common.model.spendability
import co.electriccoin.zcash.ui.common.model.toSwapMode
import co.electriccoin.zcash.ui.common.repository.DEFAULT_SLIPPAGE
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.GetCuratedSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMaxSendAmountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.MaxSendTarget
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapRefundWarningUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.TooManyNotesOutcome
import co.electriccoin.zcash.ui.common.usecase.resolve
import co.electriccoin.zcash.ui.common.viewmodel.MaxAmountRequester
import co.electriccoin.zcash.ui.common.viewmodel.MaxFeedbackController
import co.electriccoin.zcash.ui.common.viewmodel.MaxFillPolicy
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.InnerTextFieldState
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxTooManyNotesSheetState
import co.electriccoin.zcash.ui.design.component.MaxVariant
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.TextSelection
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import co.electriccoin.zcash.ui.screen.balances.spendable.SpendableBalanceArgs
import co.electriccoin.zcash.ui.screen.swap.info.SwapRefundAddressInfoArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.Duration.Companion.milliseconds

@Suppress("TooManyFunctions")
internal class SwapVM(
    private val getCuratedSwapAssetsUseCase: GetCuratedSwapAssetsUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getPreselectedSwapAsset: GetPreselectedSwapAssetUseCase,
    private val swapRepository: SwapRepository,
    private val navigateToSwapInfo: NavigateToSwapInfoUseCase,
    private val cancelSwap: CancelSwapUseCase,
    private val navigationRouter: NavigationRouter,
    private val requestSwapQuote: RequestSwapQuoteUseCase,
    private val navigateToSwapQuoteIfAvailable: NavigateToSwapQuoteIfAvailableUseCase,
    private val swapVMMapper: SwapVMMapper,
    private val navigateToScanAddress: NavigateToScanGenericAddressUseCase,
    private val navigateToSelectSwapRecipient: NavigateToSelectABSwapRecipientUseCase,
    private val navigateToSlippage: NavigateToSlippageUseCase,
    private val navigateToSwapAssetPicker: NavigateToSwapAssetPickerUseCase,
    private val navigateToSwapRefundWarning: NavigateToSwapRefundWarningUseCase,
    private val getMaxSendAmount: GetMaxSendAmountUseCase,
) : ViewModel() {
    // VM-owned state. The externally-observed `swapAssets`/`account` fields are injected by the
    // `state` combine below, so the VM only ever updates the fields it owns.
    private val internalState =
        MutableStateFlow(
            InternalStateImpl(
                account = null,
                swapAsset = null,
                currencyType = CurrencyType.TOKEN,
                amountTextState = NumberTextFieldInnerState(),
                addressText = "",
                slippage = DEFAULT_SLIPPAGE,
                swapAssets = SwapAssetsData(),
                isRequestingQuote = false,
                selectedContact = null,
                swapDirection = SWAP_INTO_ZEC,
                isEphemeralAddressLocked = false
            )
        )

    /**
     * Eager, so a Max tap can check the balance synchronously and raise the in-flight flag before anything suspends.
     * Cheap: the selected account is already shared eagerly upstream.
     */
    private val selectedAccount =
        getSelectedWalletAccount.observe().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val maxFeedback = MaxFeedbackController(variant = MaxVariant.SWAP, onClearAmount = ::clearAmount)

    private val maxAmountRequester =
        MaxAmountRequester(
            scope = viewModelScope,
            getMaxSendAmount = getMaxSendAmount,
            onFailed = maxFeedback::onFailed
        )

    /** The blocking too-many-notes sheet shown when Max cannot fit the wallet's notes into one transaction. */
    val maxSheetState: StateFlow<MaxTooManyNotesSheetState?> = maxFeedback.sheet

    /** Emits when the screen should focus the From amount field (the sheet's OK). */
    val amountFocusRequests: SharedFlow<Unit> = maxFeedback.focusRequests

    /**
     * Identifies the current Max intent: bumped whenever the intent ends or a new Max tap starts. A refill captures it
     * when it starts and drops its write if it changed, so a refill cannot overwrite what the user did meanwhile.
     */
    private var maxIntentGeneration = 0

    private val isCancelStateVisible = MutableStateFlow(false)

    /**
     * Whether the selected account holds spend authority, null until it has loaded; tracked so the
     * direction toggle and the quote request can refuse the swap-from-ZEC direction for an account
     * without it.
     */
    private val isSelectedAccountSpendable = MutableStateFlow<Boolean?>(null)

    val cancelState =
        isCancelStateVisible
            .map { isVisible ->
                if (isVisible) {
                    SwapCancelState(
                        icon = imageRes(R.drawable.ic_swap_quote_cancel),
                        title = stringRes(R.string.swapAndPay_canceltitle),
                        subtitle = stringRes(R.string.swapAndPay_cancelMsg),
                        negativeButton =
                            ButtonState(
                                text = stringRes(R.string.swapAndPay_cancelPayment),
                                onClick = ::onCancelSwapClick
                            ),
                        positiveButton =
                            ButtonState(
                                text = stringRes(R.string.swap_cancel_positive),
                                onClick = ::onDismissCancelClick
                            ),
                        onBack = ::onBack
                    )
                } else {
                    null
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    val state =
        combine(
            internalState,
            getCuratedSwapAssetsUseCase.observe(),
            selectedAccount,
            maxFeedback.error,
        ) { state, swapAssets, account, maxError ->
            createState(
                state.copy(
                    swapAssets = swapAssets,
                    account = account,
                    maxError = maxError,
                    swapDirection = state.swapDirection.coerceFor(account?.isSpendable)
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            getSelectedWalletAccount
                .observe()
                .map { it?.isSpendable }
                .distinctUntilChanged()
                .collect { isSpendable ->
                    isSelectedAccountSpendable.update { isSpendable }
                    internalState.update { it.copy(swapDirection = it.swapDirection.coerceFor(isSpendable)) }
                }
        }
        viewModelScope.launch {
            val asset = getPreselectedSwapAsset()
            val currentAssets = swapRepository.assets.value
            val resolvedAsset =
                currentAssets.data?.firstOrNull { it.assetId == asset.assetId }
                    ?: asset.takeUnless { currentAssets.data != null && !currentAssets.isLoading }
            internalState.update { if (it.swapAsset == null) it.copy(swapAsset = resolvedAsset) else it }
        }
        viewModelScope.launch {
            swapRepository.quote
                .filterIsInstance<SwapQuoteData.Success>()
                .collect { refreshMaxFill(it.quote) }
        }
        viewModelScope.launch {
            swapRepository.assets.collect { assets ->
                val catalog = assets.data ?: return@collect
                internalState.update { state ->
                    val selected = state.swapAsset ?: return@update state
                    val refreshed = catalog.firstOrNull { it.assetId == selected.assetId }
                    when {
                        refreshed == selected -> state
                        refreshed != null -> state.copy(swapAsset = refreshed)
                        !assets.isLoading -> state.copy(swapAsset = null)
                        else -> state
                    }
                }
            }
        }
    }

    // Stable method references — created once and reused across every state emission.
    private val callbacks =
        SwapStateCallbacks(
            onBack = ::onBack,
            onSwapInfoClick = ::onSwapInfoClick,
            onSwapAssetPickerClick = ::onSwapAssetPickerClick,
            onSwapCurrencyTypeClick = ::onSwapCurrencyTypeClick,
            onSlippageClick = ::onSlippageClick,
            onRequestSwapQuoteClick = ::onRequestSwapQuoteClick,
            onTryAgainClick = ::onTryAgainClick,
            onAddressChange = ::onAddressChange,
            onTextFieldChange = ::onTextFieldChange,
            onQrCodeScannerClick = ::onQrCodeScannerClick,
            onAddressBookClick = ::onAddressBookClick,
            onDeleteSelectedContactClick = ::onDeleteSelectedContactClick,
            onMaxClick = ::onMaxClick,
            onSpendableClick = ::onSpendableClick,
            onChangeButtonClick = ::onChangeButtonClick,
            onAddressClick = ::onAddressClick
        )

    private fun createState(internalState: InternalStateImpl): SwapState =
        swapVMMapper.createState(internalState = internalState, callbacks = callbacks)

    /**
     * Fills the From field with the max spendable ZEC (net of fees, against the account's own transparent receiver
     * since the real deposit address only exists after a quote) and starts the Max intent: the filled amount becomes
     * [MaxAmountRequester.activeBaseline], and every quote refresh re-fills from it (see [refreshMaxFill]) until the
     * user edits the amount, flips the direction or changes the asset. It never starts a quote; the user taps
     * "Get a quote" as usual. Re-taps while a request is running are ignored by the requester's in-flight guard.
     */
    private fun onMaxClick() {
        val current = internalState.value
        val isAvailable =
            current.swapDirection == SWAP_FROM_ZEC &&
                selectedAccount.value?.spendability is Spendability.Some &&
                SwapMaxAmountFormatter.isMaxUsable(
                    isRequestingQuote = current.isRequestingQuote,
                    currencyType = current.currencyType,
                    zecUsdPrice = getCuratedSwapAssetsUseCase().zecAsset?.usdPrice
                )
        if (!isAvailable || maxAmountRequester.isInFlight.value) return
        maxFeedback.clearError()
        val generation = ++maxIntentGeneration
        maxAmountRequester.clearIntent()
        maxAmountRequester.request(
            target = MaxSendTarget.OwnTransparentReceiver,
            spendableAtTap = selectedAccount.value?.spendability?.shieldedSpendableOrNull()
        ) {
            // Stale-resolve guard: an edit, a direction flip or a re-tap since the tap ended this intent
            if (generation == maxIntentGeneration) applyMaxAmount(it.amount, showHint = true)
        }
    }

    /**
     * Re-fills the From amount when a quote refreshes while the Max intent is active (pacu decision D1):
     * `MaxFillPolicy.refresh(baseline, netSpendable, adjust)`, so it follows spendable funds down and back up but
     * never above the baseline, and never picks up funds that arrived after the tap. The baseline is already net of
     * the fee and the buffer of the tap-time proposal, so what is spendable now is passed net of the same reserve
     * ([MaxAmountRequester.activeReserve], see [MaxFillPolicy.netSpendable]). The adjust function is
     * [SwapMaxAmountFormatter.quoteAdjust]; see there for which quote fields it uses (none).
     *
     * The write is dropped if the Max intent changed while this ran (the user edited the amount, flipped the
     * direction or re-tapped Max): see [maxIntentGeneration].
     *
     * A refresh that leaves nothing to fill (no net spendable) ends the intent: the amount is cleared and the Max
     * error line is shown, so funds that arrive later are not picked up.
     *
     * This runs on every successful quote, including the one the quote screen is about to show. The quote screen
     * shows the quote itself (its own `amountIn`, which the repository already matched to the amount the user sent),
     * so a refill here never changes the quoted amount: only the form underneath, which the user sees on going back,
     * can differ from it. The refill never touches the quote or starts a new one.
     */
    private suspend fun refreshMaxFill(quote: SwapQuote) {
        val generation = maxIntentGeneration
        val fill = if (internalState.value.swapDirection == SWAP_FROM_ZEC) maxFillFor(quote) else null
        // Stale-write guard: an edit that ended or replaced the intent must not be overwritten by this refill
        if (fill == null || generation != maxIntentGeneration) return
        if (fill.value == 0L) {
            clearAmount()
            maxFeedback.onFailed(MaxSendAmountFailure.OTHER)
        } else {
            applyMaxAmount(fill, showHint = false)
        }
    }

    /** The refilled amount for [quote], or null when there is no Max intent to follow. */
    private fun maxFillFor(quote: SwapQuote): Zatoshi? {
        val baseline = maxAmountRequester.activeBaseline.value
        val reserve = maxAmountRequester.activeReserve.value
        val spendable = selectedAccount.value?.spendability?.shieldedSpendableOrNull()
        if (baseline == null || reserve == null || spendable == null) return null
        val netSpendable = MaxFillPolicy.netSpendable(spendable, reserve)
        return MaxFillPolicy.refresh(baseline, netSpendable, SwapMaxAmountFormatter.quoteAdjust(quote))
    }

    private suspend fun applyMaxAmount(maxAmount: Zatoshi, showHint: Boolean) {
        val currencyType = internalState.value.currencyType
        val amount =
            SwapMaxAmountFormatter.format(
                maxAmount = maxAmount,
                currencyType = currencyType,
                zecUsdPrice = getCuratedSwapAssetsUseCase().zecAsset?.usdPrice
            )
        if (amount == null) {
            maxIntentGeneration++
            maxAmountRequester.clearIntent()
            maxFeedback.onFailed(MaxSendAmountFailure.OTHER)
            return
        }
        internalState.update {
            it.copy(
                amountTextState =
                    NumberTextFieldInnerState(
                        innerTextFieldState =
                            InnerTextFieldState(
                                value = stringResByDynamicNumber(amount, includeGroupingSeparator = false),
                                selection = TextSelection.End
                            ),
                        amount = amount,
                        lastValidAmount = amount
                    ),
                maxHint = if (showHint) MaxFeedback.Hint else it.maxHint
            )
        }
    }

    /** "OK" on the too-many-notes sheet: the sheet clears the From amount before the screen focuses it. */
    private fun clearAmount() {
        endMaxIntent()
        internalState.update { it.copy(amountTextState = NumberTextFieldInnerState()) }
    }

    /** Ends the Max intent: no more refreshes, and the hint and the error line go away. */
    private fun endMaxIntent() {
        maxIntentGeneration++
        // An in-flight request must not pin a baseline for an intent that no longer exists
        maxAmountRequester.cancel()
        maxAmountRequester.clearIntent()
        maxFeedback.clearError()
        internalState.update { it.copy(maxHint = null) }
    }

    private fun onSpendableClick() = navigationRouter.forward(SpendableBalanceArgs)

    private fun onChangeButtonClick() {
        if (isSelectedAccountSpendable.value != true) return
        endMaxIntent()
        internalState.update {
            it.copy(
                swapDirection =
                    when (it.swapDirection) {
                        SWAP_FROM_ZEC -> SWAP_INTO_ZEC
                        SWAP_INTO_ZEC -> SWAP_FROM_ZEC
                    }
            )
        }
    }

    private fun onAddressClick() {
        val asset = internalState.value.swapAsset
        navigationRouter.forward(
            SwapRefundAddressInfoArgs(tokenTicker = asset?.tokenTicker, chainTicker = asset?.chainTicker)
        )
    }

    private fun onDeleteSelectedContactClick() = internalState.update { it.copy(selectedContact = null) }

    private fun onTryAgainClick() = swapRepository.requestRefreshAssets()

    private fun onAddressBookClick() =
        viewModelScope.launch {
            val selected = navigateToSelectSwapRecipient()

            if (selected != null) {
                internalState.update { it.copy(selectedContact = selected, addressText = "") }
                preselectChain(selected)
            }
        }

    private fun preselectChain(selected: EnhancedABContact) {
        if (internalState.value.swapDirection != SWAP_INTO_ZEC) return

        val selectedChainTicker = selected.blockchain?.chainTicker
        val currentChainTicker = internalState.value.swapAsset?.chainTicker

        // Only proceed if the chain ticker changed.
        if (selectedChainTicker != null && !selectedChainTicker.equals(currentChainTicker, ignoreCase = true)) {
            val matchingAssets =
                getCuratedSwapAssetsUseCase()
                    .data
                    ?.filter { asset -> asset.chainTicker.equals(selectedChainTicker, ignoreCase = true) }
                    .orEmpty()
            endMaxIntent()
            internalState.update { it.copy(swapAsset = matchingAssets.singleOrNull()) }
        }
    }

    private fun onQrCodeScannerClick() =
        viewModelScope.launch {
            val result = navigateToScanAddress()
            if (result != null) {
                navigationRouter.back()
                // In SWAP_INTO_ZEC, the address field being scanned is the refund address for the
                // *source* asset, not a payment recipient -- it has no bearing on which asset is
                // being swapped. Skip resolution entirely in that direction rather than computing
                // and discarding it: resolve() does a curated-assets read plus request parsing,
                // work this branch never uses.
                val isRefundAddressScan = internalState.value.swapDirection == SWAP_INTO_ZEC
                val resolved =
                    if (isRefundAddressScan) {
                        null
                    } else {
                        result.resolve(getCuratedSwapAssetsUseCase().data.orEmpty(), internalState.value.swapAsset)
                    }
                if (resolved != null && (resolved.amount != null || resolved.isResolvedPaymentRequest)) endMaxIntent()
                internalState.update {
                    it.copy(
                        selectedContact = null,
                        addressText = resolved?.address ?: result.address,
                        swapAsset = resolved?.asset ?: it.swapAsset,
                        amountTextState =
                            when {
                                resolved == null -> {
                                    it.amountTextState
                                }

                                resolved.amount != null -> {
                                    NumberTextFieldInnerState.fromAmount(resolved.amount)
                                }

                                // Only clear a typed amount when the resolved asset actually
                                // differs from what's already selected -- see the matching
                                // comment in PayVM.kt's onQrCodeScannerClick.
                                resolved.isResolvedPaymentRequest &&
                                    resolved.asset?.assetId != it.swapAsset?.assetId -> {
                                    NumberTextFieldInnerState()
                                }

                                else -> {
                                    it.amountTextState
                                }
                            }
                    )
                }
            }
        }

    private fun onSlippageClick(fiatAmount: BigDecimal?) =
        viewModelScope.launch {
            val newSlippage =
                navigateToSlippage(
                    currentSlippage = internalState.value.slippage,
                    fiatAmount = fiatAmount,
                    mode = internalState.value.swapDirection.toSwapMode()
                )
            if (newSlippage != null) {
                internalState.update { it.copy(slippage = newSlippage) }
            }
        }

    private fun onBack() =
        viewModelScope.launch {
            if (internalState.value.isRequestingQuote) {
                isCancelStateVisible.update { true }
            } else if (isCancelStateVisible.value) {
                isCancelStateVisible.update { false }
                navigateToSwapQuoteIfAvailable { hideCancelBottomSheet() }
            } else {
                if (isCancelStateVisible.value) {
                    hideCancelBottomSheet()
                }
                cancelSwap()
            }
        }

    private fun onCancelSwapClick() =
        viewModelScope.launch {
            if (isCancelStateVisible.value) {
                hideCancelBottomSheet()
            }
            cancelSwap()
        }

    private fun onDismissCancelClick() =
        viewModelScope.launch {
            isCancelStateVisible.update { false }
            navigateToSwapQuoteIfAvailable { hideCancelBottomSheet() }
        }

    @Suppress("MagicNumber")
    private suspend fun hideCancelBottomSheet() {
        isCancelStateVisible.update { false }
        delay(350.milliseconds)
    }

    private fun onSwapCurrencyTypeClick(newTextFieldAmount: BigDecimal?) {
        val value =
            newTextFieldAmount
                ?.let { stringResByDynamicNumber(it, includeGroupingSeparator = false) }
                ?: stringRes("")
        internalState.update {
            it.copy(
                amountTextState =
                    NumberTextFieldInnerState(
                        innerTextFieldState = InnerTextFieldState(value = value, selection = TextSelection.End),
                        amount = newTextFieldAmount,
                        lastValidAmount = newTextFieldAmount
                    ),
                currencyType =
                    when (it.currencyType) {
                        CurrencyType.TOKEN -> CurrencyType.FIAT
                        CurrencyType.FIAT -> CurrencyType.TOKEN
                    }
            )
        }
    }

    /** A real edit (not a selection or focus change) ends the Max intent and hides the Max feedback. */
    private fun onTextFieldChange(new: NumberTextFieldInnerState) {
        val old = internalState.value.amountTextState
        val isEdit = old.amount != new.amount || old.innerTextFieldState.value != new.innerTextFieldState.value
        if (isEdit) endMaxIntent()
        internalState.update { it.copy(amountTextState = new) }
    }

    /** Gates the quote request behind the refund warning, on the direction snapshotted at tap time. */
    private fun onRequestSwapQuoteClick(amount: BigDecimal, fiatAmount: BigDecimal?, address: String) =
        viewModelScope.launch {
            maxAmountRequester.cancel()
            val asset = internalState.value.swapAsset ?: return@launch
            val slippage = internalState.value.slippage
            val direction = internalState.value.swapDirection.coerceFor(isSelectedAccountSpendable.value)
            if (!navigateToSwapRefundWarning(fiatAmount = fiatAmount, mode = direction.toSwapMode())) {
                return@launch
            }
            internalState.update { it.copy(isRequestingQuote = true) }
            val tooManyNotes =
                when (direction) {
                    SWAP_FROM_ZEC -> {
                        requestSwapQuote.requestExactInput(
                            amount = amount,
                            address = address,
                            selectedAsset = asset,
                            slippage = slippage,
                            canNavigateToSwapQuote = { !isCancelStateVisible.value }
                        )
                    }

                    SWAP_INTO_ZEC -> {
                        requestSwapQuote.requestFlexInputIntoZec(
                            amount = amount,
                            refundAddress = address,
                            selectedAsset = asset,
                            slippage = slippage,
                            canNavigateToSwapQuote = { !isCancelStateVisible.value }
                        )
                    }
                }
            internalState.update { it.copy(isRequestingQuote = false) }
            if (tooManyNotes == TooManyNotesOutcome.OK) onTooManyNotesOk()
        }

    /**
     * The too-many-notes destination was answered with OK (pacu decision D4): clear the amount (which also ends the
     * Max intent) and focus the field through the one Max focus path.
     */
    private fun onTooManyNotesOk() {
        clearAmount()
        maxFeedback.requestAmountFocus()
    }

    private fun onSwapInfoClick() = navigateToSwapInfo()

    private fun onAddressChange(new: String) =
        internalState.update { it.copy(selectedContact = null, addressText = new) }

    private fun onSwapAssetPickerClick() =
        viewModelScope.launch {
            val asset =
                navigateToSwapAssetPicker(
                    onlyChainTicker =
                        internalState.value.selectedContact
                            ?.blockchain
                            ?.chainTicker
                )
            if (asset != null) {
                endMaxIntent()
                internalState.update { it.copy(swapAsset = asset) }
            }
        }
}

/**
 * A view-only account holds no spend authority ([isSpendable] false), so it may only swap into ZEC;
 * any other direction is reset to [SWAP_INTO_ZEC]. An account that has not loaded yet (null) keeps
 * the current direction.
 */
private fun SwapDirection.coerceFor(isSpendable: Boolean?): SwapDirection =
    if (isSpendable == false) SWAP_INTO_ZEC else this

internal enum class CurrencyType { TOKEN, FIAT }

internal interface InternalState {
    val account: WalletAccount?
    val swapAsset: SwapAsset?
    val currencyType: CurrencyType
    val amountTextState: NumberTextFieldInnerState
    val addressText: String
    val slippage: BigDecimal
    val swapAssets: SwapAssetsData
    val isRequestingQuote: Boolean
    val selectedContact: EnhancedABContact?
    val swapDirection: SwapDirection
    val isEphemeralAddressLocked: Boolean

    /** The one-line "Max isn't available" error; cleared by a Max retap, an amount edit or a change. */
    val maxError: MaxFeedback.Error?

    /** The fees-and-slippage hint, shown from a successful Max fill until the amount, direction or asset changes. */
    val maxHint: MaxFeedback.Hint?

    /**
     * Delegates to the shared [co.electriccoin.zcash.ui.common.model.canSpend] primitive — the same
     * one the pre-quote check in
     * [co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase.requestExactInput] validates
     * against. Re-evaluated whenever the selected account emits a new balance. Null while the
     * balance has not loaded yet.
     */
    fun canSpend(amount: Zatoshi): Boolean? = account.canSpend(amount)
}

internal data class InternalStateImpl(
    override val account: WalletAccount?,
    override val swapAsset: SwapAsset?,
    override val currencyType: CurrencyType,
    override val amountTextState: NumberTextFieldInnerState,
    override val addressText: String,
    override val slippage: BigDecimal,
    override val swapAssets: SwapAssetsData,
    override val isRequestingQuote: Boolean,
    override val selectedContact: EnhancedABContact?,
    override val swapDirection: SwapDirection,
    override val isEphemeralAddressLocked: Boolean,
    override val maxError: MaxFeedback.Error? = null,
    override val maxHint: MaxFeedback.Hint? = null,
) : InternalState
