package co.electriccoin.zcash.ui.screen.pay

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.GetCuratedSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMaxSendAmountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.IsABContactHintVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapRefundWarningUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.ScanResult
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.math.BigDecimal

@Suppress("LongMethod")
internal fun payVmHarness(
    preselect: SwapAsset? = SwapAssetTestFixture.asset(),
    slippageResult: BigDecimal? = null,
    assetResult: SwapAsset? = null,
    fiatRecompute: NumberTextFieldInnerState = NumberTextFieldInnerState(),
    amountRecompute: NumberTextFieldInnerState = NumberTextFieldInnerState(),
    recipient: EnhancedABContact? = null,
    scanResult: ScanResult? = null,
    assets: SwapAssetsData = SwapAssetTestFixture.assetsData(),
    refundWarningResult: Boolean = true,
    account: WalletAccount? = null,
    maxAmount: MaxSendAmount = MaxSendAmount(Zatoshi(0), Zatoshi(0)),
): PayVmHarness {
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
    val preselectSwapAsset =
        mockk<GetPreselectedSwapAssetUseCase> {
            if (preselect != null) {
                coEvery { this@mockk.invoke() } returns preselect
            } else {
                // Never resolves -> the VM keeps `asset` null (covers the no-asset-selected path).
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
    val curatedAssets = MutableStateFlow(assets)
    val getSwapAssets =
        mockk<GetCuratedSwapAssetsUseCase> {
            every { observe() } returns curatedAssets
            every { this@mockk.invoke() } answers { curatedAssets.value }
        }
    val accountFlow = MutableStateFlow(account)
    val getSelectedWalletAccount =
        mockk<GetSelectedWalletAccountUseCase> {
            every { observe() } returns accountFlow
        }
    val getMaxSendAmount =
        mockk<GetMaxSendAmountUseCase> { coEvery { this@mockk.invoke(any()) } returns maxAmount }
    val isABContactHintVisible =
        mockk<IsABContactHintVisibleUseCase> { every { observe(any(), any()) } returns flowOf(false) }
    val mapper =
        mockk<ExactOutputVMMapper> {
            every { createFiatAmountInnerState(any(), any(), any()) } returns fiatRecompute
            every { createAmountInnerState(any(), any(), any()) } returns amountRecompute
        }
    val repositoryAssets = MutableStateFlow(assets)
    val swapRepository =
        mockk<SwapRepository>(relaxed = true) {
            every { this@mockk.assets } returns repositoryAssets
        }

    val harness =
        PayVmHarness(
            navigateToSlippage = navigateToSlippage,
            navigateToSwapAssetPicker = navigateToSwapAssetPicker,
            navigateToSwapRefundWarning = navigateToSwapRefundWarning,
            requestSwapQuote = requestSwapQuote,
            mapper = mapper,
            cancelSwap = cancelSwap,
            navigateToSwapQuoteIfAvailable = navigateToSwapQuoteIfAvailable,
            navigationRouter = navigationRouter,
            repositoryAssets = repositoryAssets,
            curatedAssets = curatedAssets,
            account = accountFlow,
            getMaxSendAmount = getMaxSendAmount,
        )

    // The VM exposes its callbacks only through ExactOutputVMMapper.createState; capture the
    // callbacks object and pull each handler out by name, so adding/reordering a callback can't
    // silently misroute the others.
    every { mapper.createState(any(), any()) } answers {
        val callbacks = arg<ExactOutputStateCallbacks>(1)
        harness.capturedState = firstArg()
        harness.onBack = callbacks.onBack
        harness.onSwapAssetPickerClick = callbacks.onSwapAssetPickerClick
        harness.onSlippageClick = callbacks.onSlippageClick
        harness.onRequestSwapQuoteClick = callbacks.onRequestSwapQuoteClick
        harness.onAddressChange = callbacks.onAddressChange
        harness.onTextFieldChange = callbacks.onTextFieldChange
        harness.onQrCodeScannerClick = callbacks.onQrCodeScannerClick
        harness.onAddressBookClick = callbacks.onAddressBookClick
        harness.onMaxClick = callbacks.onMaxClick
        mockk()
    }

    harness.vm =
        PayVM(
            getCuratedSwapAssetsUseCase = getSwapAssets,
            getSelectedWalletAccount = getSelectedWalletAccount,
            swapRepository = swapRepository,
            cancelSwap = cancelSwap,
            navigationRouter = navigationRouter,
            requestSwapQuote = requestSwapQuote,
            navigateToSwapQuoteIfAvailable = navigateToSwapQuoteIfAvailable,
            exactOutputVMMapper = mapper,
            navigateToScanAddress = navigateToScanAddress,
            navigateToSelectSwapRecipient = navigateToSelectSwapRecipient,
            isABContactHintVisible = isABContactHintVisible,
            getPreselectedSwapAsset = preselectSwapAsset,
            navigateToSlippage = navigateToSlippage,
            navigateToSwapAssetPicker = navigateToSwapAssetPicker,
            navigateToSwapRefundWarning = navigateToSwapRefundWarning,
            getMaxSendAmount = getMaxSendAmount,
        )
    return harness
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class PayVmHarness(
    val navigateToSlippage: NavigateToSlippageUseCase,
    val navigateToSwapAssetPicker: NavigateToSwapAssetPickerUseCase,
    val navigateToSwapRefundWarning: NavigateToSwapRefundWarningUseCase,
    val requestSwapQuote: RequestSwapQuoteUseCase,
    val mapper: ExactOutputVMMapper,
    val cancelSwap: CancelSwapUseCase,
    val navigateToSwapQuoteIfAvailable: NavigateToSwapQuoteIfAvailableUseCase,
    val navigationRouter: NavigationRouter,
    val repositoryAssets: MutableStateFlow<SwapAssetsData>,
    val curatedAssets: MutableStateFlow<SwapAssetsData>,
    val account: MutableStateFlow<WalletAccount?>,
    val getMaxSendAmount: GetMaxSendAmountUseCase,
) {
    lateinit var vm: PayVM

    /** What the VM's Review revision answered for each stubbed quote (null: keep the quote). */
    val revisedAmounts = mutableListOf<BigDecimal?>()

    /** What the VM's check of the re-quoted amount answered, one entry per revision that was re-quoted. */
    val acceptedRevisions = mutableListOf<Boolean>()

    fun clearViewModel() {
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { vm } })[PayVM::class]
        store.clear()
    }

    var onMaxClick: () -> Unit = {}
    val vmAsset: SwapAsset? get() = capturedState.asset
    lateinit var capturedState: InternalState
    var focusRequestCount = 0

    /** Whether the VM currently shows the Max failure line. */
    val isMaxBannerVisible: Boolean
        get() = capturedState.maxFeedback is MaxFeedback.Error

    /** Whether the VM currently shows the fee hint, i.e. a Max fill is active. */
    val isMaxHintVisible: Boolean
        get() = capturedState.maxFeedback == MaxFeedback.Hint
    var onBack: () -> Unit = {}
    var onSlippageClick: (BigDecimal?) -> Unit = {}
    var onSwapAssetPickerClick: () -> Unit = {}
    var onAddressBookClick: () -> Unit = {}
    var onAddressChange: (String) -> Unit = {}
    var onTextFieldChange: (NumberTextFieldInnerState, NumberTextFieldInnerState) -> Unit = { _, _ -> }
    var onQrCodeScannerClick: () -> Unit = {}
    var onRequestSwapQuoteClick: (BigDecimal, BigDecimal?, String) -> Unit = { _, _, _ -> }

    /** Makes the next exact-output quote request hang so `isRequestingQuote` stays true. */
    fun makeQuoteHang(): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        coEvery {
            requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            gate.await()
            null
        }
        return gate
    }

    fun collectState(scope: TestScope) {
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.state.collect {} }
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.cancelState.collect {} }
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.maxSheetState.collect {} }
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            vm.amountFocusRequests.collect { focusRequestCount++ }
        }
    }
}
