package co.electriccoin.zcash.ui.screen.swap

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountUnavailableException
import co.electriccoin.zcash.ui.common.model.LoadedAccountBalances
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.GetCuratedSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMaxSendAmountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapRefundWarningUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.ScanResult
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxTooManyNotesSheetState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.math.BigDecimal

@Suppress("LongMethod")
internal fun swapVmHarness(
    preselect: SwapAsset? = SwapAssetTestFixture.asset(),
    slippageResult: BigDecimal? = null,
    assetResult: SwapAsset? = null,
    recipient: EnhancedABContact? = null,
    scanResult: ScanResult? = null,
    assets: SwapAssetsData = SwapAssetTestFixture.assetsData(),
    refundWarningResult: Boolean = true,
    account: WalletAccount? = mockk(relaxed = true) { every { isSpendable } returns true }
): SwapVmHarness {
    val assetsRef = arrayOf(assets)
    val navigateToSlippage =
        mockk<NavigateToSlippageUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns slippageResult
        }
    val navigateToSwapAssetPicker =
        mockk<NavigateToSwapAssetPickerUseCase> {
            coEvery { this@mockk.invoke(any()) } returns assetResult
        }
    val navigateToSelectSwapRecipient =
        mockk<NavigateToSelectABSwapRecipientUseCase> { coEvery { this@mockk.invoke() } returns recipient }
    val navigateToScanAddress =
        mockk<NavigateToScanGenericAddressUseCase> { coEvery { this@mockk.invoke() } returns scanResult }
    val getPreselectedSwapAsset =
        mockk<GetPreselectedSwapAssetUseCase> {
            if (preselect != null) {
                coEvery { this@mockk.invoke() } returns preselect
            } else {
                // Never resolves -> the VM keeps `swapAsset` null (covers the no-asset-selected path).
                coEvery { this@mockk.invoke() } coAnswers { awaitCancellation() }
            }
        }
    val navigateToSwapRefundWarning =
        mockk<NavigateToSwapRefundWarningUseCase> {
            coEvery { this@mockk.invoke(any(), any()) } returns refundWarningResult
        }
    val requestSwapQuote = mockk<RequestSwapQuoteUseCase>(relaxed = true)
    val cancelSwap = mockk<CancelSwapUseCase>(relaxed = true)
    val navigateToSwapQuoteIfAvailable = mockk<NavigateToSwapQuoteIfAvailableUseCase>(relaxed = true)
    val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    val getSwapAssets =
        mockk<GetCuratedSwapAssetsUseCase> {
            every { observe() } returns MutableStateFlow(assets)
            every { this@mockk() } answers { assetsRef[0] }
        }
    val accountFlow = MutableStateFlow(account)
    val getSelectedWalletAccount =
        mockk<GetSelectedWalletAccountUseCase> {
            every { observe() } returns accountFlow
        }
    val quoteFlow = MutableStateFlow<SwapQuoteData?>(null)
    val mapper = mockk<SwapVMMapper>()
    val swapRepository =
        mockk<SwapRepository>(relaxed = true) {
            every { this@mockk.assets } returns MutableStateFlow(assets)
            every { quote } returns quoteFlow
        }

    val getMaxSendAmount = mockk<GetMaxSendAmountUseCase>()

    val harness =
        SwapVmHarness(
            getMaxSendAmount = getMaxSendAmount,
            accountFlow = accountFlow,
            quoteFlow = quoteFlow,
            assetsRef = assetsRef,
            navigateToSlippage = navigateToSlippage,
            navigateToSwapAssetPicker = navigateToSwapAssetPicker,
            navigateToSwapRefundWarning = navigateToSwapRefundWarning,
            requestSwapQuote = requestSwapQuote,
            cancelSwap = cancelSwap,
            navigateToSwapQuoteIfAvailable = navigateToSwapQuoteIfAvailable,
            navigationRouter = navigationRouter
        )

    // The VM exposes its callbacks only through SwapVMMapper.createState; capture the callbacks
    // object and pull each handler out by name, so the tests can drive the VM the same way the
    // View would — and adding/reordering a callback can't silently misroute the others.
    every { mapper.createState(any(), any()) } answers {
        val callbacks = arg<SwapStateCallbacks>(1)
        harness.capturedState = firstArg()
        harness.onBack = callbacks.onBack
        harness.onSwapAssetPickerClick = callbacks.onSwapAssetPickerClick
        harness.onSwapCurrencyTypeClick = callbacks.onSwapCurrencyTypeClick
        harness.onSlippageClick = callbacks.onSlippageClick
        harness.onRequestSwapQuoteClick = callbacks.onRequestSwapQuoteClick
        harness.onAddressChange = callbacks.onAddressChange
        harness.onQrCodeScannerClick = callbacks.onQrCodeScannerClick
        harness.onAddressBookClick = callbacks.onAddressBookClick
        harness.onChangeButtonClick = callbacks.onChangeButtonClick
        harness.onAddressClick = callbacks.onAddressClick
        harness.onMaxClick = callbacks.onMaxClick
        harness.onTextFieldChange = callbacks.onTextFieldChange
        harness.onSpendableClick = callbacks.onSpendableClick
        mockk()
    }

    harness.vm =
        SwapVM(
            getCuratedSwapAssetsUseCase = getSwapAssets,
            getSelectedWalletAccount = getSelectedWalletAccount,
            getPreselectedSwapAsset = getPreselectedSwapAsset,
            swapRepository = swapRepository,
            navigateToSwapInfo = mockk<NavigateToSwapInfoUseCase>(relaxed = true),
            cancelSwap = cancelSwap,
            navigationRouter = navigationRouter,
            requestSwapQuote = requestSwapQuote,
            navigateToSwapQuoteIfAvailable = navigateToSwapQuoteIfAvailable,
            swapVMMapper = mapper,
            navigateToScanAddress = navigateToScanAddress,
            navigateToSelectSwapRecipient = navigateToSelectSwapRecipient,
            navigateToSlippage = navigateToSlippage,
            navigateToSwapAssetPicker = navigateToSwapAssetPicker,
            navigateToSwapRefundWarning = navigateToSwapRefundWarning,
            getMaxSendAmount = getMaxSendAmount,
        )
    return harness
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class SwapVmHarness(
    val getMaxSendAmount: GetMaxSendAmountUseCase,
    val accountFlow: MutableStateFlow<WalletAccount?>,
    val quoteFlow: MutableStateFlow<SwapQuoteData?>,
    val assetsRef: Array<SwapAssetsData>,
    val navigateToSlippage: NavigateToSlippageUseCase,
    val navigateToSwapAssetPicker: NavigateToSwapAssetPickerUseCase,
    val navigateToSwapRefundWarning: NavigateToSwapRefundWarningUseCase,
    val requestSwapQuote: RequestSwapQuoteUseCase,
    val cancelSwap: CancelSwapUseCase,
    val navigateToSwapQuoteIfAvailable: NavigateToSwapQuoteIfAvailableUseCase,
    val navigationRouter: NavigationRouter,
) {
    lateinit var vm: SwapVM
    lateinit var capturedState: InternalState
    var onBack: () -> Unit = {}
    var onSlippageClick: (BigDecimal?) -> Unit = {}
    var onSwapAssetPickerClick: () -> Unit = {}
    var onSwapCurrencyTypeClick: (BigDecimal?) -> Unit = {}
    var onChangeButtonClick: () -> Unit = {}
    var onAddressBookClick: () -> Unit = {}
    var onAddressChange: (String) -> Unit = {}
    var onQrCodeScannerClick: () -> Unit = {}
    var onAddressClick: () -> Unit = {}
    var onMaxClick: () -> Unit = {}
    var onSpendableClick: () -> Unit = {}
    var onTextFieldChange: (NumberTextFieldInnerState) -> Unit = {}
    var maxSheet: MaxTooManyNotesSheetState? = null
    var focusRequestCount = 0

    /** Whether the VM currently shows the Max failure line. */
    val isMaxBannerVisible: Boolean
        get() = capturedState.maxError != null
    var onRequestSwapQuoteClick: (BigDecimal, BigDecimal?, String) -> Unit = { _, _, _ -> }

    /** Makes the next flex-input quote request hang so `isRequestingQuote` stays true. */
    fun makeQuoteHang(): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        coEvery {
            requestSwapQuote.requestFlexInputIntoZec(any(), any(), any(), any(), any())
        } coAnswers {
            gate.await()
            null
        }
        return gate
    }

    /** Makes the next exact-input (SWAP_FROM_ZEC) quote request hang so `isRequestingQuote` stays true. */
    fun makeExactInputQuoteHang(): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        coEvery {
            requestSwapQuote.requestExactInput(any(), any(), any(), any(), any())
        } coAnswers {
            gate.await()
            null
        }
        return gate
    }

    /** Makes the max use case hang until the returned gate is completed. */
    fun makeMaxHang(amount: Zatoshi): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        coEvery { getMaxSendAmount(any()) } coAnswers {
            gate.await()
            MaxSendAmount(amount = amount, fee = Zatoshi(10_000))
        }
        return gate
    }

    fun clearViewModel() {
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { vm } })[SwapVM::class]
        store.clear()
    }

    fun collectState(scope: TestScope) {
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.state.collect {} }
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.cancelState.collect {} }
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            vm.maxSheetState.collect { maxSheet = it }
        }
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            vm.amountFocusRequests.collect { focusRequestCount++ }
        }
    }
}

