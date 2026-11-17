package co.electriccoin.zcash.ui.screen.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.model.MemoContent
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.TransactionProposalNotCreatedException
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxVariant
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.send.SendVmMaxHarness.Companion.MAX
import co.electriccoin.zcash.ui.screen.send.SendVmMaxHarness.Companion.OTHER
import co.electriccoin.zcash.ui.screen.send.SendVmMaxHarness.Companion.UNIFIED
import co.electriccoin.zcash.ui.screen.send.SendVmMaxHarness.Companion.account
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.ResolvedMaxAmount
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [SendViewModel] Max: the failure line and the too-many-notes sheet (see [SendVmMaxHarness]). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendViewModelMaxFeedbackTest {
    private val harness = SendVmMaxHarness()

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    @Test
    fun errorForEveryNonSheetFailure() =
        runTest {
            listOf(
                MaxSendAmountFailure.OTHER,
                MaxSendAmountFailure.SYNC_REQUIRED,
                MaxSendAmountFailure.INVALID_RECIPIENT
            ).forEach { failure ->
                harness.failWith(failure)
                val vm = harness.newViewModel()
                vm.recipient()

                vm.onMaxClick()

                assertEquals(MaxFeedback.Error(MaxVariant.SEND), vm.maxFeedbackState.value, failure.name)
                assertNull(vm.tooManyNotesSheet.value, failure.name)
            }
        }

    @Test
    fun insufficientFundsAtMaxShowsTheError() =
        runTest {
            harness.failWithUnexpected(InsufficientFundsException(RuntimeException()))
            val vm = harness.newViewModel()
            vm.recipient()

            vm.onMaxClick()

            assertNotNull(vm.maxFeedbackState.value)
        }

    @Test
    fun errorClearsWhenMaxIsTappedAgain() =
        runTest {
            harness.failWith(MaxSendAmountFailure.OTHER)
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            assertNotNull(vm.maxFeedbackState.value)

            val gate = CompletableDeferred<Unit>()
            harness.gatedProposal(gate)
            vm.onMaxClick()

            assertNull(vm.maxFeedbackState.value)
            gate.complete(Unit)
            // the retap succeeded: the error is gone and the fee hint of the active Max fill shows instead
            assertEquals(MaxFeedback.SendHint, vm.maxFeedbackState.value)
        }

    @Test
    fun errorStaysWhenTheMaxTapIsIgnored() =
        runTest {
            harness.failWith(MaxSendAmountFailure.OTHER)
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            harness.accounts.value = account(spendable = 0, pending = 0)

            vm.onMaxClick()

            assertNotNull(vm.maxFeedbackState.value)
        }

    @Test
    fun errorClearsWhenTheAmountIsEdited() =
        runTest {
            harness.failWith(MaxSendAmountFailure.OTHER)
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            assertNotNull(vm.maxFeedbackState.value)

            vm.onAmountEdited()

            assertNull(vm.maxFeedbackState.value)
        }

    @Test
    fun errorClearsWhenTheAddressChanges() =
        runTest {
            harness.failWith(MaxSendAmountFailure.OTHER)
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            assertNotNull(vm.maxFeedbackState.value)

            vm.recipient(address = OTHER)

            assertNull(vm.maxFeedbackState.value)
        }

    @Test
    fun errorStaysWhenTheSameAddressIsRepublished() =
        runTest {
            harness.failWith(MaxSendAmountFailure.OTHER)
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()

            vm.recipient()

            assertNotNull(vm.maxFeedbackState.value)
        }

    private fun tooLarge() =
        TransactionProposalNotCreatedException(
            TransactionEncoderException.ProposalTooLargeException(RuntimeException())
        )

    @Test
    fun sheetWhenMaxFailsWithTooManyNotes() =
        runTest {
            harness.failWith(MaxSendAmountFailure.TOO_MANY_NOTES)
            val vm = harness.newViewModel()
            vm.recipient()

            vm.onMaxClick()

            assertNotNull(vm.tooManyNotesSheet.value)
            assertNull(vm.maxFeedbackState.value)
        }

    @Test
    fun theReviewSheetOkAlsoClearsAnEarlierMaxNote() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            harness.failWith(MaxSendAmountFailure.OTHER)
            vm.onMaxClick()
            assertNotNull(vm.maxFeedbackState.value)

            vm.onTooManyNotesOk()

            assertNull(vm.maxFeedbackState.value)
        }

    @Test
    fun sheetReplacesAnExistingError() =
        runTest {
            harness.failWith(MaxSendAmountFailure.OTHER)
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            assertNotNull(vm.maxFeedbackState.value)

            harness.failWith(MaxSendAmountFailure.TOO_MANY_NOTES)
            vm.onMaxClick()

            assertNotNull(vm.tooManyNotesSheet.value)
            assertNull(vm.maxFeedbackState.value)
        }

    @Test
    fun tooManyNotesStageAtReviewWhenTheProposalIsTooLarge() =
        runTest {
            coEvery { harness.createProposal(any(), any()) } throws tooLarge()
            val vm = harness.newViewModel()
            var stage: SendStage? = null

            vm.onCreateZecSendClick(
                newZecSend = mockk<ZecSend>(),
                amountState = AmountState.newFromZec("1", "", false, ExchangeRateState.OptedOut),
                setSendStage = { stage = it }
            )

            // The proposal-time sheet is the stage's (MOB-2296), never the raw backend text, and never both sheets
            assertEquals(SendStage.TooManyNotes, stage)
            assertNull(vm.tooManyNotesSheet.value)
        }

    @Test
    fun tooManyNotesStageOkClearsTheAmountAndFocusesTheField() =
        runTest {
            val vm = harness.newViewModel()
            val cleared = mutableListOf<Unit>()
            val focused = mutableListOf<Unit>()
            backgroundScope.launch(harness.mainDispatcher) { vm.amountClears.toList(cleared) }
            backgroundScope.launch(harness.mainDispatcher) { vm.amountFocusRequests.toList(focused) }

            vm.onTooManyNotesOk()

            assertEquals(1, cleared.size)
            assertEquals(1, focused.size)
        }

    @Test
    fun tooManyNotesStageAtReviewWhenTheTypedExceptionIsNotWrapped() =
        runTest {
            coEvery { harness.createProposal(any(), any()) } throws
                TransactionEncoderException.ProposalTooLargeException(RuntimeException())
            val vm = harness.newViewModel()
            var stage: SendStage? = null

            vm.onCreateZecSendClick(
                newZecSend = mockk<ZecSend>(),
                amountState = AmountState.newFromZec("1", "", false, ExchangeRateState.OptedOut),
                setSendStage = { stage = it }
            )

            assertEquals(SendStage.TooManyNotes, stage)
        }

    /** The create/PCZT path carries the size gate as the root cause of a not-created failure, not as its cause. */
    @Test
    fun tooManyNotesStageAtReviewWhenTheSizeGateIsTheRootCauseOfANotCreatedFailure() =
        runTest {
            coEvery { harness.createProposal(any(), any()) } throws
                TransactionEncoderException.TransactionNotCreatedException(
                    TransactionEncoderException.ProposalTooLargeException(RuntimeException())
                )
            val vm = harness.newViewModel()
            var stage: SendStage? = null

            vm.onCreateZecSendClick(
                newZecSend = mockk<ZecSend>(),
                amountState = AmountState.newFromZec("1", "", false, ExchangeRateState.OptedOut),
                setSendStage = { stage = it }
            )

            assertEquals(SendStage.TooManyNotes, stage)
            assertNull(vm.tooManyNotesSheet.value)
        }

    @Test
    fun otherReviewFailuresKeepTheFailureStageAndNoSheet() =
        runTest {
            coEvery { harness.createProposal(any(), any()) } throws IllegalStateException("boom")
            val vm = harness.newViewModel()
            var stage: SendStage? = null

            vm.onCreateZecSendClick(
                newZecSend = mockk<ZecSend>(),
                amountState = AmountState.newFromZec("1", "", false, ExchangeRateState.OptedOut),
                setSendStage = { stage = it }
            )

            assertEquals(SendStage.SendFailure("boom"), stage)
            assertNull(vm.tooManyNotesSheet.value)
        }

    @Test
    fun backDismissesTheSheet() =
        runTest {
            harness.failWith(MaxSendAmountFailure.TOO_MANY_NOTES)
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()

            vm.tooManyNotesSheet.value
                ?.onBack
                ?.invoke()

            assertNull(vm.tooManyNotesSheet.value)
        }

    @Test
    fun backOnlyDismissesTheSheetWithoutClearingTheAmount() =
        runTest {
            harness.failWith(MaxSendAmountFailure.TOO_MANY_NOTES)
            val vm = harness.newViewModel()
            val cleared = mutableListOf<Unit>()
            val focused = mutableListOf<Unit>()
            backgroundScope.launch(harness.mainDispatcher) { vm.amountClears.toList(cleared) }
            backgroundScope.launch(harness.mainDispatcher) { vm.amountFocusRequests.toList(focused) }
            vm.recipient()
            vm.onMaxClick()

            vm.tooManyNotesSheet.value
                ?.onBack
                ?.invoke()

            assertNull(vm.tooManyNotesSheet.value)
            assertTrue(cleared.isEmpty())
            assertTrue(focused.isEmpty())
        }

    @Test
    fun okDismissesClearsTheAmountAndFocusesTheField() =
        runTest {
            harness.failWith(MaxSendAmountFailure.TOO_MANY_NOTES)
            val vm = harness.newViewModel()
            val cleared = mutableListOf<Unit>()
            val focused = mutableListOf<Unit>()
            backgroundScope.launch(harness.mainDispatcher) { vm.amountClears.toList(cleared) }
            backgroundScope.launch(harness.mainDispatcher) { vm.amountFocusRequests.toList(focused) }
            vm.recipient()
            vm.onMaxClick()
            val sheet = vm.tooManyNotesSheet.value

            sheet
                ?.ok
                ?.onClick
                ?.invoke()

            assertNull(vm.tooManyNotesSheet.value)
            assertEquals(1, cleared.size)
            assertEquals(1, focused.size)
            // the same sheet instance is reused, so its button is stable across shows
            vm.onMaxClick()
            assertSame(sheet, vm.tooManyNotesSheet.value)
        }

    @Test
    fun theReviewSheetOkEndsTheMaxIntentSoARecipientChangePricesNothing() =
        runTest {
            val vm = harness.newViewModel()
            val cleared = mutableListOf<Unit>()
            backgroundScope.launch(harness.mainDispatcher) { vm.amountClears.toList(cleared) }
            vm.recipient()
            vm.onMaxClick()
            assertEquals(MaxFeedback.SendHint, vm.maxFeedbackState.value)

            vm.onTooManyNotesOk()

            assertEquals(1, cleared.size)
            // the fee hint went with the Max intent
            assertNull(vm.maxFeedbackState.value)
            vm.recipient(address = OTHER)
            coVerify(exactly = 1) { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) }
        }

    @Test
    fun theMaxSheetOkAfterAFailedRetapEndsTheMaxIntent() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            harness.failWith(MaxSendAmountFailure.TOO_MANY_NOTES)
            vm.onMaxClick()
            assertNotNull(vm.tooManyNotesSheet.value)

            vm.tooManyNotesSheet.value
                ?.ok
                ?.onClick
                ?.invoke()

            assertNull(vm.tooManyNotesSheet.value)
            assertNull(vm.maxFeedbackState.value)
            vm.recipient(address = OTHER)
            // the tap and the failed retap only: the amount is not priced again for the new recipient
            coVerify(exactly = 2) { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) }
        }

    @Test
    fun theSheetOkWhileMaxRunsDropsItsResult() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            harness.gatedProposal(gate)
            val vm = harness.newViewModel()
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()
            vm.onMaxClick()

            vm.onTooManyNotesOk()
            gate.complete(Unit)

            assertTrue(resolved.isEmpty())
            assertFalse(vm.maxChipState.value.isRequesting)
            assertNull(vm.maxFeedbackState.value)
        }
}
