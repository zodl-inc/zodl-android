package co.electriccoin.zcash.ui.screen.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.DEFAULT_SLIPPAGE
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.InnerTextFieldState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.TextSelection
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import co.electriccoin.zcash.ui.screen.swap.Mode.SWAP_FROM_ZEC
import co.electriccoin.zcash.ui.screen.swap.Mode.SWAP_INTO_ZEC
import co.electriccoin.zcash.ui.screen.swap.info.SwapRefundAddressInfoArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Suppress("TooManyFunctions")
internal class SwapVM(
    getSwapAssetsUseCase: GetSwapAssetsUseCase,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
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
                mode = SWAP_INTO_ZEC,
                isEphemeralAddressLocked = false
            )
        )

    private val isCancelStateVisible = MutableStateFlow(false)

    val cancelState =
        isCancelStateVisible
            .map { isVisible ->
                if (isVisible) {
                    SwapCancelState(
                        icon = imageRes(R.drawable.ic_swap_quote_cancel),
                        title = stringRes(R.string.swap_cancel_title),
                        subtitle = stringRes(R.string.swap_cancel_subtitle),
                        negativeButton =
                            ButtonState(
                                text = stringRes(R.string.swap_quote_cancel_payment),
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
            getSwapAssetsUseCase.observe(),
            getSelectedWalletAccount.observe(),
        ) { state, swapAssets, account ->
            createState(state.copy(swapAssets = swapAssets, account = account))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            val asset = getPreselectedSwapAsset()
            internalState.update { if (it.swapAsset == null) it.copy(swapAsset = asset) else it }
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
            onBalanceButtonClick = ::onBalanceButtonClick,
            onChangeButtonClick = ::onChangeButtonClick,
            onAddressClick = ::onAddressClick
        )

    private fun createState(internalState: InternalStateImpl): SwapState =
        swapVMMapper.createState(internalState = internalState, callbacks = callbacks)

    private fun onBalanceButtonClick() {
        // navigationRouter.forward(SpendableBalanceArgs)
    }

    private fun onChangeButtonClick() {
        internalState.update {
            it.copy(
                mode =
                    when (it.mode) {
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
        if (internalState.value.mode != SWAP_INTO_ZEC) return

        val selectedChainTicker = selected.blockchain?.chainTicker
        val currentChainTicker = internalState.value.swapAsset?.chainTicker

        // Only proceed if the chain ticker changed.
        if (selectedChainTicker != null && !selectedChainTicker.equals(currentChainTicker, ignoreCase = true)) {
            val matchingAssets =
                swapRepository.assets.value.data
                    ?.filter { asset -> asset.chainTicker.equals(selectedChainTicker, ignoreCase = true) }
                    .orEmpty()
            internalState.update { it.copy(swapAsset = matchingAssets.singleOrNull()) }
        }
    }

    private fun onQrCodeScannerClick() =
        viewModelScope.launch {
            val result = navigateToScanAddress()
            if (result != null) {
                navigationRouter.back()
                internalState.update {
                    it.copy(
                        selectedContact = null,
                        addressText = result.address,
                        amountTextState =
                            if (result.amount != null) {
                                NumberTextFieldInnerState.fromAmount(result.amount)
                            } else {
                                it.amountTextState
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
                    mode =
                        when (internalState.value.mode) {
                            SWAP_FROM_ZEC -> SwapMode.EXACT_INPUT
                            SWAP_INTO_ZEC -> SwapMode.FLEX_INPUT
                        }
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
        delay(350)
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

    private fun onTextFieldChange(new: NumberTextFieldInnerState) =
        internalState.update { it.copy(amountTextState = new) }

    private fun onRequestSwapQuoteClick(amount: BigDecimal, address: String) =
        viewModelScope.launch {
            val asset = internalState.value.swapAsset ?: return@launch
            val slippage = internalState.value.slippage
            internalState.update { it.copy(isRequestingQuote = true) }
            when (internalState.value.mode) {
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
                internalState.update { it.copy(swapAsset = asset) }
            }
        }
}

internal enum class CurrencyType { TOKEN, FIAT }

internal enum class Mode { SWAP_FROM_ZEC, SWAP_INTO_ZEC }

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
    val mode: Mode
    val isEphemeralAddressLocked: Boolean

    val totalSpendableBalance: Zatoshi
        get() = account?.spendableShieldedBalance ?: Zatoshi(0)
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
    override val mode: Mode,
    override val isEphemeralAddressLocked: Boolean,
) : InternalState
