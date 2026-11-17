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
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
class SwapVMMaxFeedbackTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Failure presentation: banner vs too-many-notes sheet

    @Test
    fun genericFailuresShowTheBannerNotTheSheet() =
        runTest {
            listOf(
                MaxSendAmountFailure.OTHER,
                MaxSendAmountFailure.SYNC_REQUIRED,
                MaxSendAmountFailure.INVALID_RECIPIENT
            ).forEach { failure ->
                val harness = maxHarness(max = Zatoshi(1))
                coEvery { harness.getMaxSendAmount(any()) } throws MaxSendAmountUnavailableException(failure)
                harness.onMaxClick()

                assertEquals(true, harness.isMaxBannerVisible, failure.name)
                assertNull(harness.maxSheet, failure.name)
            }
        }

    @Test
    fun tooManyNotesShowsTheSheetAndNoBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.TOO_MANY_NOTES)
            harness.onMaxClick()

            assertNotNull(harness.maxSheet)
            assertEquals(false, harness.isMaxBannerVisible)
            assertNull(harness.capturedState.amountTextState.amount)
        }

    @Test
    fun tooManyNotesAfterABannerReplacesTheBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()
            harness.failMax(MaxSendAmountFailure.TOO_MANY_NOTES)
            harness.onMaxClick()

            assertNotNull(harness.maxSheet)
            assertEquals(false, harness.isMaxBannerVisible)
        }

    @Test
    fun sheetBackKeepsTheFilledAmount() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick()
            harness.failMax(MaxSendAmountFailure.TOO_MANY_NOTES)
            harness.onMaxClick()

            harness.maxSheet!!.onBack()

            assertNull(harness.maxSheet)
            assertEquals(0, BigDecimal("1").compareTo(harness.capturedState.amountTextState.amount))
            assertEquals(0, harness.focusRequestCount)
        }

    @Test
    fun sheetBackDismissesTheSheet() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.TOO_MANY_NOTES)
            harness.onMaxClick()

            harness.maxSheet!!.onBack()

            assertNull(harness.maxSheet)
        }

    @Test
    fun okDismissesClearsAndFocusesTheAmount() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.onMaxClick() // fills the amount and the buffer hint
            harness.failMax(MaxSendAmountFailure.TOO_MANY_NOTES)
            harness.onMaxClick()

            harness.maxSheet!!.ok.onClick()

            assertNull(harness.maxSheet)
            assertEquals(NumberTextFieldInnerState(), harness.capturedState.amountTextState)
            assertNull(harness.capturedState.maxHint)
            assertEquals(false, harness.isMaxBannerVisible)
            assertEquals(1, harness.focusRequestCount)
        }

    // Banner clearing rules

    @Test
    fun maxRetapClearsTheBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000), hang = true)
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()
            assertEquals(true, harness.isMaxBannerVisible)

            val gate = harness.makeMaxHang(Zatoshi(100_000_000))
            harness.onMaxClick()

            assertEquals(false, harness.isMaxBannerVisible)
            gate.complete(Unit)
        }

    @Test
    fun maxRetapThatFailsAgainShowsTheBannerAgain() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()
            harness.onMaxClick()

            assertEquals(true, harness.isMaxBannerVisible)
        }

    @Test
    fun maxRetapThatSucceedsClearsTheBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(100_000_000))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()
            coEvery { harness.getMaxSendAmount(any()) } returns
                MaxSendAmount(amount = Zatoshi(100_000_000), fee = Zatoshi(10_000))
            harness.onMaxClick()

            assertEquals(false, harness.isMaxBannerVisible)
        }

    @Test
    fun editingTheAmountClearsTheBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()

            harness.onTextFieldChange(NumberTextFieldInnerState.fromAmount(BigDecimal("2")))

            assertEquals(false, harness.isMaxBannerVisible)
        }

    @Test
    fun aNonEditChangeKeepsTheBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()

            harness.onTextFieldChange(harness.capturedState.amountTextState) // e.g. focus or selection only

            assertEquals(true, harness.isMaxBannerVisible)
        }

    @Test
    fun changingDirectionClearsTheBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()

            harness.onChangeButtonClick()

            assertEquals(false, harness.isMaxBannerVisible)
        }

    @Test
    fun changingTheAssetClearsTheBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1), assetResult = SwapAssetTestFixture.asset(tokenTicker = "btc"))
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()

            harness.onSwapAssetPickerClick()

            assertEquals(false, harness.isMaxBannerVisible)
        }

    @Test
    fun cancellingTheAssetPickerKeepsTheBanner() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1), assetResult = null)
            harness.failMax(MaxSendAmountFailure.OTHER)
            harness.onMaxClick()

            harness.onSwapAssetPickerClick()

            assertEquals(true, harness.isMaxBannerVisible)
        }

    // The error line reaches the screen state (real mapper)

    @Test
    fun theErrorLineIsShownOnlyWhileFlagged() =
        runTest {
            val harness = maxHarness(max = Zatoshi(1))
            assertNull(mapped(harness).maxFeedback)

            val error = MaxFeedback.Error(MaxVariant.SWAP)

            assertEquals(error, mapped(harness, maxError = error).maxFeedback)
        }

    /** The swap's error footer owns the slot above the button, so it hides the Max feedback. */
    @Test
    fun theErrorFooterTakesTheMaxFeedbackSlot() =
        runTest {
            val harness =
                maxHarness(
                    max = Zatoshi(1),
                    account = nothingAccount(shieldable = true, pending = false),
                    assets = SwapAssetTestFixture.assetsData(error = IllegalStateException("assets"))
                )

            val state = mapped(harness, maxError = MaxFeedback.Error(MaxVariant.SWAP), maxHint = MaxFeedback.Hint)

            assertNotNull(state.errorFooter)
            assertNull(state.maxFeedback)
        }
}
