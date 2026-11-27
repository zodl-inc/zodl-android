package co.electriccoin.zcash.ui.screen.pay

import co.electriccoin.zcash.ui.common.model.CrossPayRequest
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.usecase.ScanResult
import co.electriccoin.zcash.ui.common.usecase.TooManyNotesOutcome
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
    fun assetPickerResultPreservesFiatAndRecomputesAmountExactlyOnce() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
            val typedFiat = NumberTextFieldInnerState.fromAmount(BigDecimal("123"))
            val recomputedAmount = NumberTextFieldInnerState.fromAmount(BigDecimal("0.5"))
            val harness = payVmHarness(assetResult = asset, amountRecompute = recomputedAmount)
            harness.collectState(this)
            harness.onTextFieldChange(NumberTextFieldInnerState(), typedFiat)

            harness.onSwapAssetPickerClick()

            assertEquals("eth", harness.capturedState.asset?.tokenTicker)
            assertEquals(typedFiat, harness.capturedState.fiatAmount)
            assertEquals(recomputedAmount, harness.capturedState.amount)
            // Recompute happens once for the single asset change — proves there is no feedback loop.
            verify(exactly = 1) { harness.mapper.createAmountInnerState(typedFiat, any(), asset) }
        }

    @Test
    fun livePricePreservesTypedFiatAndRecomputesTokenAmount() =
        runTest {
            val cachedAsset = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth", usdPrice = null)
            val liveAsset =
                SwapAssetTestFixture.asset(
                    tokenTicker = "eth",
                    chainTicker = "eth",
                    usdPrice = BigDecimal("2")
                )
            val typedFiat = NumberTextFieldInnerState.fromAmount(BigDecimal("50"))
            val recomputedAmount = NumberTextFieldInnerState.fromAmount(BigDecimal("25"))
            val cachedAssets = SwapAssetsData(data = listOf(cachedAsset), isLoading = true)
            val harness =
                payVmHarness(
                    preselect = cachedAsset,
                    assets = cachedAssets,
                    amountRecompute = recomputedAmount
                )
            harness.collectState(this)
            harness.onTextFieldChange(NumberTextFieldInnerState(), typedFiat)

            harness.repositoryAssets.value = SwapAssetsData(data = listOf(liveAsset), isLoading = false)

            assertEquals(typedFiat, harness.capturedState.fiatAmount)
            assertEquals(recomputedAmount, harness.capturedState.amount)
            assertEquals(liveAsset, harness.capturedState.asset)
            verify(exactly = 1) {
                harness.mapper.createAmountInnerState(typedFiat, any(), liveAsset)
            }
        }

    @Test
    fun periodicPriceUpdatePreservesTypedValues() =
        runTest {
            val selectedAsset =
                SwapAssetTestFixture.asset(
                    tokenTicker = "eth",
                    chainTicker = "eth",
                    usdPrice = BigDecimal("2")
                )
            val refreshedAsset =
                SwapAssetTestFixture.asset(
                    tokenTicker = "eth",
                    chainTicker = "eth",
                    usdPrice = BigDecimal("3")
                )
            val typedAmount = NumberTextFieldInnerState.fromAmount(BigDecimal("25"))
            val typedFiat = NumberTextFieldInnerState.fromAmount(BigDecimal("50"))
            val harness =
                payVmHarness(
                    preselect = selectedAsset,
                    assets = SwapAssetsData(data = listOf(selectedAsset))
                )
            harness.collectState(this)
            harness.onTextFieldChange(typedAmount, typedFiat)

            harness.repositoryAssets.value =
                SwapAssetsData(data = listOf(refreshedAsset), isLoading = false)

            assertEquals(typedAmount, harness.capturedState.amount)
            assertEquals(typedFiat, harness.capturedState.fiatAmount)
            assertEquals(refreshedAsset, harness.capturedState.asset)
        }

    @Test
    fun theTooManyNotesOkClearsTheAmountsAndAsksForFocus() =
        runTest {
            val harness = payVmHarness()
            harness.collectState(this)
            val focusRequests = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                harness.vm.amountFocusRequests.collect { focusRequests += it }
            }
            harness.onTextFieldChange(
                NumberTextFieldInnerState.fromAmount(BigDecimal("25")),
                NumberTextFieldInnerState.fromAmount(BigDecimal("50"))
            )

            coEvery {
                harness.requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any(), any(), any(), any())
            } returns TooManyNotesOutcome.OK
            harness.onRequestSwapQuoteClick(BigDecimal("25"), BigDecimal("50"), "destination-address")

            assertNull(harness.capturedState.amount.amount)
            assertNull(harness.capturedState.fiatAmount.amount)
            assertEquals(listOf(Unit), focusRequests)
        }

    @Test
    fun aDismissedTooManyNotesSheetLeavesTheAmountsAlone() =
        runTest {
            val harness = payVmHarness()
            harness.collectState(this)
            val focusRequests = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                harness.vm.amountFocusRequests.collect { focusRequests += it }
            }
            coEvery {
                harness.requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any(), any(), any(), any())
            } returns TooManyNotesOutcome.DISMISSED
            harness.onTextFieldChange(
                NumberTextFieldInnerState.fromAmount(BigDecimal("25")),
                NumberTextFieldInnerState.fromAmount(BigDecimal("50"))
            )

            harness.onRequestSwapQuoteClick(BigDecimal("25"), BigDecimal("50"), "destination-address")

            assertEquals(BigDecimal("25"), harness.capturedState.amount.amount)
            assertEquals(emptyList(), focusRequests)
        }

    @Test
    fun slippagePickerResultAppliedToState() =
        runTest {
            val harness = payVmHarness(slippageResult = BigDecimal("7"))
            harness.collectState(this)

            harness.onSlippageClick(null)

            assertEquals(BigDecimal("7"), harness.capturedState.slippage)
            coVerify { harness.navigateToSlippage(BigDecimal("2"), null, SwapMode.EXACT_OUTPUT) }
        }

    @Test
    fun requestQuotePinsSelectedAssetAndSlippage() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "btc")
            val harness = payVmHarness(preselect = asset)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")

            coVerify(exactly = 1) {
                harness.requestSwapQuote.requestExactOutput(
                    amount = BigDecimal("1"),
                    address = "destination-address",
                    selectedAsset = asset,
                    slippage = BigDecimal("2"),
                    canNavigateToSwapQuote = any(),
                    reviseAmount = any(),
                    isRevisedQuoteAcceptable = any(),
                    maxRevisions = any()
                )
            }
        }

    // region quote-request guards

    @Test
    fun requestQuoteIgnoredWhenNoAssetSelected() =
        runTest {
            val harness = payVmHarness(preselect = null)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")

            assertNull(harness.capturedState.asset)
            coVerify(exactly = 0) {
                harness.requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun requestingQuoteTogglesRequestingFlag() =
        runTest {
            val harness = payVmHarness()
            harness.collectState(this)
            val gate = harness.makeQuoteHang()

            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")
            assertEquals(true, harness.capturedState.isRequestingQuote)

            gate.complete(Unit)
            assertEquals(false, harness.capturedState.isRequestingQuote)
        }

    @Test
    fun refundWarningCancelledSkipsQuoteAndClearsRequestingFlag() =
        runTest {
            val harness = payVmHarness(refundWarningResult = false)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), BigDecimal("100"), "destination-address")

            coVerify(exactly = 0) {
                harness.requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any(), any(), any(), any())
            }
            assertEquals(false, harness.capturedState.isRequestingQuote)
        }

    @Test
    fun refundWarningReceivesFiatAmountInExactOutputMode() =
        runTest {
            val harness = payVmHarness()
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), BigDecimal("100"), "destination-address")

            coVerify(exactly = 1) {
                harness.navigateToSwapRefundWarning(fiatAmount = BigDecimal("100"), mode = SwapMode.EXACT_OUTPUT)
            }
        }

    // endregion

    // region cancel / back flow

    @Test
    fun backCancelsSwapWhenNoQuotePending() =
        runTest {
            val harness = payVmHarness()
            harness.collectState(this)

            harness.onBack()

            assertNull(harness.vm.cancelState.value)
            coVerify(exactly = 1) { harness.cancelSwap() }
        }

    @Test
    fun backWhileRequestingShowsCancelConfirmation() =
        runTest {
            val harness = payVmHarness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")

            harness.onBack()

            assertNotNull(harness.vm.cancelState.value)
        }

    @Test
    fun backFromCancelConfirmationChecksForAvailableQuote() =
        runTest {
            val harness = payVmHarness()
            harness.collectState(this)
            val gate = harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")
            harness.onBack() // requesting -> show cancel confirmation
            gate.complete(Unit) // request finishes -> no longer requesting, sheet still visible

            harness.onBack() // now resolves the cancel confirmation

            assertNull(harness.vm.cancelState.value)
            coVerify { harness.navigateToSwapQuoteIfAvailable(any()) }
        }

    @Test
    fun cancelConfirmationNegativeButtonCancelsSwap() =
        runTest {
            val harness = payVmHarness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")
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
            val harness = payVmHarness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")
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
            val contract = "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913"
            val recipientAddress = "0x92bF6Fbd794bA41093013Db027400B174aE4b5Cd"
            val request =
                CrossPayRequest(
                    address = recipientAddress,
                    amount = CrossPayRequest.Amount(BigDecimal("2500000"), isAtomic = true),
                    assetReference =
                        CrossPayRequest.AssetReference.Contract(
                            chain = null,
                            chainId = "8453",
                            address = contract
                        )
                )
            val baseUsdc =
                SwapAssetTestFixture.asset(
                    tokenTicker = "USDC",
                    chainTicker = "base",
                    decimals = 6,
                    contractAddress = contract
                )
            val harness =
                payVmHarness(
                    recipient = contactOnChain("btc"),
                    scanResult =
                        ScanResult(address = request.address, amount = request.amount?.value, request = request),
                    assets = SwapAssetTestFixture.assetsData(data = listOf(baseUsdc))
                )
            harness.collectState(this)
            harness.onAddressBookClick() // select a contact first, to prove the scan clears it

            harness.onQrCodeScannerClick()

            assertEquals(recipientAddress, harness.capturedState.address)
            assertEquals(0, BigDecimal("2.5").compareTo(harness.capturedState.amount.amount))
            assertEquals(baseUsdc, harness.capturedState.asset)
            assertNull(harness.capturedState.selectedABContact)
            verify { harness.navigationRouter.back() }
        }

    @Test
    fun addressBookSelectionSetsContactAndClearsAddress() =
        runTest {
            val harness = payVmHarness(recipient = contactOnChain("btc"))
            harness.collectState(this)

            harness.onAddressChange("typed-before-picking")
            harness.onAddressBookClick()

            assertNotNull(harness.capturedState.selectedABContact)
            assertEquals("", harness.capturedState.address)
        }

    @Test
    fun addressChangeClearsSelectedContact() =
        runTest {
            val harness = payVmHarness(recipient = contactOnChain("btc"))
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
            val harness = payVmHarness(recipient = contactOnChain("eth"))
            harness.collectState(this)
            harness.onAddressBookClick()

            harness.onSwapAssetPickerClick()

            coVerify { harness.navigateToSwapAssetPicker(onlyChainTicker = "eth") }
        }

    // endregion

    private fun contactOnChain(chainTicker: String): EnhancedABContact =
        mockk(relaxed = true) { every { blockchain } returns SwapAssetTestFixture.blockchain(chainTicker) }
}
