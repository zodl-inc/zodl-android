package co.electriccoin.zcash.ui.screen.swap

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapDirection
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.GetCuratedSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.ScanResult
import co.electriccoin.zcash.ui.screen.swap.info.SwapRefundAddressInfoArgs
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
 * [SwapVM] now owns the selected asset and slippage in its merged state: preselect applies only when
 * nothing is selected, the slippage/asset pickers round-trip their results back into the state, the
 * mode toggles, the quote request is pinned to the asset + slippage selected at click time, and the
 * cancel-confirmation flow (back while requesting, then confirm/dismiss) drives the cancel sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapVMTest {
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
    fun preselectAppliedWhenNothingSelected() =
        runTest {
            val harness = harness(preselect = SwapAssetTestFixture.asset(tokenTicker = "btc"))

            harness.collectState(this)

            assertEquals("btc", harness.capturedState.swapAsset?.tokenTicker)
        }

    @Test
    fun slippagePickerResultAppliedToState() =
        runTest {
            val harness = harness(slippageResult = BigDecimal("7"))
            harness.collectState(this)

            harness.onSlippageClick(null)

            assertEquals(BigDecimal("7"), harness.capturedState.slippage)
            coVerify { harness.navigateToSlippage(BigDecimal("2"), null, any()) }
        }

    @Test
    fun assetPickerResultAppliedToState() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
            val harness = harness(assetResult = asset)
            harness.collectState(this)

            harness.onSwapAssetPickerClick()

            assertEquals("eth", harness.capturedState.swapAsset?.tokenTicker)
        }

    @Test
    fun changeButtonTogglesMode() =
        runTest {
            val harness = harness()
            harness.collectState(this)

            assertEquals(SwapDirection.SWAP_INTO_ZEC, harness.capturedState.swapDirection)

            harness.onChangeButtonClick()

            assertEquals(SwapDirection.SWAP_FROM_ZEC, harness.capturedState.swapDirection)
        }

    @Test
    fun requestQuotePinsSelectedAssetAndSlippage() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "btc")
            val harness = harness(preselect = asset)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), "refund-address")

            // Default mode is SWAP_INTO_ZEC -> flex input, pinned to the preselected asset + default slippage.
            coVerify(exactly = 1) {
                harness.requestSwapQuote.requestFlexInputIntoZec(
                    amount = BigDecimal("1"),
                    refundAddress = "refund-address",
                    selectedAsset = asset,
                    slippage = BigDecimal("2"),
                    canNavigateToSwapQuote = any()
                )
            }
        }

    @Test
    fun swappingFromZecRequestsExactInputQuote() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "btc")
            val harness = harness(preselect = asset)
            harness.collectState(this)

            harness.onChangeButtonClick() // SWAP_INTO_ZEC -> SWAP_FROM_ZEC
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "destination-address")

            coVerify(exactly = 1) {
                harness.requestSwapQuote.requestExactInput(
                    amount = BigDecimal("1"),
                    address = "destination-address",
                    selectedAsset = asset,
                    slippage = BigDecimal("2"),
                    canNavigateToSwapQuote = any()
                )
            }
        }

    @Test
    fun slippageModeFollowsSwapDirection() =
        runTest {
            val harness = harness()
            harness.collectState(this)

            // SWAP_INTO_ZEC opens the slippage screen in flex-input mode.
            harness.onSlippageClick(null)
            coVerify { harness.navigateToSlippage(any(), any(), SwapMode.FLEX_INPUT) }

            // After flipping to SWAP_FROM_ZEC it opens in exact-input mode.
            harness.onChangeButtonClick()
            harness.onSlippageClick(null)
            coVerify { harness.navigateToSlippage(any(), any(), SwapMode.EXACT_INPUT) }
        }

    @Test
    fun pickingAbContactOnNewChainWithSingleAssetSelectsThatAsset() =
        runTest {
            val eth = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
            val harness =
                harness(
                    preselect = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                    assets = SwapAssetTestFixture.assetsData(data = listOf(eth)),
                    recipient = contactOnChain("eth")
                )
            harness.collectState(this)

            harness.onAddressBookClick()

            assertEquals("eth", harness.capturedState.swapAsset?.tokenTicker)
        }

    @Test
    fun pickingAbContactOnNewChainWithMultipleAssetsClearsSelection() =
        runTest {
            val harness =
                harness(
                    preselect = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                    assets =
                        SwapAssetTestFixture.assetsData(
                            data =
                                listOf(
                                    SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth"),
                                    SwapAssetTestFixture.asset(tokenTicker = "usdc", chainTicker = "eth"),
                                )
                        ),
                    recipient = contactOnChain("eth")
                )
            harness.collectState(this)

            harness.onAddressBookClick()

            // The chain has more than one asset, so the VM cannot disambiguate and clears the selection.
            assertNull(harness.capturedState.swapAsset)
        }

    // region quote-request guards

    @Test
    fun requestQuoteIgnoredWhenNoAssetSelected() =
        runTest {
            val harness = harness(preselect = null)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), "refund-address")

            assertNull(harness.capturedState.swapAsset)
            coVerify(exactly = 0) {
                harness.requestSwapQuote.requestFlexInputIntoZec(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun requestingQuoteTogglesRequestingFlag() =
        runTest {
            val harness = harness()
            harness.collectState(this)
            val gate = harness.makeQuoteHang()

            harness.onRequestSwapQuoteClick(BigDecimal("1"), "refund-address")
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
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "refund-address")

            harness.onBack()

            assertNotNull(harness.vm.cancelState.value)
        }

    @Test
    fun backFromCancelConfirmationChecksForAvailableQuote() =
        runTest {
            val harness = harness()
            harness.collectState(this)
            val gate = harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "refund-address")
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
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "refund-address")
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
            harness.onRequestSwapQuoteClick(BigDecimal("1"), "refund-address")
            harness.onBack()

            harness.vm.cancelState.value
                ?.positiveButton
                ?.onClick
                ?.invoke()

            assertNull(harness.vm.cancelState.value)
            coVerify { harness.navigateToSwapQuoteIfAvailable(any()) }
        }

    // endregion

    // region currency type + preselectChain guards + input editing

    @Test
    fun swapCurrencyTypeTogglesTypeAndRebuildsAmount() =
        runTest {
            val harness = harness()
            harness.collectState(this)

            assertEquals(CurrencyType.TOKEN, harness.capturedState.currencyType)

            harness.onSwapCurrencyTypeClick(BigDecimal("5"))

            assertEquals(CurrencyType.FIAT, harness.capturedState.currencyType)
            assertEquals(0, BigDecimal("5").compareTo(harness.capturedState.amountTextState.amount))
        }

    @Test
    fun preselectChainSkippedWhenSwappingFromZec() =
        runTest {
            val harness =
                harness(
                    preselect = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                    assets =
                        SwapAssetTestFixture.assetsData(
                            data = listOf(SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth"))
                        ),
                    recipient = contactOnChain("eth")
                )
            harness.collectState(this)

            harness.onChangeButtonClick() // SWAP_FROM_ZEC: chain preselection does not apply
            harness.onAddressBookClick()

            assertEquals("btc", harness.capturedState.swapAsset?.tokenTicker)
        }

    @Test
    fun preselectChainSkippedWhenChainUnchanged() =
        runTest {
            val harness =
                harness(
                    preselect = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                    recipient = contactOnChain("btc")
                )
            harness.collectState(this)

            harness.onAddressBookClick()

            assertEquals("btc", harness.capturedState.swapAsset?.tokenTicker)
        }

    @Test
    fun preselectChainSkippedWhenContactHasNoChain() =
        runTest {
            val harness =
                harness(
                    preselect = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                    recipient = mockk(relaxed = true) { every { blockchain } returns null }
                )
            harness.collectState(this)

            harness.onAddressBookClick()

            assertEquals("btc", harness.capturedState.swapAsset?.tokenTicker)
        }

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

            assertEquals("scanned-address", harness.capturedState.addressText)
            assertEquals(0, BigDecimal("2.5").compareTo(harness.capturedState.amountTextState.amount))
            assertNull(harness.capturedState.selectedContact)
            verify { harness.navigationRouter.back() }
        }

    @Test
    fun addressChangeClearsSelectedContact() =
        runTest {
            val harness = harness(recipient = contactOnChain("btc"))
            harness.collectState(this)
            harness.onAddressBookClick()
            assertNotNull(harness.capturedState.selectedContact)

            harness.onAddressChange("zs1-typed-address")

            assertEquals("zs1-typed-address", harness.capturedState.addressText)
            assertNull(harness.capturedState.selectedContact)
        }

    @Test
    fun assetPickerIsFilteredBySelectedContactChain() =
        runTest {
            val eth = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
            val harness =
                harness(
                    assets = SwapAssetTestFixture.assetsData(data = listOf(eth)),
                    recipient = contactOnChain("eth")
                )
            harness.collectState(this)
            harness.onAddressBookClick()

            harness.onSwapAssetPickerClick()

            coVerify { harness.navigateToSwapAssetPicker(onlyChainTicker = "eth") }
        }

    @Test
    fun addressClickForwardsRefundAddressInfoArgs() =
        runTest {
            val harness = harness(preselect = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"))
            harness.collectState(this)

            harness.onAddressClick()

            verify {
                harness.navigationRouter.forward(
                    SwapRefundAddressInfoArgs(tokenTicker = "btc", chainTicker = "btc")
                )
            }
        }

    // endregion

    private fun contactOnChain(chainTicker: String): EnhancedABContact =
        mockk(relaxed = true) { every { blockchain } returns SwapAssetTestFixture.blockchain(chainTicker) }

    @Suppress("LongMethod")
    private fun harness(
        preselect: SwapAsset? = SwapAssetTestFixture.asset(),
        slippageResult: BigDecimal? = null,
        assetResult: SwapAsset? = null,
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
        val getPreselectedSwapAsset =
            mockk<GetPreselectedSwapAssetUseCase> {
                if (preselect != null) {
                    coEvery { this@mockk.invoke() } returns preselect
                } else {
                    // Never resolves -> the VM keeps `swapAsset` null (covers the no-asset-selected path).
                    coEvery { this@mockk.invoke() } coAnswers { awaitCancellation() }
                }
            }
        val requestSwapQuote = mockk<RequestSwapQuoteUseCase>(relaxed = true)
        val cancelSwap = mockk<CancelSwapUseCase>(relaxed = true)
        val navigateToSwapQuoteIfAvailable = mockk<NavigateToSwapQuoteIfAvailableUseCase>(relaxed = true)
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)
        val getSwapAssets =
            mockk<GetCuratedSwapAssetsUseCase> {
                every { observe() } returns MutableStateFlow(assets)
                every { this@mockk() } returns assets
            }
        val getSelectedWalletAccount =
            mockk<GetSelectedWalletAccountUseCase> {
                every { observe() } returns MutableStateFlow<WalletAccount?>(null)
            }
        val mapper = mockk<SwapVMMapper>()
        val swapRepository =
            mockk<SwapRepository>(relaxed = true) { every { this@mockk.assets } returns MutableStateFlow(assets) }

        val harness =
            Harness(
                navigateToSlippage = navigateToSlippage,
                navigateToSwapAssetPicker = navigateToSwapAssetPicker,
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
            )
        return harness
    }

    private class Harness(
        val navigateToSlippage: NavigateToSlippageUseCase,
        val navigateToSwapAssetPicker: NavigateToSwapAssetPickerUseCase,
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
        var onRequestSwapQuoteClick: (BigDecimal, String) -> Unit = { _, _ -> }

        /** Makes the next flex-input quote request hang so `isRequestingQuote` stays true. */
        fun makeQuoteHang(): CompletableDeferred<Unit> {
            val gate = CompletableDeferred<Unit>()
            coEvery {
                requestSwapQuote.requestFlexInputIntoZec(any(), any(), any(), any(), any())
            } coAnswers { gate.await() }
            return gate
        }

        fun collectState(scope: TestScope) {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.state.collect {} }
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.cancelState.collect {} }
        }
    }
}
