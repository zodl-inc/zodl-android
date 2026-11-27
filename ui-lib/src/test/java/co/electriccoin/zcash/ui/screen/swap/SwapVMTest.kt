package co.electriccoin.zcash.ui.screen.swap

import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapDirection
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.usecase.ScanResult
import co.electriccoin.zcash.ui.common.usecase.TooManyNotesOutcome
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.screen.swap.info.SwapRefundAddressInfoArgs
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
            val harness = swapVmHarness(preselect = SwapAssetTestFixture.asset(tokenTicker = "btc"))

            harness.collectState(this)

            assertEquals("btc", harness.capturedState.swapAsset?.tokenTicker)
        }

    @Test
    fun slippagePickerResultAppliedToState() =
        runTest {
            val harness = swapVmHarness(slippageResult = BigDecimal("7"))
            harness.collectState(this)

            harness.onSlippageClick(null)

            assertEquals(BigDecimal("7"), harness.capturedState.slippage)
            coVerify { harness.navigateToSlippage(BigDecimal("2"), null, any()) }
        }

    @Test
    fun assetPickerResultAppliedToState() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
            val harness = swapVmHarness(assetResult = asset)
            harness.collectState(this)

            harness.onSwapAssetPickerClick()

            assertEquals("eth", harness.capturedState.swapAsset?.tokenTicker)
        }

    @Test
    fun theTooManyNotesOkClearsTheAmountAndAsksForFocus() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)
            val focusRequests = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                harness.vm.amountFocusRequests.collect { focusRequests += it }
            }
            coEvery {
                harness.requestSwapQuote.requestFlexInputIntoZec(any(), any(), any(), any(), any())
            } returns TooManyNotesOutcome.OK
            harness.onTextFieldChange(NumberTextFieldInnerState(amount = BigDecimal("3")))

            assertEquals(BigDecimal("3"), harness.capturedState.amountTextState.amount)

            harness.onRequestSwapQuoteClick(BigDecimal("3"), null, "refund-address")

            assertNull(harness.capturedState.amountTextState.amount)
            assertEquals(listOf(Unit), focusRequests)
        }

    @Test
    fun aDismissedTooManyNotesSheetLeavesTheAmountAlone() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)
            val focusRequests = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                harness.vm.amountFocusRequests.collect { focusRequests += it }
            }
            coEvery {
                harness.requestSwapQuote.requestFlexInputIntoZec(any(), any(), any(), any(), any())
            } returns TooManyNotesOutcome.DISMISSED
            harness.onTextFieldChange(NumberTextFieldInnerState(amount = BigDecimal("3")))

            harness.onRequestSwapQuoteClick(BigDecimal("3"), null, "refund-address")

            assertEquals(BigDecimal("3"), harness.capturedState.amountTextState.amount)
            assertEquals(emptyList(), focusRequests)
        }

    @Test
    fun changeButtonTogglesMode() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)

            assertEquals(SwapDirection.SWAP_INTO_ZEC, harness.capturedState.swapDirection)

            harness.onChangeButtonClick()

            assertEquals(SwapDirection.SWAP_FROM_ZEC, harness.capturedState.swapDirection)
        }

    @Test
    fun changeButtonIgnoredForViewOnlyAccount() =
        runTest {
            val harness = swapVmHarness(account = account(isSpendable = false))
            harness.collectState(this)

            harness.onChangeButtonClick()

            assertEquals(SwapDirection.SWAP_INTO_ZEC, harness.capturedState.swapDirection)
        }

    @Test
    fun changeButtonIgnoredWhileAccountLoading() =
        runTest {
            val harness = swapVmHarness(account = null)
            harness.collectState(this)

            harness.onChangeButtonClick()

            assertEquals(SwapDirection.SWAP_INTO_ZEC, harness.capturedState.swapDirection)
        }

    @Test
    fun switchingToViewOnlyAccountResetsModeToIntoZec() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "btc")
            val harness = swapVmHarness(preselect = asset)
            harness.collectState(this)
            harness.onChangeButtonClick()
            assertEquals(SwapDirection.SWAP_FROM_ZEC, harness.capturedState.swapDirection)

            harness.accountFlow.value = account(isSpendable = false)

            assertEquals(SwapDirection.SWAP_INTO_ZEC, harness.capturedState.swapDirection)
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "refund-address")
            coVerify(exactly = 0) { harness.requestSwapQuote.requestExactInput(any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) {
                harness.requestSwapQuote.requestFlexInputIntoZec(any(), "refund-address", asset, any(), any())
            }
        }

    @Test
    fun switchingBackToSpendableAccountKeepsIntoZecAndAllowsToggle() =
        runTest {
            val harness = swapVmHarness(account = account(isSpendable = false))
            harness.collectState(this)

            harness.accountFlow.value = account(isSpendable = true)
            assertEquals(SwapDirection.SWAP_INTO_ZEC, harness.capturedState.swapDirection)

            harness.onChangeButtonClick()
            assertEquals(SwapDirection.SWAP_FROM_ZEC, harness.capturedState.swapDirection)
        }

    @Test
    fun requestQuotePinsSelectedAssetAndSlippage() =
        runTest {
            val asset = SwapAssetTestFixture.asset(tokenTicker = "btc")
            val harness = swapVmHarness(preselect = asset)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "refund-address")

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
            val harness = swapVmHarness(preselect = asset)
            harness.collectState(this)

            harness.onChangeButtonClick() // SWAP_INTO_ZEC -> SWAP_FROM_ZEC
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")

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
            val harness = swapVmHarness()
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
                swapVmHarness(
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
                swapVmHarness(
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
            val harness = swapVmHarness(preselect = null)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "refund-address")

            assertNull(harness.capturedState.swapAsset)
            coVerify(exactly = 0) {
                harness.requestSwapQuote.requestFlexInputIntoZec(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun requestingQuoteTogglesRequestingFlag() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)
            val gate = harness.makeQuoteHang()

            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "refund-address")
            assertEquals(true, harness.capturedState.isRequestingQuote)

            gate.complete(Unit)
            assertEquals(false, harness.capturedState.isRequestingQuote)
        }

    @Test
    fun refundWarningCancelledSkipsQuoteAndClearsRequestingFlag() =
        runTest {
            val harness = swapVmHarness(refundWarningResult = false)
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), BigDecimal("100"), "refund-address")

            coVerify(exactly = 0) {
                harness.requestSwapQuote.requestFlexInputIntoZec(any(), any(), any(), any(), any())
            }
            assertEquals(false, harness.capturedState.isRequestingQuote)
        }

    @Test
    fun refundWarningModeFollowsSwapDirection() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), BigDecimal("100"), "refund-address")
            coVerify(exactly = 1) {
                harness.navigateToSwapRefundWarning(fiatAmount = BigDecimal("100"), mode = SwapMode.FLEX_INPUT)
            }

            harness.onChangeButtonClick()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), BigDecimal("50"), "destination-address")
            coVerify(exactly = 1) {
                harness.navigateToSwapRefundWarning(fiatAmount = BigDecimal("50"), mode = SwapMode.EXACT_INPUT)
            }
        }

    // endregion

    // region cancel / back flow

    @Test
    fun backCancelsSwapWhenNoQuotePending() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)

            harness.onBack()

            assertNull(harness.vm.cancelState.value)
            coVerify(exactly = 1) { harness.cancelSwap() }
        }

    @Test
    fun backWhileRequestingShowsCancelConfirmation() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "refund-address")

            harness.onBack()

            assertNotNull(harness.vm.cancelState.value)
        }

    @Test
    fun backFromCancelConfirmationChecksForAvailableQuote() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)
            val gate = harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "refund-address")
            harness.onBack() // requesting -> show cancel confirmation
            gate.complete(Unit) // request finishes -> no longer requesting, sheet still visible

            harness.onBack() // now resolves the cancel confirmation

            assertNull(harness.vm.cancelState.value)
            coVerify { harness.navigateToSwapQuoteIfAvailable(any()) }
        }

    @Test
    fun cancelConfirmationNegativeButtonCancelsSwap() =
        runTest {
            val harness = swapVmHarness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "refund-address")
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
            val harness = swapVmHarness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "refund-address")
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
            val harness = swapVmHarness()
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
                swapVmHarness(
                    preselect = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                    assets =
                        SwapAssetTestFixture.assetsData(
                            data =
                                listOf(
                                    SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                                    SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
                                )
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
                swapVmHarness(
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
                swapVmHarness(
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
                swapVmHarness(
                    recipient = contactOnChain("btc"),
                    scanResult = ScanResult(address = "scanned-address", amount = BigDecimal("2.5"))
                )
            harness.collectState(this)
            harness.onAddressBookClick() // select a contact first, to prove the scan clears it
            // The default SWAP_INTO_ZEC direction treats a scan as a refund-address scan, which
            // deliberately does not apply the resolved asset/amount (see onQrCodeScannerClick) --
            // switch to SWAP_FROM_ZEC, where the scanned address is the actual swap counterparty.
            harness.onChangeButtonClick()

            harness.onQrCodeScannerClick()

            assertEquals("scanned-address", harness.capturedState.addressText)
            assertEquals(0, BigDecimal("2.5").compareTo(harness.capturedState.amountTextState.amount))
            assertNull(harness.capturedState.selectedContact)
            verify { harness.navigationRouter.back() }
        }

    @Test
    fun addressChangeClearsSelectedContact() =
        runTest {
            val harness = swapVmHarness(recipient = contactOnChain("btc"))
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
                swapVmHarness(
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
            val harness =
                swapVmHarness(preselect = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"))
            harness.collectState(this)

            harness.onAddressClick()

            verify {
                harness.navigationRouter.forward(
                    SwapRefundAddressInfoArgs(tokenTicker = "btc", chainTicker = "btc")
                )
            }
        }

    // endregion

    /** A relaxed account mock reports `isSpendable = false`, so the spend authority is always stubbed explicitly. */
    private fun account(isSpendable: Boolean): WalletAccount =
        mockk(relaxed = true) { every { this@mockk.isSpendable } returns isSpendable }

    private fun contactOnChain(chainTicker: String): EnhancedABContact =
        mockk(relaxed = true) { every { blockchain } returns SwapAssetTestFixture.blockchain(chainTicker) }
}
