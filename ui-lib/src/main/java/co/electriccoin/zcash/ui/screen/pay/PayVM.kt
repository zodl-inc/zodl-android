package co.electriccoin.zcash.ui.screen.pay

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
import co.electriccoin.zcash.ui.common.usecase.GetCuratedSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.IsABContactHintVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.pay.info.PayInfoArgs
import co.electriccoin.zcash.ui.screen.swap.SwapCancelState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.Duration.Companion.milliseconds

@Suppress("TooManyFunctions")
internal class PayVM(
    getCuratedSwapAssetsUseCase: GetCuratedSwapAssetsUseCase,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
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

    private val isCancelStateVisible = MutableStateFlow(false)

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
            onDeleteSelectedContactClick = ::onDeleteSelectedContactClick
        )

    val state =
        combine(
            internalState,
            getCuratedSwapAssetsUseCase.observe(),
            getSelectedWalletAccount.observe(),
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
        viewModelScope.launch {
            val asset = getPreselectedSwapAsset()
            internalState.update { if (it.asset == null) it.withAsset(asset) else it }
        }
    }

    private fun onDeleteSelectedContactClick() = internalState.update { it.copy(selectedABContact = null) }

    private fun onTryAgainClick() = swapRepository.requestRefreshAssets()

    private fun onAddressBookClick() =
        viewModelScope.launch {
            val selected = navigateToSelectSwapRecipient()

            if (selected != null) {
                internalState.update { it.copy(selectedABContact = selected, address = "") }
            }
        }

    private fun onQrCodeScannerClick() =
        viewModelScope.launch {
            val result = navigateToScanAddress()
            if (result != null) {
                navigationRouter.back()
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

    private fun onTextFieldChange(amount: NumberTextFieldInnerState, fiat: NumberTextFieldInnerState) {
        internalState.update { it.copy(amount = amount, fiatAmount = fiat) }
    }

    private fun onRequestSwapQuoteClick(amount: BigDecimal, address: String) =
        viewModelScope.launch {
            val asset = internalState.value.asset ?: return@launch
            val slippage = internalState.value.slippage
            internalState.update { it.copy(isRequestingQuote = true) }
            requestSwapQuote.requestExactOutput(
                amount = amount,
                address = address,
                selectedAsset = asset,
                slippage = slippage,
                canNavigateToSwapQuote = { !isCancelStateVisible.value }
            )
            internalState.update { it.copy(isRequestingQuote = false) }
        }

    private fun onInfoClick() = navigationRouter.forward(PayInfoArgs)

    private fun onAddressChange(new: String) =
        internalState.update { it.copy(selectedABContact = null, address = new) }

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
                internalState.update { it.withAsset(asset) }
            }
        }

    /**
     * Sets the selected asset and atomically recomputes the fiat amount inner state in a single
     * update so the displayed fiat value always reflects the current asset's USD price — without an
     * observer feeding back into this same flow.
     */
    private fun InternalStateImpl.withAsset(asset: SwapAsset?): InternalStateImpl =
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

    val totalSpendableBalance: Zatoshi
        get() = account?.spendableShieldedBalance ?: Zatoshi(0)
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
    override val isEphemeralAddressLocked: Boolean
) : InternalState
