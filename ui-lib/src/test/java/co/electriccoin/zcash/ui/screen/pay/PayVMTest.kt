package co.electriccoin.zcash.ui.screen.pay

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.IsABContactHintVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.ScanResult
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [PayVM] owns the selected asset + slippage. Selecting an asset atomically recomputes the fiat
 * amount in a single state update (no observer feedback loop), the slippage picker round-trips, the
 * exact-output quote is pinned to the asset + slippage at click time, and the cancel-confirmation
 * flow (back while requesting, then confirm/dismiss) drives the cancel sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PayVMTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun assetPickerResultRecomputesFiatExactlyOnce() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
            val sentinelFiat = NumberTextFieldInnerState.fromAmount(BigDecimal("123"))
            val harness = harness(assetResult = asset, fiatRecompute = sentinelFiat)
            harness.collectState(this)

            harness.onSwapAssetPickerClick()

            assertEquals("eth", harness.capturedState.asset?.tokenTicker)
            assertEquals(sentinelFiat, harness.capturedState.fiatAmount)
            // Recompute happens once for the single asset change — proves there is no feedback loop.
            verify(exactly = 1) { harness.mapper.createFiatAmountInnerState(any(), any(), asset) }
        }

    @Test
    fun slippagePickerResultAppliedToState() =
        runTest {
            val harness = harness(slippageResult = BigDecimal("7"))
            harness.collectState(this)

            harness.onSlippageClick(null)

            assertEquals(BigDecimal("7"), harness.capturedState.slippage)
            coVerify { harness.navigateToSlippage(BigDecimal("2"), null, SwapMode.EXACT_OUTPUT) }
        }

    @Test
    fun requestQuotePinsSelectedAssetAndSlippage() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "btc")
            val harness = harness(preselect = asset)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), "destination-address")

            coVerify(exactly = 1) {
                harness.requestSwapQuote.requestExactOutput(
                    amount = BigDecimal("1"),
                    address = "destination-address",
                    selectedAsset = asset,
                    slippage = BigDecimal("2"),
                    canNavigateToSwapQuote = any()
                )
            }
        }

    // region quote-request guards

    @Test
    fun requestQuoteIgnoredWhenNoAssetSelected() =
        runTest {
            val harness = harness(preselect = null)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), "destination-address")

            assertNull(harness.capturedState.asset)
            coVerify(exactly = 0) {
                harness.requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun requestingQuoteTogglesRequestingFlag() =
        runTest {
            val harness = harness()
            harness.collectState(this)
            val gate = harness.makeQuoteHang()

            harness.onRequestSwapQuoteClick(BigDecimal("1"), "destination-address")
            assertEquals(true, harness.capturedState.isRequestingQuote)

            gate.complete(Unit)
            assertEquals(false, harness.capturedState.isRequestingQuote)
        }

    // endregion

    // region cancel / back flow

    @Test
    fun backCancelsSwapWhenNoQuotePending() =
        runTest {
            val harness = harness()
            harness.collectState(this)

            harness.onBack()

            assertNull(harness.vm.cancelState.value)
            coVerify(exactly = 1) { harness.cancelSwap() }
        }

    @Test
    fun backWhileRequestingShowsCancelConfirmation() =
        runTest {
            val harness = harness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "destination-address")

            harness.onBack()

            assertNotNull(harness.vm.cancelState.value)
        }

    @Test
    fun backFromCancelConfirmationChecksForAvailableQuote() =
        runTest {
            val harness = harness()
            harness.collectState(this)
            val gate = harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "destination-address")
            harness.onBack() // requesting -> show cancel confirmation
            gate.complete(Unit) // request finishes -> no longer requesting, sheet still visible

            harness.onBack() // now resolves the cancel confirmation

            assertNull(harness.vm.cancelState.value)
            coVerify { harness.navigateToSwapQuoteIfAvailable(any()) }
        }

    @Test
    fun cancelConfirmationNegativeButtonCancelsSwap() =
        runTest {
            val harness = harness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "destination-address")
            harness.onBack()

            harness.vm.cancelState.value
                ?.negativeButton
                ?.onClick
                ?.invoke()
            mainDispatcher.scheduler.advanceUntilIdle() // let the 350ms dismiss delay elapse

            assertNull(harness.vm.cancelState.value)
            coVerify(exactly = 1) { harness.cancelSwap() }
        }

    @Test
    fun cancelConfirmationPositiveButtonChecksForQuote() =
        runTest {
            val harness = harness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "destination-address")
            harness.onBack()

            harness.vm.cancelState.value
                ?.positiveButton
                ?.onClick
                ?.invoke()

            assertNull(harness.vm.cancelState.value)
            coVerify { harness.navigateToSwapQuoteIfAvailable(any()) }
        }

    // endregion

    // region input editing

    @Test
    fun scanResultAppliesAddressAndAmountAndClearsContact() =
        runTest {
            val harness =
                harness(
                    recipient = contactOnChain("btc"),
                    scanResult = ScanResult(address = "scanned-address", amount = BigDecimal("2.5"))
                )
            harness.collectState(this)
            harness.onAddressBookClick() // select a contact first, to prove the scan clears it

            harness.onQrCodeScannerClick()

            assertEquals("scanned-address", harness.capturedState.address)
            assertEquals(0, BigDecimal("2.5").compareTo(harness.capturedState.amount.amount))
            assertNull(harness.capturedState.selectedABContact)
            verify { harness.navigationRouter.back() }
        }

    @Test
    fun addressBookSelectionSetsContactAndClearsAddress() =
        runTest {
            val harness = harness(recipient = contactOnChain("btc"))
            harness.collectState(this)

            harness.onAddressChange("typed-before-picking")
            harness.onAddressBookClick()

            assertNotNull(harness.capturedState.selectedABContact)
            assertEquals("", harness.capturedState.address)
        }

    @Test
    fun addressChangeClearsSelectedContact() =
        runTest {
            val harness = harness(recipient = contactOnChain("btc"))
            harness.collectState(this)
            harness.onAddressBookClick()
            assertNotNull(harness.capturedState.selectedABContact)

            harness.onAddressChange("zs1-typed-address")

            assertEquals("zs1-typed-address", harness.capturedState.address)
            assertNull(harness.capturedState.selectedABContact)
        }

    @Test
    fun assetPickerIsFilteredBySelectedContactChain() =
        runTest {
            val harness = harness(recipient = contactOnChain("eth"))
            harness.collectState(this)
            harness.onAddressBookClick()

            harness.onSwapAssetPickerClick()

            coVerify { harness.navigateToSwapAssetPicker(onlyChainTicker = "eth") }
        }

    // endregion

    private fun contactOnChain(chainTicker: String): EnhancedABContact =
        mockk(relaxed = true) { every { blockchain } returns SwapAssetTestFixture.blockchain(chainTicker) }

    @Suppress("LongMethod")
    private fun harness(
        preselect: SwapAsset? = SwapAssetTestFixture.asset(),
        slippageResult: BigDecimal? = null,
        assetResult: SwapAsset? = null,
        fiatRecompute: NumberTextFieldInnerState = NumberTextFieldInnerState(),
        recipient: EnhancedABContact? = null,
        scanResult: ScanResult? = null,
        assets: SwapAssetsData = SwapAssetTestFixture.assetsData()
    ): Harness {
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
        val requestSwapQuote = mockk<RequestSwapQuoteUseCase>(relaxed = true)
        val cancelSwap = mockk<CancelSwapUseCase>(relaxed = true)
        val navigateToSwapQuoteIfAvailable = mockk<NavigateToSwapQuoteIfAvailableUseCase>(relaxed = true)
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)
        val getSwapAssets =
            mockk<GetSwapAssetsUseCase> { every { observe() } returns MutableStateFlow(assets) }
        val getSelectedWalletAccount =
            mockk<GetSelectedWalletAccountUseCase> {
                every { observe() } returns MutableStateFlow<WalletAccount?>(null)
            }
        val isABContactHintVisible =
            mockk<IsABContactHintVisibleUseCase> { every { observe(any(), any()) } returns flowOf(false) }
        val mapper =
            mockk<ExactOutputVMMapper> {
                every { createFiatAmountInnerState(any(), any(), any()) } returns fiatRecompute
            }

        val harness =
            Harness(
                navigateToSlippage = navigateToSlippage,
                navigateToSwapAssetPicker = navigateToSwapAssetPicker,
                requestSwapQuote = requestSwapQuote,
                mapper = mapper,
                cancelSwap = cancelSwap,
                navigateToSwapQuoteIfAvailable = navigateToSwapQuoteIfAvailable,
                navigationRouter = navigationRouter
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
            harness.onQrCodeScannerClick = callbacks.onQrCodeScannerClick
            harness.onAddressBookClick = callbacks.onAddressBookClick
            mockk()
        }

        harness.vm =
            PayVM(
                getSwapAssetsUseCase = getSwapAssets,
                getSelectedWalletAccount = getSelectedWalletAccount,
                swapRepository = mockk<SwapRepository>(relaxed = true),
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
            )
        return harness
    }

    private class Harness(
        val navigateToSlippage: NavigateToSlippageUseCase,
        val navigateToSwapAssetPicker: NavigateToSwapAssetPickerUseCase,
        val requestSwapQuote: RequestSwapQuoteUseCase,
        val mapper: ExactOutputVMMapper,
        val cancelSwap: CancelSwapUseCase,
        val navigateToSwapQuoteIfAvailable: NavigateToSwapQuoteIfAvailableUseCase,
        val navigationRouter: NavigationRouter,
    ) {
        lateinit var vm: PayVM
        lateinit var capturedState: InternalState
        var onBack: () -> Unit = {}
        var onSlippageClick: (BigDecimal?) -> Unit = {}
        var onSwapAssetPickerClick: () -> Unit = {}
        var onAddressBookClick: () -> Unit = {}
        var onAddressChange: (String) -> Unit = {}
        var onQrCodeScannerClick: () -> Unit = {}
        var onRequestSwapQuoteClick: (BigDecimal, String) -> Unit = { _, _ -> }

        /** Makes the next exact-output quote request hang so `isRequestingQuote` stays true. */
        fun makeQuoteHang(): CompletableDeferred<Unit> {
            val gate = CompletableDeferred<Unit>()
            coEvery {
                requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any())
            } coAnswers { gate.await() }
            return gate
        }

        fun collectState(scope: TestScope) {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.state.collect {} }
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.cancelState.collect {} }
        }
    }
}
