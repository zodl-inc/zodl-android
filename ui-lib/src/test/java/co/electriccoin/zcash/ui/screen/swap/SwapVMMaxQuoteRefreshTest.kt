package co.electriccoin.zcash.ui.screen.swap

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxVariant
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.screen.swap.quote.FakeSwapQuote
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull

/**
 * [SwapVM] quote-linked Max (decision D1): while the Max intent is active every quote refresh re-fills the From
 * amount with `MaxFillPolicy.refresh(baseline, spendable, adjust)`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapVMMaxQuoteRefreshTest {
    private var quoteCounter = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Quotes differ by amount, so the repository's state flow emits each one. */
    private fun SwapVmHarness.refreshQuote() {
        quoteCounter++
        quoteFlow.value =
            SwapQuoteData.Success(
                FakeSwapQuote(
                    destinationAsset = SwapAssetTestFixture.asset(),
                    mode = SwapMode.EXACT_INPUT,
                    amountInFormatted = BigDecimal(quoteCounter)
                )
            )
    }

    private fun SwapVmHarness.setSpendable(spendable: Long) {
        accountFlow.value = spendableAccount(Zatoshi(spendable))
    }

    private fun SwapVmHarness.fill(): BigDecimal? = capturedState.amountTextState.amount

    /** The account holds `baseline + reserve` at the tap, so the reserve captured at the tap is [reserve]. */
    private fun TestScope.tappedMax(baseline: Long = 100_000_000, reserve: Long = 20_000): SwapVmHarness =
        maxHarness(max = Zatoshi(baseline), account = spendableAccount(Zatoshi(baseline + reserve)))
            .also { it.onMaxClick() }

    @Test
    fun aRefreshWithEnoughFundsKeepsTheBaseline() =
        runTest {
            val harness = tappedMax()
            harness.refreshQuote()

            assertEquals(0, BigDecimal("1").compareTo(harness.fill()))
        }

    @Test
    fun aRefreshLowersTheFillWhenSpendableFellBelowTheBaselineAndRestoresIt() =
        runTest {
            val harness = tappedMax()
            harness.setSpendable(60_000_000)
            harness.refreshQuote()
            // net of the 20_000 reserve captured at the tap: 0.6 - 0.0002
            assertEquals(0, BigDecimal("0.5998").compareTo(harness.fill()))

            harness.setSpendable(500_000_000)
            harness.refreshQuote()
            assertEquals(0, BigDecimal("1").compareTo(harness.fill()))
        }

    @Test
    fun aGrossSpendableJustAboveTheBaselineStillLowersTheFillByTheFeeAndBuffer() =
        runTest {
            // gross 1.0001 is above the 1.0 baseline, but net of fee and buffer it is 0.9999
            val harness = tappedMax()
            harness.setSpendable(100_010_000)
            harness.refreshQuote()

            assertEquals(0, BigDecimal("0.9999").compareTo(harness.fill()))
        }

    @Test
    fun aMultiNoteReserveLowersTheFillByExactlyTheSpendableDrop() =
        runTest {
            // 3 notes: real fee 15_000 plus a 10_000 buffer, a 25_000 reserve (not one marginal fee)
            val harness = tappedMax(reserve = 25_000)
            harness.setSpendable(100_025_000 - 5_000)
            harness.refreshQuote()

            assertEquals(0, BigDecimal("0.99995").compareTo(harness.fill()))
        }

    @Test
    fun aRefreshWithNothingLeftToFillClearsTheAmountEndsTheIntentAndShowsTheMaxError() =
        runTest {
            val harness = tappedMax()
            harness.setSpendable(15_000) // net of fee and buffer: nothing
            harness.refreshQuote()

            assertNull(harness.fill())
            assertNull(harness.capturedState.maxHint)
            assertEquals(MaxFeedback.Error(MaxVariant.SWAP), harness.capturedState.maxError)

            // The intent ended: funds that arrive later are not picked up
            harness.setSpendable(500_000_000)
            harness.refreshQuote()
            assertNull(harness.fill())
        }

    @Test
    fun aRefreshDoesNotAlterTheQuoteTheQuoteScreenShows() =
        runTest {
            val harness = tappedMax()
            harness.refreshQuote()
            val shownQuote = harness.quoteFlow.value

            harness.setSpendable(60_000_000)
            harness.refreshQuote()
            val refilledQuote = harness.quoteFlow.value

            // Only the form underneath follows spendable funds; no quote is requested or replaced by the refill
            assertEquals(0, BigDecimal("0.5998").compareTo(harness.fill()))
            assertEquals(2, quoteCounter)
            assertEquals(BigDecimal(1), ((shownQuote as SwapQuoteData.Success).quote.amountInFormatted))
            assertEquals(BigDecimal(2), ((refilledQuote as SwapQuoteData.Success).quote.amountInFormatted))
            coVerify(exactly = 0) { harness.requestSwapQuote.requestExactInput(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) {
                harness.requestSwapQuote.requestFlexInputIntoZec(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun newSpendableFundsNeverRaiseTheFillAboveTheBaseline() =
        runTest {
            val harness = tappedMax()
            harness.setSpendable(900_000_000)
            harness.refreshQuote()

            assertEquals(0, BigDecimal("1").compareTo(harness.fill()))
        }

    @Test
    fun theRefreshKeepsTheHint() =
        runTest {
            val harness = tappedMax()
            harness.refreshQuote()

            assertEquals(MaxFeedback.Hint, harness.capturedState.maxHint)
        }

    @Test
    fun fiatModeFollowsTheSameZecFill() =
        runTest {
            // (0.6 - 2 * 0.0001) ZEC * $30 = 17.994, floored to cents
            val harness = tappedMax()
            harness.onSwapCurrencyTypeClick(null)
            harness.setSpendable(60_000_000)
            harness.refreshQuote()

            assertEquals(0, BigDecimal("17.99").compareTo(harness.fill()))
        }

    @Test
    fun aManualEditStopsTheRefreshes() =
        runTest {
            val harness = tappedMax()
            harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("0.25")))
            harness.refreshQuote()

            assertEquals(0, BigDecimal("0.25").compareTo(harness.fill()))
        }

    @Test
    fun flippingTheDirectionStopsTheRefreshes() =
        runTest {
            val harness = tappedMax()
            harness.onChangeButtonClick() // INTO_ZEC
            harness.onChangeButtonClick() // FROM_ZEC again, the amount field was kept
            harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("0.5")))
            harness.setSpendable(60_000_000)
            harness.refreshQuote()

            assertEquals(0, BigDecimal("0.5").compareTo(harness.fill()))
        }

    @Test
    fun changingTheAssetStopsTheRefreshes() =
        runTest {
            val harness =
                maxHarness(max = Zatoshi(100_000_000), assetResult = SwapAssetTestFixture.asset(tokenTicker = "btc"))
            harness.onMaxClick()
            harness.onSwapAssetPickerClick()
            harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("0.5")))
            harness.setSpendable(60_000_000)
            harness.refreshQuote()

            assertEquals(0, BigDecimal("0.5").compareTo(harness.fill()))
        }

    @Test
    fun withoutAMaxIntentARefreshLeavesTheAmountAlone() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("0.4")))
            harness.refreshQuote()

            assertEquals(0, BigDecimal("0.4").compareTo(harness.fill()))
        }

    @Test
    fun aFailedMaxStartsNoRefreshes() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure.OTHER)
            harness.onMaxClick()
            harness.refreshQuote()

            assertNull(harness.fill())
        }

    @Test
    fun theQuoteAdjustIsTheIdentity() {
        val quote = FakeSwapQuote(destinationAsset = SwapAssetTestFixture.asset(), mode = SwapMode.EXACT_INPUT)

        assertEquals(Zatoshi(123), SwapMaxAmountFormatter.quoteAdjust(quote)(Zatoshi(123)))
        assertEquals(Zatoshi(123), SwapMaxAmountFormatter.quoteAdjust(null)(Zatoshi(123)))
    }
}
