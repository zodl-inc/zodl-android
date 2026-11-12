package co.electriccoin.zcash.ui.screen.pay

import cash.z.ecc.android.sdk.ext.toZecExact
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountUnavailableException
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.near.NearSwapAsset
import co.electriccoin.zcash.ui.common.usecase.MaxSendTarget
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxVariant
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import co.electriccoin.zcash.ui.screen.swap.quote.FakeSwapQuote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val NET_OF_APP_FEE = BigDecimal("0.9933")

/** [PayVM] Max: fills the token amount with what the account can spend, converted through USD and floored. */
@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class PayVMMaxTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1.23456789 ZEC at $30 into a $11 token is 3.367003336..., so flooring (3.36700333) differs from rounding.
    private val maxZatoshi = Zatoshi(123_456_789L)
    private val expectedToken = BigDecimal("3.34444441")

    @Test
    fun maxFillsFlooredTokenAmountAndMirrorsFiatFromIt() =
        runTest {
            val fiatSentinel = NumberTextFieldInnerState.fromAmount(BigDecimal("37.03703663"))
            val harness = maxHarness(fiat = fiatSentinel)
            harness.collectState(this)

            harness.onMaxClick()

            assertEquals(expectedToken, harness.capturedState.amount.amount)
            assertEquals(expectedToken, harness.capturedState.amount.lastValidAmount)
            assertEquals(fiatSentinel, harness.capturedState.fiatAmount)
            // The USD mirror is derived from the floored token amount, exactly like typing it.
            verify(exactly = 1) {
                harness.mapper.createFiatAmountInnerState(
                    match { it.amount == expectedToken },
                    any(),
                    harness.vmAsset
                )
            }
            coVerify(exactly = 1) { harness.getMaxSendAmount(MaxSendTarget.OwnTransparentReceiver) }
            assertFalse(harness.isMaxBannerVisible)
        }

    @Test
    fun maxNeverExceedsTheProposalMax() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)

            harness.onMaxClick()

            val tokenAmount = harness.capturedState.amount.amount!!
            val backInZec = tokenAmount.multiply(BigDecimal("11")).divide(BigDecimal("30"), 20, RoundingMode.DOWN)
            assertTrue(backInZec <= BigDecimal("1.23456789"))
            assertTrue(tokenAmount.scale() <= 8)
        }

    @Test
    fun maxWritesTokenAmountWithoutGroupingSeparators() =
        runTest {
            // 2,000,000 ZEC net of the 0.67 percent app fee at $30 into a $1 token is 59,598,000 (the account holds
            // the fee and buffer on top).
            val harness =
                maxHarness(
                    max = Zatoshi(200_000_000_000_000L),
                    tokenPrice = BigDecimal("1"),
                    account = account(spendable = 200_000_000_100_000L, pending = 0)
                )
            harness.collectState(this)

            harness.onMaxClick()

            assertEquals(
                stringResByDynamicNumber(BigDecimal("59598000.00000000"), includeGroupingSeparator = false),
                harness.capturedState.amount.innerTextFieldState.value
            )
        }

    @Test
    fun maxWithoutTokenPriceDoesNothing() =
        runTest {
            val harness = maxHarness(tokenPrice = null)
            harness.collectState(this)

            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
            assertNull(harness.capturedState.amount.amount)
        }

    @Test
    fun maxWithoutZecPriceDoesNothing() =
        runTest {
            val harness = maxHarness(zecAsset = null)
            harness.collectState(this)

            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
            assertNull(harness.capturedState.amount.amount)
        }

    @Test
    fun maxFailsWithBannerWhenAPriceDisappearsWhileTheProposalRuns() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            coEvery { harness.getMaxSendAmount(any()) } coAnswers {
                harness.curatedAssets.value = SwapAssetTestFixture.assetsData(zecAsset = null)
                MaxSendAmount(maxZatoshi, Zatoshi(10_000))
            }

            harness.onMaxClick()

            assertTrue(harness.isMaxBannerVisible)
            assertNull(harness.capturedState.amount.amount)
        }

    @Test
    fun maxFailsWithBannerWhenTheTokenAmountFloorsToZero() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1), tokenPrice = BigDecimal("1000000000"))
            harness.collectState(this)

            harness.onMaxClick()

            assertTrue(harness.isMaxBannerVisible)
            assertNull(harness.capturedState.amount.amount)
        }

    @Test
    fun maxUnavailableShowsFailureBannerAndLeavesAmountUntouched() =
        runTest {
            val typed = NumberTextFieldInnerState.fromAmount(BigDecimal("2"))
            val harness = maxHarness()
            harness.collectState(this)
            harness.onTextFieldChange(typed, NumberTextFieldInnerState())
            coEvery { harness.getMaxSendAmount(any()) } throws
                MaxSendAmountUnavailableException(MaxSendAmountFailure.OTHER)

            harness.onMaxClick()

            assertTrue(harness.isMaxBannerVisible)
            assertEquals(typed, harness.capturedState.amount)
        }

    @Test
    fun maxWithNothingLeftAfterFeesShowsFailureBanner() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            coEvery { harness.getMaxSendAmount(any()) } throws InsufficientFundsException()

            harness.onMaxClick()

            assertTrue(harness.isMaxBannerVisible)
        }

    @Test
    fun maxIsIgnoredUnlessSpendable() =
        runTest {
            listOf(null, account(spendable = null, pending = null), account(spendable = 0, pending = 5)).forEach {
                val harness = maxHarness(account = it)
                harness.collectState(this)

                harness.onMaxClick()

                coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
                assertNull(harness.capturedState.amount.amount)
            }
        }

    @Test
    fun maxIsIgnoredWhileRequestingQuote() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")

            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun secondMaxTapWhileOneIsInFlightIsIgnored() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            val gate = CompletableDeferred<MaxSendAmount>()
            coEvery { harness.getMaxSendAmount(any()) } coAnswers { gate.await() }

            harness.onMaxClick()
            harness.onMaxClick()
            gate.complete(MaxSendAmount(maxZatoshi, Zatoshi(10_000)))

            coVerify(exactly = 1) { harness.getMaxSendAmount(any()) }
            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    /**
     * With a dispatcher that does not run coroutines on the spot, both taps happen before anything is launched: the
     * in-flight flag must already be raised by the first one.
     */
    @Test
    fun secondMaxTapBeforeTheFirstIsDispatchedIsIgnored() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness = maxHarness()
            harness.collectState(this)
            runCurrent()

            harness.onMaxClick()
            harness.onMaxClick()
            runCurrent()

            coVerify(exactly = 1) { harness.getMaxSendAmount(any()) }
            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    // ---- Quote-linked Max (D1): the fill follows prices and spendable, never above the tap baseline ----

    private fun tokenFor(zatoshi: Long, zecPrice: String = "30", tokenPrice: String = "11") =
        Zatoshi(zatoshi)
            .toZecExact()
            .multiply(NET_OF_APP_FEE)
            .multiply(BigDecimal(zecPrice))
            .divide(BigDecimal(tokenPrice), java.math.MathContext.DECIMAL128)
            .setScale(8, RoundingMode.FLOOR)

    private fun setZecPrice(harness: PayVmHarness, price: String) {
        harness.curatedAssets.value =
            SwapAssetTestFixture.assetsData(
                zecAsset = (SwapAssetTestFixture.zecAsset() as NearSwapAsset).copy(usdPrice = BigDecimal(price))
            )
    }

    @Test
    fun maxFillShowsTheFeeHintWhileTheIntentIsActive() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            assertFalse(harness.isMaxHintVisible)

            harness.onMaxClick()

            assertTrue(harness.isMaxHintVisible)
            assertFalse(harness.isMaxBannerVisible)
        }

    @Test
    fun aFailedMaxShowsTheSendErrorAndNoHint() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)

            failMax(harness, MaxSendAmountFailure.OTHER)

            assertEquals(MaxFeedback.Error(MaxVariant.SEND), harness.capturedState.maxFeedback)
            assertFalse(harness.isMaxHintVisible)
        }

    @Test
    fun theErrorWinsOverTheHintOfAnEarlierFill() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()

            failMax(harness, MaxSendAmountFailure.OTHER)

            assertEquals(MaxFeedback.Error(MaxVariant.SEND), harness.capturedState.maxFeedback)
        }

    @Test
    fun theFillFallsWithSpendableAndRestoresButNeverExceedsTheBaseline() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            assertEquals(expectedToken, harness.capturedState.amount.amount)

            harness.account.value = account(spendable = 50_000_000, pending = 0)
            // net of the 20_000 reserve captured at the tap
            assertEquals(tokenFor(50_000_000 - 20_000), harness.capturedState.amount.amount)

            harness.account.value = account(spendable = 500_000_000, pending = 0)
            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    @Test
    fun fundsThatArriveAfterTheTapDoNotRaiseTheFill() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()

            harness.account.value = account(spendable = 5_000_000_000, pending = 0)

            coVerify(exactly = 1) { harness.getMaxSendAmount(any()) }
            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    @Test
    fun aPriceRefreshRecomputesTheTokenAmountFromTheSameZec() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()

            setZecPrice(harness, "60")

            assertEquals(tokenFor(123_456_789, zecPrice = "60"), harness.capturedState.amount.amount)
            setZecPrice(harness, "30")
            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    @Test
    fun aRefreshWithNothingLeftToFillClearsTheFieldsEndsTheIntentAndShowsTheMaxError() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()

            harness.account.value = account(spendable = 0, pending = 5)
            assertNull(harness.capturedState.amount.amount)
            assertNull(harness.capturedState.fiatAmount.amount)
            assertEquals(MaxFeedback.Error(MaxVariant.SEND), harness.capturedState.maxFeedback)

            // The intent ended: funds that arrive later are not picked up
            harness.account.value = account(spendable = 500_000_000, pending = 0)
            assertNull(harness.capturedState.amount.amount)
        }

    @Test
    fun aGrossSpendableJustAboveTheBaselineStillLowersTheFillByTheFeeAndBuffer() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()

            harness.account.value = account(spendable = 123_456_789 + 10_000, pending = 0)

            assertEquals(tokenFor(123_456_789 + 10_000 - 20_000), harness.capturedState.amount.amount)
        }

    // ---- Review: the quote's provider fee can push the fill past what is spendable ----

    private fun quoteWith(
        harness: PayVmHarness,
        amountIn: Long,
        amountInUsd: String = "100",
        amountOutUsd: String = "99",
        secondAmountIn: Long? = null
    ) {
        val quote =
            FakeSwapQuote(
                destinationAsset = SwapAssetTestFixture.asset(),
                mode = SwapMode.EXACT_OUTPUT,
                amountIn = BigDecimal(amountIn),
                amountInUsd = BigDecimal(amountInUsd),
                amountOutUsd = BigDecimal(amountOutUsd)
            )
        harness.revisedAmounts.clear()
        coEvery {
            harness.requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val revise = arg<suspend (SwapQuote) -> BigDecimal?>(5)
            val revised = revise(quote)
            harness.revisedAmounts += revised
            if (revised != null && secondAmountIn != null) {
                val accept = arg<suspend (SwapQuote) -> Boolean>(6)
                harness.acceptedRevisions += accept(quote.withAmountIn(BigDecimal(secondAmountIn)))
            }
            null
        }
    }

    private fun SwapQuote.withAmountIn(amountIn: BigDecimal) =
        FakeSwapQuote(
            destinationAsset = SwapAssetTestFixture.asset(),
            mode = SwapMode.EXACT_OUTPUT,
            amountIn = amountIn,
            amountInUsd = amountInUsd,
            amountOutUsd = amountOutUsd
        )

    /**
     * The quotes the provider returns for successive requests of one Review, in order: each one is offered for a
     * revision and the one after the last allowed revision is checked, as `RequestSwapQuoteUseCase` does.
     */
    private fun quotesWith(harness: PayVmHarness, vararg amountIns: Long) {
        harness.revisedAmounts.clear()
        coEvery {
            harness.requestSwapQuote.requestExactOutput(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val revise = arg<suspend (SwapQuote) -> BigDecimal?>(5)
            val accept = arg<suspend (SwapQuote) -> Boolean>(6)
            val maxRevisions = arg<Int>(7)
            var index = 0
            var revisions = 0
            while (revisions < maxRevisions) {
                val revised = revise(fakeQuote(amountIns[index]))
                harness.revisedAmounts += revised
                if (revised == null) break
                revisions++
                index++
            }
            if (revisions > 0) harness.acceptedRevisions += accept(fakeQuote(amountIns[index]))
            null
        }
    }

    private fun fakeQuote(amountIn: Long) =
        FakeSwapQuote(
            destinationAsset = SwapAssetTestFixture.asset(),
            mode = SwapMode.EXACT_OUTPUT,
            amountIn = BigDecimal(amountIn),
            amountInUsd = BigDecimal("100"),
            amountOutUsd = BigDecimal("99")
        )

    @Test
    fun aSecondQuoteStillOverTheCapIsRevisedAgainAndAThirdWithinTheCapIsAccepted() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            // A fixed part of the provider's fee: the ratio-sized second quote is still over the cap
            quotesWith(harness, 124_000_000, 123_600_000, 123_300_000)

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            assertEquals(2, harness.revisedAmounts.count { it != null })
            assertEquals(listOf(true), harness.acceptedRevisions)
            assertFalse(harness.isMaxBannerVisible)
            // Each revision lowers the amount
            assertTrue(harness.revisedAmounts[1]!! < harness.revisedAmounts[0]!!)
        }

    @Test
    fun aThirdQuoteStillOverTheCapStopsWithTheMaxFailureLine() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quotesWith(harness, 124_000_000, 123_600_000, 123_500_000)

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            assertEquals(listOf(false), harness.acceptedRevisions)
            assertEquals(MaxFeedback.Error(MaxVariant.SEND), harness.capturedState.maxFeedback)
        }

    @Test
    fun aQuoteThatFitsAfterTheFirstRevisionEndsTheRevisions() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quotesWith(harness, 124_000_000, 123_000_000)

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            // revised once, then the fitting quote was offered and kept
            assertEquals(2, harness.revisedAmounts.size)
            assertNull(harness.revisedAmounts[1])
            assertEquals(listOf(true), harness.acceptedRevisions)
        }

    @Test
    fun aMaxFillThatTheQuoteCostsMoreThanSpendableIsRefilledOnceForTheCostRatio() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            // The provider quotes 124_000_000 zat for the 3.34444441 tokens: scaled by cap / amountIn with the
            // margin below the cap (123_456_789 * 0.997 / 124_000_000), not by the app's USD prices
            quoteWith(harness, amountIn = 124_000_000)

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            val revisedToken = BigDecimal("3.31980390")
            assertEquals(listOf<BigDecimal?>(revisedToken), harness.revisedAmounts)
            assertEquals(revisedToken, harness.capturedState.amount.amount)
        }

    @Test
    fun theRevisionDoesNotDependOnTheAppUsdPrices() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            // The quote's own USD valuation is nonsense; only amountIn and the cap matter
            quoteWith(harness, amountIn = 124_000_000, amountInUsd = "1", amountOutUsd = "0")

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            assertEquals(listOf<BigDecimal?>(BigDecimal("3.31980390")), harness.revisedAmounts)
        }

    @Test
    fun aSecondQuoteWithinTheCapIsAccepted() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quoteWith(harness, amountIn = 124_000_000, secondAmountIn = 123_456_789)

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            assertEquals(listOf(true), harness.acceptedRevisions)
            assertFalse(harness.isMaxBannerVisible)
        }

    @Test
    fun aSecondQuoteStillOverTheCapStopsWithTheMaxFailureLineAndEndsTheIntent() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quoteWith(harness, amountIn = 124_000_000, secondAmountIn = 123_456_790)

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            assertEquals(listOf(false), harness.acceptedRevisions)
            assertEquals(MaxFeedback.Error(MaxVariant.SEND), harness.capturedState.maxFeedback)
            assertFalse(harness.isMaxHintVisible)
            // The intent ended: later spendable changes do not refill
            val amountAfterStop = harness.capturedState.amount.amount
            harness.account.value = account(spendable = 50_000_000, pending = 0)
            assertEquals(amountAfterStop, harness.capturedState.amount.amount)
        }

    @Test
    fun aQuoteThatFitsIsNeverChecked() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quoteWith(harness, amountIn = 123_000_000, secondAmountIn = 999_000_000)

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            assertTrue(harness.acceptedRevisions.isEmpty())
        }

    @Test
    fun aSelectionOnlyChangeKeepsTheMaxIntentAndTheHint() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            val same = harness.capturedState.amount
            val sameFiat = harness.capturedState.fiatAmount

            harness.onTextFieldChange(same, sameFiat)
            harness.account.value = account(spendable = 50_000_000, pending = 0)

            assertTrue(harness.isMaxHintVisible)
            assertEquals(tokenFor(50_000_000 - 20_000), harness.capturedState.amount.amount)
        }

    @Test
    fun aRevisedFillStaysRevisedWhileTheIntentIsActive() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quoteWith(harness, amountIn = 124_000_000)
            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            setZecPrice(harness, "60")

            assertEquals(tokenFor(122_547_209, zecPrice = "60"), harness.capturedState.amount.amount)
        }

    @Test
    fun aRefreshAfterARevisionNeverNudgesTheAmountUp() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quoteWith(harness, amountIn = 124_000_000)
            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")
            val revised = harness.capturedState.amount.amount!!

            // A refresh of the balance with the unchanged prices must leave the revised amount alone or lower it
            harness.account.value = account(spendable = 500_000_000, pending = 0)
            assertTrue(harness.capturedState.amount.amount!! <= revised)
            harness.account.value = account(spendable = 500_000_001, pending = 0)
            assertTrue(harness.capturedState.amount.amount!! <= revised)
        }

    @Test
    fun aLaterReviewThatNeedsNoRevisionNeverChecksTheOldCap() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quoteWith(harness, amountIn = 124_000_000, secondAmountIn = 123_456_789)
            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")
            harness.acceptedRevisions.clear()

            quoteWith(harness, amountIn = 100_000_000, secondAmountIn = 999_000_000)
            harness.onRequestSwapQuoteClick(harness.capturedState.amount.amount!!, null, "destination-address")

            assertTrue(harness.acceptedRevisions.isEmpty())
        }

    @Test
    fun aQuoteThatFitsWithinWhatIsSpendableIsKept() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            quoteWith(harness, amountIn = 123_000_000)

            harness.onRequestSwapQuoteClick(expectedToken, null, "destination-address")

            assertEquals(listOf<BigDecimal?>(null), harness.revisedAmounts)
            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    @Test
    fun withoutAMaxIntentTheQuoteIsNeverRevised() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            quoteWith(harness, amountIn = 999_000_000)

            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")

            assertEquals(listOf<BigDecimal?>(null), harness.revisedAmounts)
        }

    @Test
    fun balancesBeingUndeterminedKeepTheFill() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()

            harness.account.value = account(spendable = null, pending = null)

            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    @Test
    fun aManualEditEndsTheIntentAndTheHint() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            val typed = NumberTextFieldInnerState.fromAmount(BigDecimal("1"))

            harness.onTextFieldChange(typed, NumberTextFieldInnerState())
            harness.account.value = account(spendable = 50_000_000, pending = 0)
            setZecPrice(harness, "60")

            assertEquals(typed, harness.capturedState.amount)
            assertFalse(harness.isMaxHintVisible)
        }

    @Test
    fun choosingAnotherAssetEndsTheIntent() =
        runTest {
            val other = SwapAssetTestFixture.asset(usdPrice = BigDecimal("2"))
            val harness = maxHarness(assetResult = other)
            harness.collectState(this)
            harness.onMaxClick()

            harness.onSwapAssetPickerClick()
            val amountAfterSwitch = harness.capturedState.amount
            harness.account.value = account(spendable = 50_000_000, pending = 0)

            assertEquals(amountAfterSwitch, harness.capturedState.amount)
            assertFalse(harness.isMaxHintVisible)
        }

    @Test
    fun maxIsNotRefreshedWhileRequestingAQuote() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            harness.onMaxClick()
            harness.makeQuoteHang()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")

            harness.account.value = account(spendable = 50_000_000, pending = 0)

            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    @Test
    fun clearingTheViewModelCancelsAnInFlightMax() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            var cancelled = false
            coEvery { harness.getMaxSendAmount(any()) } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            harness.onMaxClick()

            harness.clearViewModel()

            assertTrue(cancelled)
            assertNull(harness.capturedState.amount.amount)
            assertFalse(harness.isMaxBannerVisible)
        }

    @Test
    fun clearingTheViewModelWithoutMaxInFlightIsHarmless() =
        runTest {
            val harness = maxHarness()

            harness.clearViewModel()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun requestingAQuoteCancelsAnInFlightMax() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            var cancelled = false
            coEvery { harness.getMaxSendAmount(any()) } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            harness.onMaxClick()

            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")

            assertTrue(cancelled)
            assertNull(harness.capturedState.amount.amount)
        }

    private fun failMax(harness: PayVmHarness, failure: MaxSendAmountFailure) {
        coEvery { harness.getMaxSendAmount(any()) } throws MaxSendAmountUnavailableException(failure)
        harness.onMaxClick()
    }

    @Test
    fun everyFailureExceptTooManyNotesIsABannerNotASheet() =
        runTest {
            listOf(
                MaxSendAmountFailure.INVALID_RECIPIENT,
                MaxSendAmountFailure.SYNC_REQUIRED,
                MaxSendAmountFailure.OTHER
            ).forEach {
                val harness = maxHarness()
                harness.collectState(this)

                failMax(harness, it)

                assertTrue(harness.isMaxBannerVisible)
                assertNull(harness.vm.maxSheetState.value)
            }
        }

    @Test
    fun tooManyNotesShowsTheSheetAndNoBanner() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)

            failMax(harness, MaxSendAmountFailure.TOO_MANY_NOTES)

            assertNotNull(harness.vm.maxSheetState.value)
            assertFalse(harness.isMaxBannerVisible)
        }

    @Test
    fun backOnTheSheetDismissesItAndKeepsTheAmount() =
        runTest {
            val typed = NumberTextFieldInnerState.fromAmount(BigDecimal("2"))
            val harness = maxHarness()
            harness.collectState(this)
            harness.onTextFieldChange(typed, NumberTextFieldInnerState())
            failMax(harness, MaxSendAmountFailure.TOO_MANY_NOTES)

            harness.vm.maxSheetState.value!!
                .onBack()

            assertNull(harness.vm.maxSheetState.value)
            assertEquals(typed, harness.capturedState.amount)
            assertEquals(0, harness.focusRequestCount)
        }

    @Test
    fun okDismissesTheSheetClearsTheAmountAndFocusesTheField() =
        runTest {
            val typed = NumberTextFieldInnerState.fromAmount(BigDecimal("2"))
            val harness = maxHarness()
            harness.collectState(this)
            harness.onTextFieldChange(typed, typed)
            failMax(harness, MaxSendAmountFailure.TOO_MANY_NOTES)

            harness.vm.maxSheetState.value!!
                .ok
                .onClick()

            assertNull(harness.vm.maxSheetState.value)
            assertNull(harness.capturedState.amount.amount)
            assertNull(harness.capturedState.fiatAmount.amount)
            assertEquals(1, harness.focusRequestCount)
        }

    @Test
    fun bannerIsClearedByAMaxRetap() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            failMax(harness, MaxSendAmountFailure.OTHER)
            assertTrue(harness.isMaxBannerVisible)
            val gate = CompletableDeferred<MaxSendAmount>()
            coEvery { harness.getMaxSendAmount(any()) } coAnswers { gate.await() }

            harness.onMaxClick()

            assertFalse(harness.isMaxBannerVisible)
            gate.complete(MaxSendAmount(maxZatoshi, Zatoshi(10_000)))
            assertFalse(harness.isMaxBannerVisible)
            assertEquals(expectedToken, harness.capturedState.amount.amount)
        }

    @Test
    fun aRetapThatFailsAgainShowsTheBannerAgain() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            failMax(harness, MaxSendAmountFailure.OTHER)

            failMax(harness, MaxSendAmountFailure.OTHER)

            assertTrue(harness.isMaxBannerVisible)
        }

    @Test
    fun bannerIsClearedByEditingTheAmount() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            failMax(harness, MaxSendAmountFailure.OTHER)

            harness.onTextFieldChange(
                NumberTextFieldInnerState.fromAmount(BigDecimal("1")),
                NumberTextFieldInnerState()
            )

            assertFalse(harness.isMaxBannerVisible)
        }

    @Test
    fun bannerIsClearedByChangingTheAddress() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            failMax(harness, MaxSendAmountFailure.OTHER)

            harness.onAddressChange("another-address")

            assertFalse(harness.isMaxBannerVisible)
        }

    @Test
    fun bannerIsClearedByChoosingAnotherAsset() =
        runTest {
            val other = SwapAssetTestFixture.asset(usdPrice = BigDecimal("2"))
            val harness = maxHarness(assetResult = other)
            harness.collectState(this)
            failMax(harness, MaxSendAmountFailure.OTHER)

            harness.onSwapAssetPickerClick()

            assertFalse(harness.isMaxBannerVisible)
        }

    @Test
    fun bannerSurvivesAPriceRefreshOfTheSameAsset() =
        runTest {
            val harness = maxHarness()
            harness.collectState(this)
            failMax(harness, MaxSendAmountFailure.OTHER)

            harness.curatedAssets.value = SwapAssetTestFixture.assetsData(zecAsset = SwapAssetTestFixture.zecAsset())

            assertTrue(harness.isMaxBannerVisible)
        }

    private fun account(spendable: Long?, pending: Long?) =
        mockk<WalletAccount> {
            every { spendableShieldedBalance } returns spendable?.let { Zatoshi(it) }
            every { pendingShieldedBalance } returns pending?.let { Zatoshi(it) }
            every { isShieldingAvailable } returns false
            every { isShieldedPending } returns ((pending ?: 0) > 0)
        }

    /** A harness whose selected token costs [tokenPrice] USD, ZEC costs $30, and whose proposal max is [max]. */
    private fun maxHarness(
        max: Zatoshi = maxZatoshi,
        tokenPrice: BigDecimal? = BigDecimal("11"),
        zecAsset: SwapAsset? = SwapAssetTestFixture.zecAsset(),
        // At the tap the account holds the max plus the 20_000 reserve (fee and buffer), so the reserve is 20_000
        account: WalletAccount? = account(spendable = max.value + 20_000, pending = 0),
        fiat: NumberTextFieldInnerState = NumberTextFieldInnerState(),
        assetResult: SwapAsset? = null,
    ): PayVmHarness {
        val token = SwapAssetTestFixture.asset(usdPrice = tokenPrice)
        return payVmHarness(
            preselect = token,
            assets = SwapAssetTestFixture.assetsData(data = listOf(token), zecAsset = zecAsset),
            fiatRecompute = fiat,
            account = account,
            assetResult = assetResult,
            maxAmount = MaxSendAmount(max, Zatoshi(10_000)),
        )
    }
}