internal fun SwapVmHarness.failMax(failure: MaxSendAmountFailure) {
    coEvery { getMaxSendAmount(any()) } throws MaxSendAmountUnavailableException(failure)
}

/** The screen state the real mapper builds for the harness's current VM state, plus overrides. */
internal fun mapped(
    harness: SwapVmHarness,
    maxError: MaxFeedback.Error? = null,
    maxHint: MaxFeedback.Hint? = null
): SwapState {
    val current = harness.capturedState
    val internal =
        InternalStateImpl(
            account = current.account,
            swapAsset = current.swapAsset,
            currencyType = current.currencyType,
            amountTextState = current.amountTextState,
            addressText = current.addressText,
            slippage = current.slippage,
            swapAssets = current.swapAssets,
            isRequestingQuote = current.isRequestingQuote,
            selectedContact = current.selectedContact,
            swapDirection = current.swapDirection,
            isEphemeralAddressLocked = current.isEphemeralAddressLocked,
            maxError = maxError,
            maxHint = maxHint
        )
    // android.icu is not available in plain unit tests
    mockkObject(FiatCurrency.USD)
    every { FiatCurrency.USD.symbol } returns "$"
    return try {
        createMappedState(internal)
    } finally {
        unmockkObject(FiatCurrency.USD)
    }
}

