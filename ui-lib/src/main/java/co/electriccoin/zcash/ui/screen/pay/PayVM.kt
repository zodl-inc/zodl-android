package co.electriccoin.zcash.ui.screen.pay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.ext.toZecExact
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.model.Spendability
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.canSpend
import co.electriccoin.zcash.ui.common.model.floorToScale
import co.electriccoin.zcash.ui.common.model.spendability
import co.electriccoin.zcash.ui.common.repository.DEFAULT_SLIPPAGE
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.GetCuratedSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMaxSendAmountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.IsABContactHintVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.MaxSendTarget
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapRefundWarningUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.TooManyNotesOutcome
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
import co.electriccoin.zcash.ui.screen.pay.info.PayInfoArgs
import co.electriccoin.zcash.ui.screen.swap.SwapCancelState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import kotlin.time.Duration.Companion.milliseconds

@Suppress("TooManyFunctions")
internal class PayVM(
    private val getCuratedSwapAssetsUseCase: GetCuratedSwapAssetsUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val swapRepository: SwapRepository,
    private val cancelSwap: CancelSwapUseCase,
    private val navigationRouter: NavigationRouter,
    private val requestSwapQuote: RequestSwapQuoteUseCase,
    private val navigateToSwapQuoteIfAvailable: NavigateToSwapQuoteIfAvailableUseCase,
    private val exactOutputVMMapper: ExactOutputVMMapper,
    private val navigateToScanAddress: NavigateToScanGenericAddressUseCase,
    private val navigateToSelectSwapRecipient: NavigateToSelectABSwapRecipientUseCase,
    private val isABContactHintVisible: IsABContactHintVisibleUseCase,
    private val getPreselectedSwapAsset: GetPreselectedSwapAssetUseCase,
    private val navigateToSlippage: NavigateToSlippageUseCase,
    private val navigateToSwapAssetPicker: NavigateToSwapAssetPickerUseCase,
    private val navigateToSwapRefundWarning: NavigateToSwapRefundWarningUseCase,
    private val getMaxSendAmount: GetMaxSendAmountUseCase,
) : ViewModel() {
    // VM-owned state. The externally-observed `swapAssets`/`account`/`isABHintVisible` fields are
    // injected by the `state` combine below, so the VM only ever updates the fields it owns.
    private val internalState =
        MutableStateFlow(
            InternalStateImpl(
                address = "",
                isABHintVisible = false,
                selectedABContact = null,
                asset = null,
                amount = NumberTextFieldInnerState(),
                fiatAmount = NumberTextFieldInnerState(),
                slippage = DEFAULT_SLIPPAGE,
                isRequestingQuote = false,
                account = null,
                swapAssets = SwapAssetsData(),
                isEphemeralAddressLocked = false
            )
        )

    /**
     * Eager, so a Max tap can check the balance synchronously and raise the in-flight flag before anything suspends.
     * Cheap: the selected account is already shared eagerly upstream.
     */
    private val selectedAccount =
        getSelectedWalletAccount.observe().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val maxFeedback = MaxFeedbackController(variant = MaxVariant.SEND, onClearAmount = ::clearAmount)

    private val maxAmountRequester =
        MaxAmountRequester(
            scope = viewModelScope,
            getMaxSendAmount = getMaxSendAmount,
            onFailed = maxFeedback::onFailed
        )

    /**
     * The fee hint while the Max intent is active, or the Max failure, which wins. The hint ends with the intent: a
     * manual edit or an asset change.
     */
    private val maxFeedbackState: Flow<MaxFeedback?> =
        combine(maxAmountRequester.activeBaseline, maxFeedback.error) { baseline, error ->
            MaxFeedback.of(hint = MaxFeedback.Hint.takeIf { baseline != null }, error = error)
        }

    private val isCancelStateVisible = MutableStateFlow(false)

    /** The blocking too-many-notes sheet of a failed Max, null while hidden. */
    val maxSheetState: StateFlow<MaxTooManyNotesSheetState?> = maxFeedback.sheet

    /** Emits when the screen should focus the amount field ("Send a smaller amount"). */
    val amountFocusRequests: SharedFlow<Unit> = maxFeedback.focusRequests

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isABHintVisible =
        internalState
            .map { it.address to it.selectedABContact }
            .distinctUntilChanged()
            .flatMapLatest { (address, contact) ->
                isABContactHintVisible.observe(selectedContact = contact, text = address)
            }

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
                                text = stringRes(R.string.swapAndPay_cancelSwap),
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

    // Stable method references — created once and reused across every state emission.
    private val callbacks =
        ExactOutputStateCallbacks(
            onBack = ::onBack,
            onSwapInfoClick = ::onInfoClick,
            onSwapAssetPickerClick = ::onSwapAssetPickerClick,
            onSlippageClick = ::onSlippageClick,
            onRequestSwapQuoteClick = ::onRequestSwapQuoteClick,
            onTryAgainClick = ::onTryAgainClick,
            onAddressChange = ::onAddressChange,
            onTextFieldChange = ::onTextFieldChange,
            onQrCodeScannerClick = ::onQrCodeScannerClick,
            onAddressBookClick = ::onAddressBookClick,
            onDeleteSelectedContactClick = ::onDeleteSelectedContactClick,
            onMaxClick = ::onMaxClick
        )

    val state =
        combine(
            internalState.combine(maxFeedbackState) { state, feedback -> state.copy(maxFeedback = feedback) },
            getCuratedSwapAssetsUseCase.observe(),
            selectedAccount,
            isABHintVisible,
        ) { state, swapAssets, account, isABHintVisible ->
            exactOutputVMMapper.createState(
                internalState =
                    state.copy(
                        swapAssets = swapAssets,
                        account = account,
                        isABHintVisible = isABHintVisible
                    ),
                callbacks = callbacks
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    init {
        viewModelScope.launch { observeMaxRefreshes() }
        viewModelScope.launch {
            val asset = getPreselectedSwapAsset()
            val currentAssets = swapRepository.assets.value
            val resolvedAsset =
                currentAssets.data?.firstOrNull { it.assetId == asset.assetId }
                    ?: asset.takeUnless { currentAssets.data != null && !currentAssets.isLoading }
            internalState.update { if (it.asset == null) it.withAsset(resolvedAsset) else it }
        }
        viewModelScope.launch {
            swapRepository.assets.collect { assets ->
                val catalog = assets.data ?: return@collect
                internalState.update { state ->
                    val selected = state.asset ?: return@update state
                    val refreshed = catalog.firstOrNull { it.assetId == selected.assetId }
                    when {
                        refreshed == selected -> state
                        refreshed != null && selected.usdPrice != null -> state.copy(asset = refreshed)
                        refreshed != null -> state.withAsset(refreshed)
                        !assets.isLoading -> state.withAsset(null)
                        else -> state
                    }
                }
            }
        }
    }

    /**
     * Fills the token amount (and its USD mirror) with everything the account can spend towards its own transparent
     * receiver, as no deposit address exists before the quote. The SDK result (already minus one ZIP-317 marginal
     * fee) becomes the Max intent's baseline; see [observeMaxRefreshes] for how the fill follows later changes.
     * A tap while a request is in flight is ignored by [MaxAmountRequester].
     */
    private fun onMaxClick() {
        val isAvailable =
            selectedAccount.value?.spendability?.allowsPrimaryAction == true &&
                !internalState.value.isRequestingQuote &&
                PayMax.hasUsablePrices(currentZecUsdPrice(), internalState.value.asset?.usdPrice)
        if (!isAvailable) return
        // A retap clears the failure line before the new request can set it again.
        reviewCeiling = null
        maxFeedback.clearError()
        maxAmountRequester.request(
            target = MaxSendTarget.OwnTransparentReceiver,
            spendableAtTap = selectedAccount.value?.spendability?.shieldedSpendableOrNull()
        ) {
            if (!applyMax(it.amount)) {
                maxAmountRequester.clearIntent()
                maxFeedback.onFailed(MaxSendAmountFailure.OTHER)
            }
        }
    }

    /**
     * Quote-linked Max: while the intent is active, every price refresh (the token's or ZEC's USD price, which is what
     * the Pay form shows before a quote exists) and every change of the shielded spendable balance recomputes the fill
     * with [MaxFillPolicy.refresh]. It can fall and rise back but never goes above the baseline, so funds that became
     * spendable after the tap are not picked up. The baseline is already net of the fee and the buffer, so what is
     * spendable now is passed net of them too ([maxCap]).
     *
     * Quote fields: a CrossPay [co.electriccoin.zcash.ui.common.model.SwapQuote] only exists after Review, when the
     * form is left, so no quote field (`affiliateFeeZatoshi`, `getTotalFeesZatoshi`, `slippage`) can feed the form;
     * the provider fee that the quote adds is handled at Review ([reviseMaxAmount]). The ZEC side of the exact-output
     * swap is the transparent deposit, whose network fee the baseline already reserves, and slippage only moves the
     * received side. [adjust] is therefore the identity.
     *
     * A refresh that leaves nothing to fill (nothing spendable now, locked or only pending) ends the intent: the
     * fields are cleared and the Max error line is shown, so funds that arrive later are not picked up (D1). While
     * balances are undetermined nothing changes.
     */
    private suspend fun observeMaxRefreshes() {
        combine(
            maxAmountRequester.activeBaseline,
            maxAmountRequester.activeReserve,
            selectedAccount.map { it?.spendability }.distinctUntilChanged(),
            getCuratedSwapAssetsUseCase.observe().map { it.zecAsset?.usdPrice }.distinctUntilChanged(),
            internalState.map { it.asset?.usdPrice to it.isRequestingQuote }.distinctUntilChanged()
        ) { baseline, reserve, spendability, _, _ -> Triple(baseline, reserve, spendability) }
            .collect { (baseline, reserve, spendability) ->
                if (baseline == null) reviewCeiling = null
                if (baseline != null && reserve != null && !internalState.value.isRequestingQuote) {
                    refreshMaxFill(baseline, reserve, spendability)
                }
            }
    }

    private fun refreshMaxFill(baseline: Zatoshi, reserve: Zatoshi, spendability: Spendability?) {
        val cap = maxCap(baseline, reserve, spendability?.shieldedSpendableOrNull() ?: return)
        val fill = reviewCeiling?.let { minOf(cap, it) } ?: cap
        if (fill.value == 0L) {
            clearAmount()
            maxFeedback.onFailed(MaxSendAmountFailure.OTHER)
        } else if (internalState.value.amount.amount != tokenAmountFor(fill)) {
            applyMax(fill)
        }
    }

    /**
     * What the Max fill may be now: `MaxFillPolicy.refresh(baseline, netSpendable, identity)` with the spendable
     * balance net of the reserve captured at the tap (the real fee of the tap-time proposal plus the buffer).
     */
    private fun maxCap(baseline: Zatoshi, reserve: Zatoshi, spendable: Zatoshi): Zatoshi =
        MaxFillPolicy.refresh(baseline, MaxFillPolicy.netSpendable(spendable, reserve), adjust = ::adjustToQuote)

    /** See [observeMaxRefreshes]: no quote exists on the form, and the baseline already reserves the fee. */
    private fun adjustToQuote(candidate: Zatoshi): Zatoshi = candidate

    /**
     * The ZEC fill a Review revised the Max amount down to, held until the intent ends so the refreshes do not put the
     * larger fill back. Null when the Review needed no revision.
     */
    @Volatile
    private var reviewCeiling: Zatoshi? = null

    /**
     * What the first quote callback ([reviseMaxAmount]) hands to the second ([isRevisedQuoteWithinCap]) within one
     * Review; each Review owns its own value, so nothing is left on the view model.
     */
    private sealed interface MaxRevision {
        /** The quote was kept as is: no Max intent, it fits, or it cannot be scaled. */
        data object None : MaxRevision

        /** The amount was scaled down to [token]; the re-quote must fit in [cap]. */
        data class Revised(
            val token: BigDecimal,
            val cap: Zatoshi
        ) : MaxRevision
    }

    /**
     * Review of a Max fill (the quote only exists now): the quote's `amountIn` includes the provider fee, so the fill
     * the form showed can need more ZEC than is spendable and the proposal would fail. If `amountIn` is above the
     * [maxCap], the token amount is sized by [MaxQuoteSizing] to land [MaxQuoteSizing]'s margin below the cap, using
     * the quotes of this Review so far ([history]). The use case then requests the quote again, up to
     * [MaxQuoteSizing.MAX_REVISIONS] times; [isRevisedQuoteWithinCap] checks the last one.
     *
     * The ceiling the refreshes honor afterwards is the ZEC equivalent of the revised token amount as the form now
     * shows it ([PayMax.zecEquivalentZatoshi]), so a later refresh never puts the amount above it.
     *
     * @param requested the token amount the quote was requested for
     * @return [MaxRevision.Revised] with the token amount to quote instead, or [MaxRevision.None] to keep the quote
     */
    private fun reviseMaxAmount(
        quote: SwapQuote,
        requested: BigDecimal,
        history: MutableList<MaxQuotePoint>
    ): MaxRevision {
        val baseline = maxAmountRequester.activeBaseline.value
        val reserve = maxAmountRequester.activeReserve.value
        val spendable = selectedAccount.value?.spendability?.shieldedSpendableOrNull()
        val cap =
            if (baseline == null || reserve == null || spendable == null) {
                null
            } else {
                maxCap(baseline, reserve, spendable)
            }
        val capDecimal = cap?.let { BigDecimal(it.value) }
        logQuoteCost(quote, capDecimal, history.size + 1)
        return if (cap == null || capDecimal == null || quote.amountIn <= capDecimal) {
            MaxRevision.None
        } else {
            history += MaxQuotePoint(token = requested, zecIn = quote.amountIn)
            val token = MaxQuoteSizing.nextToken(history, capDecimal)
            val ceilingValue = token?.let { zecEquivalentOf(it) }
            if (token != null && ceilingValue != null && applyToken(token)) {
                reviewCeiling = Zatoshi(minOf(ceilingValue, cap.value))
                MaxRevision.Revised(token, cap)
            } else {
                MaxRevision.None
            }
        }
    }

    /** Only the cost relative to the cap and the quote number are logged: no amount, address or asset. */
    private fun logQuoteCost(quote: SwapQuote, cap: BigDecimal?, number: Int) {
        if (cap == null || cap <= BigDecimal.ZERO) return
        Twig.info {
            "Pay Max quote $number: cost/cap " +
                quote.amountIn.divide(cap, COST_LOG_SCALE, java.math.RoundingMode.HALF_UP).toPlainString()
        }
    }

    /**
     * The check on the last re-quote after [reviseMaxAmount]: a provider fee with a fixed component, or a price move,
     * can leave the revised quote's `amountIn` above the cap again. Then the Review stops here with the Max failure
     * line and no proposal is created; the Max intent ends, and the user taps Max again.
     */
    private fun isRevisedQuoteWithinCap(quote: SwapQuote, revision: MaxRevision): Boolean {
        val withinCap = revision !is MaxRevision.Revised || quote.amountIn <= BigDecimal(revision.cap.value)
        if (!withinCap) {
            maxAmountRequester.clearIntent()
            maxFeedback.onFailed(MaxSendAmountFailure.OTHER)
        }
        return withinCap
    }

    /** The zatoshi value of [token] at the current prices, or null if unusable or zero. */
    private fun zecEquivalentOf(token: BigDecimal): Long? =
        PayMax
            .zecEquivalentZatoshi(token, currentZecUsdPrice(), internalState.value.asset?.usdPrice)
            ?.takeIf { it > 0 }

    private fun tokenAmountFor(zatoshi: Zatoshi): BigDecimal? =
        PayMax.tokenAmount(zatoshi.toZecExact(), currentZecUsdPrice(), internalState.value.asset?.usdPrice)

    /** The user changed the amount or the asset by hand: the failure line and the Max intent end. */
    private fun onFillEditedByUser() {
        maxFeedback.clearError()
        // A request still in flight must not write its fill over what the user just typed
        maxAmountRequester.cancel()
        maxAmountRequester.clearIntent()
    }

    /** The sheet's OK clears both amounts before the screen focuses the token amount. */
    private fun clearAmount() {
        maxAmountRequester.clearIntent()
        internalState.update {
            it.copy(amount = NumberTextFieldInnerState(), fiatAmount = NumberTextFieldInnerState())
        }
    }

    private fun currentZecUsdPrice(): BigDecimal? = getCuratedSwapAssetsUseCase().zecAsset?.usdPrice

    /** Prices are read after the proposal returns, so a price refresh while it ran is honored. */
    private fun applyMax(maxZatoshi: Zatoshi): Boolean {
        val token =
            PayMax.tokenAmount(
                maxZec = maxZatoshi.toZecExact(),
                zecUsdPrice = currentZecUsdPrice(),
                tokenUsdPrice = internalState.value.asset?.usdPrice
            ) ?: return false
        return applyToken(token)
    }

    /** Writes [token] into the token amount field and derives its USD mirror, exactly like typing it. */
    private fun applyToken(token: BigDecimal): Boolean {
        val asset = internalState.value.asset ?: return false
        val tokenState =
            NumberTextFieldInnerState(
                innerTextFieldState =
                    InnerTextFieldState(
                        value = stringResByDynamicNumber(token, includeGroupingSeparator = false),
                        selection = TextSelection.End
                    ),
                amount = token,
                lastValidAmount = token
            )
        internalState.update {
            it.copy(
                amount = tokenState,
                fiatAmount =
                    exactOutputVMMapper.createFiatAmountInnerState(
                        amountInnerState = tokenState,
                        fiatInnerState = it.fiatAmount,
                        asset = asset
                    )
            )
        }
        return true
    }

    private fun onDeleteSelectedContactClick() {
        maxFeedback.clearError()
        internalState.update { it.copy(selectedABContact = null) }
    }

    private fun onTryAgainClick() = swapRepository.requestRefreshAssets()

    private fun onAddressBookClick() =
        viewModelScope.launch {
            val selected = navigateToSelectSwapRecipient()

            if (selected != null) {
                maxFeedback.clearError()
                internalState.update { it.copy(selectedABContact = selected, address = "") }
            }
        }

    private fun onQrCodeScannerClick() =
        viewModelScope.launch {
            val result = navigateToScanAddress()
            if (result != null) {
                navigationRouter.back()
                maxFeedback.clearError()
                if (result.amount != null) maxAmountRequester.clearIntent()
                internalState.update {
                    it.copy(
                        selectedABContact = null,
                        address = result.address,
                        amount =
                            if (result.amount != null) {
                                NumberTextFieldInnerState.fromAmount(result.amount)
                            } else {
                                it.amount
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
                    mode = SwapMode.EXACT_OUTPUT
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

    /** A real edit (not a selection or focus change) ends the Max intent and hides the Max feedback. */
    private fun onTextFieldChange(amount: NumberTextFieldInnerState, fiat: NumberTextFieldInnerState) {
        val old = internalState.value
        val isEdit =
            old.amount.amount != amount.amount ||
                old.amount.innerTextFieldState.value != amount.innerTextFieldState.value ||
                old.fiatAmount.amount != fiat.amount ||
                old.fiatAmount.innerTextFieldState.value != fiat.innerTextFieldState.value
        if (isEdit) onFillEditedByUser()
        internalState.update { it.copy(amount = amount, fiatAmount = fiat) }
    }

    /** Gates the quote request behind the refund warning before the CTA starts spinning. */
    private fun onRequestSwapQuoteClick(amount: BigDecimal, fiatAmount: BigDecimal?, address: String) =
        viewModelScope.launch {
            // Leaving the form must not let a late Max rewrite the amount that is being quoted.
            maxAmountRequester.cancel()
            val asset = internalState.value.asset ?: return@launch
            val slippage = internalState.value.slippage
            if (!navigateToSwapRefundWarning(fiatAmount = fiatAmount, mode = SwapMode.EXACT_OUTPUT)) {
                return@launch
            }
            var revision: MaxRevision = MaxRevision.None
            val quoteHistory = mutableListOf<MaxQuotePoint>()
            var requested = amount
            internalState.update { it.copy(isRequestingQuote = true) }
            val tooManyNotes =
                requestSwapQuote.requestExactOutput(
                    amount = amount,
                    address = address,
                    selectedAsset = asset,
                    slippage = slippage,
                    canNavigateToSwapQuote = { !isCancelStateVisible.value },
                    reviseAmount = { quote ->
                        revision = reviseMaxAmount(quote, requested, quoteHistory)
                        (revision as? MaxRevision.Revised)?.token?.also { requested = it }
                    },
                    isRevisedQuoteAcceptable = { quote -> isRevisedQuoteWithinCap(quote, revision) },
                    maxRevisions = MaxQuoteSizing.MAX_REVISIONS
                )
            internalState.update { it.copy(isRequestingQuote = false) }
            if (tooManyNotes == TooManyNotesOutcome.OK) onTooManyNotesOk()
        }

    /**
     * The too-many-notes destination was answered with OK (pacu decision D4): clear both amounts (which also ends the
     * Max intent) and focus the field through the one Max focus path.
     */
    private fun onTooManyNotesOk() {
        clearAmount()
        maxFeedback.requestAmountFocus()
    }

    private fun onInfoClick() = navigationRouter.forward(PayInfoArgs)

    private fun onAddressChange(new: String) {
        maxFeedback.clearError()
        internalState.update { it.copy(selectedABContact = null, address = new) }
    }

    private fun onSwapAssetPickerClick() =
        viewModelScope.launch {
            val asset =
                navigateToSwapAssetPicker(
                    onlyChainTicker =
                        internalState.value.selectedABContact
                            ?.blockchain
                            ?.chainTicker
                )
            if (asset != null) {
                onFillEditedByUser()
                internalState.update { it.withAsset(asset) }
            }
        }

    /**
     * Sets the selected asset and atomically recomputes the fiat amount inner state in a single
     * update so the displayed fiat value always reflects the current asset's USD price — without an
     * observer feeding back into this same flow.
     */
    private fun InternalStateImpl.withAsset(asset: SwapAsset?): InternalStateImpl =
        if (amount.amount == null && fiatAmount.amount != null && asset?.usdPrice != null) {
            copy(
                asset = asset,
                amount =
                    exactOutputVMMapper.createAmountInnerState(
                        fiatInnerState = fiatAmount,
                        amountInnerState = amount,
                        asset = asset
                    )
            )
        } else {
            copy(
                asset = asset,
                fiatAmount =
                    exactOutputVMMapper.createFiatAmountInnerState(
                        amountInnerState = amount,
                        fiatInnerState = fiatAmount,
                        asset = asset
                    )
            )
        }
}

internal interface InternalState {
    val address: String
    val isABHintVisible: Boolean
    val selectedABContact: EnhancedABContact?
    val asset: SwapAsset?
    val amount: NumberTextFieldInnerState
    val fiatAmount: NumberTextFieldInnerState
    val slippage: BigDecimal
    val isRequestingQuote: Boolean
    val account: WalletAccount?
    val swapAssets: SwapAssetsData
    val isEphemeralAddressLocked: Boolean

    /** The one line above the main button (fee hint or Max failure), null when there is none. */
    val maxFeedback: MaxFeedback?

    /**
     * Delegates to the shared [co.electriccoin.zcash.ui.common.model.canSpend] primitive.
     * Re-evaluated whenever the selected account emits a new balance. Null while the balance has
     * not loaded yet.
     */
    fun canSpend(amount: Zatoshi): Boolean? = account.canSpend(amount)
}

internal data class InternalStateImpl(
    override val address: String,
    override val isABHintVisible: Boolean,
    override val selectedABContact: EnhancedABContact?,
    override val asset: SwapAsset?,
    override val amount: NumberTextFieldInnerState,
    override val fiatAmount: NumberTextFieldInnerState,
    override val slippage: BigDecimal,
    override val isRequestingQuote: Boolean,
    override val account: WalletAccount?,
    override val swapAssets: SwapAssetsData,
    override val isEphemeralAddressLocked: Boolean,
    override val maxFeedback: MaxFeedback? = null,
) : InternalState

/** Decimals of the cost/cap ratio in the Max quote log. */
private const val COST_LOG_SCALE = 4
