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
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.PreselectSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.InnerTextFieldState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.TextSelection
import co.electriccoin.zcash.ui.design.util.combine
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import co.electriccoin.zcash.ui.screen.swap.Mode.SWAP_FROM_ZEC
import co.electriccoin.zcash.ui.screen.swap.Mode.SWAP_INTO_ZEC
import co.electriccoin.zcash.ui.screen.swap.info.SwapRefundAddressInfoArgs
import co.electriccoin.zcash.ui.screen.swap.picker.SwapAssetPickerArgs
import co.electriccoin.zcash.ui.screen.swap.slippage.SwapSlippageArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Suppress("TooManyFunctions")
internal class SwapVM(
    getSlippage: GetSlippageUseCase,
    getSelectedSwapAsset: GetSelectedSwapAssetUseCase,
    getSwapAssetsUseCase: GetSwapAssetsUseCase,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    preselectSwapAsset: PreselectSwapAssetUseCase,
    private val swapRepository: SwapRepository,
    private val navigateToSwapInfo: NavigateToSwapInfoUseCase,
    private val cancelSwap: CancelSwapUseCase,
    private val navigationRouter: NavigationRouter,
    private val requestSwapQuote: RequestSwapQuoteUseCase,
    private val navigateToSwapQuoteIfAvailable: NavigateToSwapQuoteIfAvailableUseCase,
    private val swapVMMapper: SwapVMMapper,
    private val navigateToScanAddress: NavigateToScanGenericAddressUseCase,
    private val navigateToSelectSwapRecipient: NavigateToSelectABSwapRecipientUseCase,
) : ViewModel() {
    private val mode = MutableStateFlow(SWAP_INTO_ZEC)

    private val currencyType: MutableStateFlow<CurrencyType> = MutableStateFlow(CurrencyType.TOKEN)

    private val addressText: MutableStateFlow<String> = MutableStateFlow("")

    private val amountText: MutableStateFlow<NumberTextFieldInnerState> = MutableStateFlow(NumberTextFieldInnerState())

    private val isRequestingQuote = MutableStateFlow(false)

    private val isCancelStateVisible = MutableStateFlow(false)

    private var selectedContact: MutableStateFlow<EnhancedABContact?> = MutableStateFlow(null)

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

    private val innerState =
        combine(
            addressText,
            amountText,
            getSelectedSwapAsset.observe(),
            getSlippage.observe(),
            currencyType,
            getSwapAssetsUseCase.observe(),
            isRequestingQuote,
            selectedContact,
            getSelectedWalletAccount.observe(),
            mode,
            // isEphemeralAddressLocked.observe()
        ) {
            address,
            amount,
            asset,
            slippage,
            currencyType,
            swapAssets,
            isRequestingQuote,
            selectedContact,
            account,
            mode,
            // isEphemeralAddressLocked
            ->
            InternalStateImpl(
                swapAsset = asset,
                currencyType = currencyType,
                amountTextState = amount,
                addressText = address,
                slippage = slippage,
                swapAssets = swapAssets,
                isRequestingQuote = isRequestingQuote,
                selectedContact = selectedContact,
                account = account,
                mode = mode,
                isEphemeralAddressLocked = false
            )
        }

    val state =
        innerState
            .map { innerState ->
                createState(innerState)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    init {
        preselectSwapAsset
            .observe()
            .launchIn(viewModelScope)
    }

    private fun createState(innerState: InternalStateImpl): SwapState =
        swapVMMapper.createState(
            internalState = innerState,
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

    private fun onBalanceButtonClick() {
        // navigationRouter.forward(SpendableBalanceArgs)
    }

    private fun onChangeButtonClick() {
        mode.update {
            when (it) {
                SWAP_FROM_ZEC -> SWAP_INTO_ZEC
                SWAP_INTO_ZEC -> SWAP_FROM_ZEC
            }
        }
    }

    private fun onAddressClick() = navigationRouter.forward(SwapRefundAddressInfoArgs)

    private fun onDeleteSelectedContactClick() = selectedContact.update { null }

    private fun onTryAgainClick() = swapRepository.requestRefreshAssets()

    private fun onAddressBookClick() =
        viewModelScope.launch {
            val selected = navigateToSelectSwapRecipient()

            if (selected != null) {
                selectedContact.update { selected }
                addressText.update { "" }
                preselectChain(selected)
            }
        }

    private fun preselectChain(selected: EnhancedABContact) {
        if (mode.value == SWAP_INTO_ZEC) {
            val selectedChainTicker = selected.blockchain?.chainTicker
            val currentAsset = swapRepository.selectedAsset.value
            val currentChainTicker = currentAsset?.chainTicker

            // Only proceed if chain ticker changed
            if (selectedChainTicker != null &&
                !selectedChainTicker.equals(currentChainTicker, ignoreCase = true)
            ) {
                val swapAssets = swapRepository.assets.value.data
                val matchingAssets =
                    swapAssets
                        ?.filter { asset ->
                            asset.chainTicker.equals(selectedChainTicker, ignoreCase = true)
                        }.orEmpty()

                if (matchingAssets.size == 1) {
                    swapRepository.select(matchingAssets.first())
                } else {
                    swapRepository.select(null)
                }
            }
        }
    }

    private fun onQrCodeScannerClick() =
        viewModelScope.launch {
            val result = navigateToScanAddress()
            if (result != null) {
                navigationRouter.back()
                selectedContact.update { null }
                addressText.update { result.address }
                if (result.amount != null) {
                    amountText.update { NumberTextFieldInnerState.fromAmount(result.amount) }
                }
            }
        }

    private fun onSlippageClick(fiatAmount: BigDecimal?) =
        navigationRouter.forward(
            SwapSlippageArgs(
                fiatAmount = fiatAmount?.toPlainString(),
                mode =
                    when (mode.value) {
                        SWAP_FROM_ZEC -> SwapMode.EXACT_INPUT
                        SWAP_INTO_ZEC -> SwapMode.FLEX_INPUT
                    }
            )
        )

    private fun onBack() =
        viewModelScope.launch {
            if (isRequestingQuote.value) {
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
        amountText.update {
            NumberTextFieldInnerState(
                innerTextFieldState =
                    InnerTextFieldState(
                        value =
                            newTextFieldAmount
                                ?.let { stringResByDynamicNumber(it, includeGroupingSeparator = false) }
                                ?: stringRes(""),
                        selection = TextSelection.End
                    ),
                amount = newTextFieldAmount,
                lastValidAmount = newTextFieldAmount
            )
        }
        currencyType.update {
            when (it) {
                CurrencyType.TOKEN -> CurrencyType.FIAT
                CurrencyType.FIAT -> CurrencyType.TOKEN
            }
        }
    }

    private fun onTextFieldChange(new: NumberTextFieldInnerState) = amountText.update { new }

    private fun onRequestSwapQuoteClick(amount: BigDecimal, address: String) =
        viewModelScope.launch {
            isRequestingQuote.update { true }
            when (mode.value) {
                SWAP_FROM_ZEC -> {
                    requestSwapQuote.requestExactInput(
                        amount = amount,
                        address = address,
                        canNavigateToSwapQuote = { !isCancelStateVisible.value }
                    )
                }

                SWAP_INTO_ZEC -> {
                    requestSwapQuote.requestFlexInputIntoZec(
                        amount = amount,
                        refundAddress = address,
                        canNavigateToSwapQuote = { !isCancelStateVisible.value }
                    )
                }
            }
            isRequestingQuote.update { false }
        }

    private fun onSwapInfoClick() = navigateToSwapInfo()

    private fun onAddressChange(new: String) {
        selectedContact.update { null }
        addressText.update { new }
    }

    private fun onSwapAssetPickerClick() =
        navigationRouter.forward(
            SwapAssetPickerArgs(onlyChainTicker = selectedContact.value?.blockchain?.chainTicker)
        )
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