private fun createMappedState(internal: InternalStateImpl): SwapState =
    SwapVMMapper().createState(
        internalState = internal,
        callbacks =
            SwapStateCallbacks(
                onBack = {},
                onSwapInfoClick = {},
                onSwapAssetPickerClick = {},
                onSwapCurrencyTypeClick = {},
                onSlippageClick = {},
                onRequestSwapQuoteClick = { _, _, _ -> },
                onTryAgainClick = {},
                onAddressChange = {},
                onTextFieldChange = {},
                onQrCodeScannerClick = {},
                onAddressBookClick = {},
                onDeleteSelectedContactClick = {},
                onMaxClick = {},
                onSpendableClick = {},
                onChangeButtonClick = {},
                onAddressClick = {},
            )
    )

internal fun nothingAccount(
    shieldable: Boolean,
    pending: Boolean
): WalletAccount =
    mockk(relaxed = true) {
        every { spendableShieldedBalance } returns Zatoshi(0)
        every { pendingShieldedBalance } returns Zatoshi(0)
        every { isShieldingAvailable } returns shieldable
        every { isShieldedPending } returns pending
        every { isSpendable } returns true
        every { loadedBalances } returns
            loadedBalances(
                spendable = 0,
                pending = if (pending) 50 else 0,
                transparent = if (shieldable) 500 else 0
            )
    }

/** A harness already in SWAP_FROM_ZEC mode, with a spendable account and a ZEC price of $30. */
internal fun TestScope.maxHarness(
    max: Zatoshi,
    account: WalletAccount? = spendableAccount(spendable = Zatoshi(500_000_000)),
    assets: SwapAssetsData = SwapAssetTestFixture.assetsData(zecAsset = zecAssetWithPrice(BigDecimal("30"))),
    hang: Boolean = false,
    assetResult: SwapAsset? = null
): SwapVmHarness {
    val harness = swapVmHarness(account = account, assets = assets, assetResult = assetResult)
    harness.collectState(this)
    if (!hang) {
        coEvery { harness.getMaxSendAmount(any()) } returns MaxSendAmount(amount = max, fee = Zatoshi(10_000))
    }
    harness.onChangeButtonClick() // SWAP_INTO_ZEC -> SWAP_FROM_ZEC
    return harness
}

internal fun spendableAccount(spendable: Zatoshi): WalletAccount =
    mockk(relaxed = true) {
        every { spendableShieldedBalance } returns spendable
        every { pendingShieldedBalance } returns Zatoshi(0)
        every { canSpend(any()) } returns true
        every { isSpendable } returns true
        every { loadedBalances } returns loadedBalances(spendable = spendable.value, pending = 0, transparent = 0)
    }

internal fun loadedBalances(
    spendable: Long,
    pending: Long,
    transparent: Long
) = LoadedAccountBalances(
    isAllShielded = transparent == 0L,
    totalBalance = Zatoshi(spendable + pending + transparent),
    totalShieldedBalance = Zatoshi(spendable + pending),
    totalTransparentBalance = Zatoshi(transparent),
    spendableShieldedBalance = Zatoshi(spendable),
    pendingShieldedBalance = Zatoshi(pending),
    isShieldedPending = pending > 0,
    isShieldingAvailable = transparent > 0,
    transparentBalance = Zatoshi(transparent),
)

internal fun zecAssetWithPrice(price: BigDecimal?): SwapAsset =
    mockk(relaxed = true) { every { usdPrice } returns price }
