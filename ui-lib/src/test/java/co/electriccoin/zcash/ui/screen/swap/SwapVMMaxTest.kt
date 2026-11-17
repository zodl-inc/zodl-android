package co.electriccoin.zcash.ui.screen.swap

import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountUnavailableException
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.usecase.MaxSendTarget
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxVariant
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.asPrivacySensitive
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import co.electriccoin.zcash.ui.screen.balances.spendable.SpendableBalanceArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** [SwapVM] Max: fills the From amount with the max spendable ZEC net of fees. */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapVMMaxTest {
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
    fun maxProposesAgainstOwnTransparentReceiverWithNoMemo() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()

            coVerify(exactly = 1) { harness.getMaxSendAmount(MaxSendTarget.OwnTransparentReceiver) }
        }

    @Test
    fun maxIntoZecIsNoOp() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onChangeButtonClick() // SWAP_FROM_ZEC -> SWAP_INTO_ZEC
            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun maxWithoutAccountIsNoOp() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), account = null)
            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun maxWithoutSpendableBalanceIsNoOp() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), account = spendableAccount(spendable = Zatoshi(0)))
            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun maxWhileQuoteRequestInFlightIsNoOp() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.makeExactInputQuoteHang() // maxHarness is in SWAP_FROM_ZEC
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")
            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun maxInFiatModeWithoutZecPriceIsNoOp() =
        runTest {
            val harness =
                maxHarness(
                    max = Zatoshi(100_000_000),
                    assets = SwapAssetTestFixture.assetsData(zecAsset = zecAssetWithPrice(null))
                )
            harness.onSwapCurrencyTypeClick(null)
            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun maxInFiatModeWithZeroZecPriceIsNoOp() =
        runTest {
            val harness =
                maxHarness(
                    max = Zatoshi(100_000_000),
                    assets = SwapAssetTestFixture.assetsData(zecAsset = zecAssetWithPrice(BigDecimal.ZERO))
                )
            harness.onSwapCurrencyTypeClick(null)
            harness.onMaxClick()

            coVerify(exactly = 0) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun maxInTokenModeWorksWithoutZecPrice() =
        runTest {
            val harness =
                maxHarness(
                    max = Zatoshi(100_000_000),
                    assets = SwapAssetTestFixture.assetsData(zecAsset = zecAssetWithPrice(null))
                )
            harness.onMaxClick()

            assertEquals(0, BigDecimal("1").compareTo(harness.capturedState.amountTextState.amount))
        }

    @Test
    fun maxFillsZecInTokenMode() =
        runTest {
            val harness = maxHarness(max = Zatoshi(123_456_789))
            harness.onMaxClick()

            val field = harness.capturedState.amountTextState
            assertEquals(0, BigDecimal("1.23456789").compareTo(field.amount))
            assertEquals(0, BigDecimal("1.23456789").compareTo(field.lastValidAmount))
            assertEquals(false, harness.isMaxBannerVisible)
        }

    @Test
    fun maxFillsFlooredUsdInFiatMode() =
        runTest {
            // 1.23456789 ZEC * $30 = 37.0370367 -> 37.03, never 37.04.
            val harness = maxHarness(max = Zatoshi(123_456_789))
            harness.onSwapCurrencyTypeClick(null)
            harness.onMaxClick()

            assertEquals(0, BigDecimal("37.03").compareTo(harness.capturedState.amountTextState.amount))
        }

    @Test
    fun maxNeverRoundsUpInFiatMode() =
        runTest {
            // 0.99999999 ZEC * $30 = 29.99999970 -> 29.99.
            val harness = maxHarness(max = Zatoshi(99_999_999))
            harness.onSwapCurrencyTypeClick(null)
            harness.onMaxClick()

            assertEquals(0, BigDecimal("29.99").compareTo(harness.capturedState.amountTextState.amount))
        }

    @Test
    fun maxWritesTextWithoutGroupingSeparators() =
        runTest {
            val harness = maxHarness(max = Zatoshi(123_450_000_000))
            harness.onMaxClick()

            val text = harness.capturedState.amountTextState.innerTextFieldState.value
            assertEquals(
                stringResByDynamicNumber(BigDecimal("1234.5").setScale(8), includeGroupingSeparator = false),
                text
            )
        }

    @Test
    fun maxUsesCurrencyTypeAtResolutionTime() =
        runTest {
            val harness = maxHarness(max = Zatoshi(123_456_789), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(123_456_789))
            harness.onMaxClick()
            harness.onSwapCurrencyTypeClick(null) // toggled to FIAT while the request is running
            gate.complete(Unit)

            assertEquals(0, BigDecimal("37.03").compareTo(harness.capturedState.amountTextState.amount))
        }

    @Test
    fun maxFailsWhenZecPriceDisappearsBeforeResolution() =
        runTest {
            val harness = maxHarness(max = Zatoshi(123_456_789), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(123_456_789))
            harness.onSwapCurrencyTypeClick(null)
            harness.onMaxClick()
            harness.assetsRef[0] = SwapAssetTestFixture.assetsData(zecAsset = zecAssetWithPrice(null))
            gate.complete(Unit)

            assertEquals(true, harness.isMaxBannerVisible)
            assertNull(harness.capturedState.amountTextState.amount)
        }

    @Test
    fun maxFailureShowsBannerAndLeavesAmountUntouched() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()

            assertEquals(true, harness.isMaxBannerVisible)
            assertNull(harness.maxSheet)
            assertNull(harness.capturedState.maxHint)
            assertNull(harness.capturedState.amountTextState.amount)
        }

    @Test
    fun maxSecondTapWhileInFlightIsIgnored() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.onMaxClick()
            gate.complete(Unit)

            coVerify(exactly = 1) { harness.getMaxSendAmount(any()) }
        }

    /**
     * With a dispatcher that does not run coroutines on the spot, both taps happen before anything is launched: the
     * in-flight flag must already be raised by the first one.
     */
    @Test
    fun maxSecondTapBeforeTheFirstIsDispatchedIsIgnored() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val harness =
                swapVmHarness(
                    account = spendableAccount(spendable = Zatoshi(500_000_000)),
                    assets = SwapAssetTestFixture.assetsData(zecAsset = zecAssetWithPrice(BigDecimal("30")))
                )
            harness.collectState(this)
            coEvery { harness.getMaxSendAmount(any()) } returns
                MaxSendAmount(amount = Zatoshi(100_000_000), fee = Zatoshi(10_000))
            runCurrent()
            harness.onChangeButtonClick() // SWAP_INTO_ZEC -> SWAP_FROM_ZEC
            runCurrent()

            harness.onMaxClick()
            harness.onMaxClick()
            runCurrent()

            coVerify(exactly = 1) { harness.getMaxSendAmount(any()) }
            assertEquals(0, BigDecimal("1").compareTo(harness.capturedState.amountTextState.amount))
        }

    @Test
    fun aTapAfterAMaxIsCancelledStartsANewRequest() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.onChangeButtonClick() // cancels
            harness.onChangeButtonClick() // back to SWAP_FROM_ZEC

            harness.onMaxClick()
            gate.complete(Unit)

            coVerify(exactly = 2) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun maxIsCancelledWhenViewModelIsCleared() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.clearViewModel()
            gate.complete(Unit)

            assertNull(harness.capturedState.amountTextState.amount)
            assertEquals(false, harness.isMaxBannerVisible)
        }

    @Test
    fun maxIsCancelledWhenQuoteIsRequested() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.onRequestSwapQuoteClick(BigDecimal("1"), null, "destination-address")
            gate.complete(Unit)

            assertNull(harness.capturedState.amountTextState.amount)
        }

    @Test
    fun maxIsCancelledWhenDirectionChanges() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.onChangeButtonClick()
            gate.complete(Unit)

            assertNull(harness.capturedState.amountTextState.amount)
        }

    @Test
    fun maxDoesNotFollowLaterBalanceChanges() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.assetsRef[0] = SwapAssetTestFixture.assetsData(zecAsset = zecAssetWithPrice(BigDecimal("99")))

            assertEquals(0, BigDecimal("1").compareTo(harness.capturedState.amountTextState.amount))
            coVerify(exactly = 1) { harness.getMaxSendAmount(any()) }
        }

    @Test
    fun maxDoesNotRequestQuote() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()

            coVerify(exactly = 0) { harness.requestSwapQuote.requestExactInput(any(), any(), any(), any(), any()) }
        }

    // Fees-and-slippage hint (the slot above the button)

    @Test
    fun maxShowsTheFeeSlippageHint() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()

            assertEquals(MaxFeedback.Hint, harness.capturedState.maxHint)
        }

    @Test
    fun noHintBeforeMaxOrAfterAFailedMax() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            assertNull(harness.capturedState.maxHint)
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()

            assertNull(harness.capturedState.maxHint)
        }

    @Test
    fun editingTheAmountWhileMaxIsInFlightCancelsIt() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()

            harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("0.5")))
            gate.complete(Unit)

            assertEquals(0, BigDecimal("0.5").compareTo(harness.capturedState.amountTextState.amount))
            assertNull(harness.capturedState.maxHint)
        }

    @Test
    fun maxCanBeTappedAgainAfterAnEditCancelledIt() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("0.5")))
            gate.complete(Unit)
            harness.onMaxClick()

            assertEquals(0, BigDecimal("1").compareTo(harness.capturedState.amountTextState.amount))
        }

    @Test
    fun editingTheAmountHidesTheHint() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()

            harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("0.5")))

            assertNull(harness.capturedState.maxHint)
        }

    @Test
    fun aNonEditChangeKeepsTheHint() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()

            harness.onTextFieldChange(harness.capturedState.amountTextState)

            assertEquals(MaxFeedback.Hint, harness.capturedState.maxHint)
        }

    @Test
    fun changingTheAssetHidesTheHint() =
        runTest {
            val harness =
                maxHarness(max = Zatoshi(100_000_000), assetResult = SwapAssetTestFixture.asset(tokenTicker = "btc"))
            harness.onMaxClick()

            harness.onSwapAssetPickerClick()

            assertNull(harness.capturedState.maxHint)
        }

    @Test
    fun anAssetThatLeavesTheCatalogEndsTheMaxIntent() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()
            assertEquals(MaxFeedback.Hint, harness.capturedState.maxHint)

            harness.repositoryAssets.value = SwapAssetsData(data = emptyList(), isLoading = false)

            assertNull(harness.capturedState.swapAsset)
            assertNull(harness.capturedState.maxHint)
        }

    @Test
    fun anAssetCatalogThatIsStillLoadingKeepsTheMaxIntent() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()

            harness.repositoryAssets.value = SwapAssetsData(data = emptyList(), isLoading = true)

            assertEquals(MaxFeedback.Hint, harness.capturedState.maxHint)
        }

    @Test
    fun changingDirectionHidesTheHint() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()

            harness.onChangeButtonClick()

            assertNull(harness.capturedState.maxHint)
        }

    @Test
    fun aSecondMaxKeepsTheHint() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.onMaxClick()

            assertEquals(MaxFeedback.Hint, harness.capturedState.maxHint)
        }

    @Test
    fun theHintReachesTheSlotAndAFailureWinsOverIt() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))

            assertNull(mapped(harness).maxFeedback)
            assertEquals(MaxFeedback.Hint, mapped(harness, maxHint = MaxFeedback.Hint).maxFeedback)
            val error = MaxFeedback.Error(MaxVariant.SWAP)
            assertEquals(error, mapped(harness, maxError = error).maxFeedback)
            assertEquals(error, mapped(harness, maxHint = MaxFeedback.Hint, maxError = error).maxFeedback)
        }

    @Test
    fun maxFailureUsesTheSwapErrorCopy() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()

            assertEquals(MaxFeedback.Error(MaxVariant.SWAP), harness.capturedState.maxError)
            assertEquals(stringRes(R.string.general_maxUnavailableSwap), harness.capturedState.maxError?.text)
        }

    // Chip and "Get a quote" gating (real mapper)

    @Test
    fun theChipIsEnabledOnlyWithSpendableFundsAndHasNoSpinner() =
        runTest {
            assertEquals(true, mapped(maxHarness(max = Zatoshi(1))).amountTextField.max?.isEnabled)
            listOf(
                nothingAccount(shieldable = true, pending = false),
                nothingAccount(shieldable = false, pending = true),
                nothingAccount(shieldable = false, pending = false),
            ).forEach { account ->
                val harness = maxHarness(max = Zatoshi(1), account = account)
                assertEquals(false, mapped(harness).amountTextField.max?.isEnabled)
            }
        }

    @Test
    fun theChipStaysEnabledWhileMaxIsComputing() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()

            assertEquals(true, mapped(harness).amountTextField.max?.isEnabled)
            gate.complete(Unit)
        }

    @Test
    fun getAQuoteNeedsSpendableFundsOnTopOfTheExistingRules() =
        runTest {
            val withFunds = maxHarness(max = Zatoshi(1))
            withFunds.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("1")))
            withFunds.onAddressChange("destination-address")
            assertEquals(true, mapped(withFunds).primaryButton?.isEnabled)

            listOf(
                nothingAccount(shieldable = true, pending = false),
                nothingAccount(shieldable = false, pending = true),
                nothingAccount(shieldable = false, pending = false),
                null,
            ).forEach { account ->
                // The direction can only be flipped once the account has loaded as spendable, so the
                // not-loaded case is reached by clearing the account after the flip
                val harness = maxHarness(max = Zatoshi(1), account = account ?: spendableAccount(Zatoshi(1)))
                if (account == null) harness.accountFlow.value = null
                harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("1")))
                harness.onAddressChange("destination-address")
                assertEquals(false, mapped(harness).primaryButton?.isEnabled, account.toString())
            }
        }

    // The Spendable pill (real mapper)

    @Test
    fun theFromCardShowsTheTappableSpendablePillAndNavigatesToTheSheet() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            val pill = mapped(harness).amountTextField.spendable

            assertEquals(
                stringRes(
                    co.electriccoin.zcash.ui.R.string.swapAndPay_max,
                    stringRes(Zatoshi(500_000_000), TickerLocation.HIDDEN).asPrivacySensitive()
                ),
                pill?.text
            )
            assertEquals(false, pill?.isLoading)
            harness.onSpendableClick()
            verify { harness.navigationRouter.forward(SpendableBalanceArgs) }
        }

    @Test
    fun noPillIntoZec() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.onChangeButtonClick()

            assertNull(mapped(harness).amountTextField.spendable)
        }
}
