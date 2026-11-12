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

/** [SendViewModel] Max: the chip, the request and the memo it is priced with (see [SendVmMaxHarness]). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendViewModelMaxTest {
    private val harness = SendVmMaxHarness()

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    @Test
    fun invalidAddressDoesNothing() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient(address = "bad", type = AddressType.Invalid())

            vm.onMaxClick()

            coVerify(exactly = 0) { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) }
        }

    @Test
    fun noRecipientTypeDoesNothing() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient(type = null)

            vm.onMaxClick()

            coVerify(exactly = 0) { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) }
        }

    @Test
    fun validAddressResolvesMaxAmountForThatAddress() =
        runTest {
            val vm = harness.newViewModel()
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()

            vm.onMaxClick()

            assertEquals(listOf(ResolvedMaxAmount(UNIFIED, MAX.amount)), resolved)
            assertTrue(vm.maxChipState.value.isEnabled)
        }

    @Test
    fun failureClearsInFlightEmitsFailureAndResolvesNothing() =
        runTest {
            harness.failWithUnexpected(IllegalStateException("x"))
            val vm = harness.newViewModel()
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()

            vm.onMaxClick()

            assertEquals(MaxFeedback.Error(MaxVariant.SEND), vm.maxFeedbackState.value)
            assertNull(vm.tooManyNotesSheet.value)
            assertTrue(resolved.isEmpty())
            assertTrue(vm.maxChipState.value.isEnabled)
        }

    @Test
    fun clearingTheViewModelCancelsTheInFlightRequest() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var cancelled = false
            coEvery { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } coAnswers {
                try {
                    gate.await()
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
                MAX
            }
            val store = ViewModelStore()
            val vm =
                ViewModelProvider(
                    store,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = harness.newViewModel() as T
                    }
                )[SendViewModel::class.java]
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()
            vm.onMaxClick()

            store.clear()
            gate.complete(Unit)

            assertTrue(cancelled)
            assertTrue(resolved.isEmpty())
        }

    @Test
    fun recipientChangeCancelsTheInFlightRequest() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            harness.gatedProposal(gate)
            val vm = harness.newViewModel()
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()
            vm.onMaxClick()

            vm.recipient(address = OTHER)
            gate.complete(Unit)

            assertTrue(resolved.isEmpty())
        }

    @Test
    fun changingTheRecipientAfterAMaxFillClearsTheAmount() =
        runTest {
            val vm = harness.newViewModel()
            val cleared = mutableListOf<Unit>()
            backgroundScope.launch(harness.mainDispatcher) { vm.amountClears.toList(cleared) }
            vm.recipient()
            vm.onMaxClick()

            vm.recipient(address = OTHER)

            assertEquals(1, cleared.size)
        }

    @Test
    fun changingTheRecipientEndsTheMaxIntentSoASecondChangeClearsNothing() =
        runTest {
            val vm = harness.newViewModel()
            val cleared = mutableListOf<Unit>()
            backgroundScope.launch(harness.mainDispatcher) { vm.amountClears.toList(cleared) }
            vm.recipient()
            vm.onMaxClick()

            vm.recipient(address = OTHER)
            vm.recipient(address = UNIFIED)

            assertEquals(1, cleared.size)
        }

    @Test
    fun changingTheRecipientWithoutAMaxFillClearsNothing() =
        runTest {
            val vm = harness.newViewModel()
            val cleared = mutableListOf<Unit>()
            backgroundScope.launch(harness.mainDispatcher) { vm.amountClears.toList(cleared) }
            vm.recipient()

            vm.recipient(address = OTHER)

            assertTrue(cleared.isEmpty())
        }

    @Test
    fun anEditedAmountEndsTheMaxIntentSoARecipientChangeKeepsIt() =
        runTest {
            val vm = harness.newViewModel()
            val cleared = mutableListOf<Unit>()
            backgroundScope.launch(harness.mainDispatcher) { vm.amountClears.toList(cleared) }
            vm.recipient()
            vm.onMaxClick()
            vm.onAmountEdited()

            vm.recipient(address = OTHER)

            assertTrue(cleared.isEmpty())
        }

    @Test
    fun aResultResolvedBeforeAnyCollectorIsDeliveredToTheNextOne() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()

            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }

            assertEquals(listOf(ResolvedMaxAmount(UNIFIED, MAX.amount)), resolved)
        }

    @Test
    fun aResultIsDeliveredOnlyOnce() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            val first = mutableListOf<ResolvedMaxAmount>()
            val job = backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(first) }
            job.cancel()

            val second = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(second) }

            assertTrue(second.isEmpty())
        }

    @Test
    fun sameRecipientRepublishedDoesNotCancelTheRequest() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            harness.gatedProposal(gate)
            val vm = harness.newViewModel()
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()
            vm.onMaxClick()

            vm.recipient()
            gate.complete(Unit)

            assertEquals(1, resolved.size)
        }

    @Test
    fun resultForAStaleAddressIsDropped() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            // A request that outlives its cancellation, as a non cancellable native call would
            harness.gatedProposal(gate, nonCancellable = true)
            val vm = harness.newViewModel()
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()
            vm.onMaxClick()

            vm.recipient(address = OTHER)
            gate.complete(Unit)

            assertTrue(resolved.isEmpty())
        }

    @Test
    fun staleRequestDoesNotClearTheFlagOfItsSuccessor() =
        runTest {
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            harness.gatedProposal(first, nonCancellable = true)
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()
            vm.recipient(address = OTHER)
            harness.gatedProposal(second)
            vm.onMaxClick()

            first.complete(Unit)

            // the stale request's end must not release the guard of the current one: this re-tap is still ignored
            vm.onMaxClick()
            coVerify(exactly = 2) { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) }
            second.complete(Unit)
        }

    @Test
    fun tappingReviewCancelsTheInFlightRequest() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            harness.gatedProposal(gate)
            val vm = harness.newViewModel()
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()
            vm.onMaxClick()

            vm.onCreateZecSendClick(
                newZecSend = mockk<ZecSend>(),
                amountState = AmountState.newFromZec("1", "", false, ExchangeRateState.OptedOut),
                setSendStage = {}
            )
            gate.complete(Unit)

            assertTrue(resolved.isEmpty())
            coVerify { harness.createProposal(any(), any()) }
        }

    @Test
    fun secondTapWhileInFlightIsIgnored() =
        runTest {
            harness.gatedProposal(CompletableDeferred())
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMaxClick()

            vm.onMaxClick()

            coVerify(exactly = 1) { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) }
        }

    private suspend fun memoFor(type: AddressType, address: String, text: String): MemoContent? {
        var sent: MemoContent? = null
        coEvery { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } coAnswers {
            sent = thirdArg()
            MAX
        }
        val vm = harness.newViewModel()
        vm.recipient(address, type)
        vm.onMemoChanged(text)
        vm.onMaxClick()
        return sent
    }

    @Test
    fun memoIsPassedForShieldedRecipients() =
        runTest {
            assertEquals(MemoContent.fromString("max memo"), memoFor(AddressType.Unified, UNIFIED, "max memo"))
            assertEquals(MemoContent.fromString("max memo"), memoFor(AddressType.Shielded, UNIFIED, "max memo"))
        }

    @Test
    fun noMemoIsPassedForTransparentAndTexRecipients() =
        runTest {
            assertNull(memoFor(AddressType.Transparent, "t1", "should not ride along"))
            assertNull(memoFor(AddressType.Tex, "tex1", "should not ride along"))
        }

    @Test
    fun enablementMatrix() =
        runTest {
            val vm = harness.newViewModel()

            // No recipient yet
            assertEquals(false, vm.maxChipState.value.isEnabled)

            vm.recipient()
            assertTrue(vm.maxChipState.value.isEnabled)

            vm.recipient(address = "bad", type = AddressType.Invalid())
            assertFalse(vm.maxChipState.value.isEnabled)
            vm.recipient()

            harness.accounts.value = account(spendable = 0, pending = 500)
            assertFalse(vm.maxChipState.value.isEnabled)

            harness.accounts.value = account(spendable = 1, pending = 0)
            assertTrue(vm.maxChipState.value.isEnabled)

            harness.accounts.value = null
            assertFalse(vm.maxChipState.value.isEnabled)
        }

    @Test
    fun undeterminedSpendabilityDisablesTheChipUntilDetermined() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient()

            harness.accounts.value = account(spendable = null, pending = null)
            assertFalse(vm.maxChipState.value.isEnabled)

            harness.accounts.value = account(spendable = 10, pending = 0)
            assertTrue(vm.maxChipState.value.isEnabled)
        }

    @Test
    fun zeroSpendableDisablesTheChip() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient()

            harness.accounts.value = account(spendable = 0, pending = 0)

            assertFalse(vm.maxChipState.value.isEnabled)
        }

    /** Figma round 2: no spinner. The chip stays enabled while the request runs and the in-flight guard eats taps. */
    @Test
    fun inFlightRequestKeepsTheChipEnabledAndIgnoresRetaps() =
        runTest {
            harness.gatedProposal(CompletableDeferred())
            val vm = harness.newViewModel()
            vm.recipient()

            vm.onMaxClick()
            vm.onMaxClick()

            assertTrue(vm.maxChipState.value.isEnabled)
            coVerify(exactly = 1) { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) }
        }

    @Test
    fun theChipClickRequestsTheMaxWithoutHelpFromTheView() =
        runTest {
            val vm = harness.newViewModel()
            val resolved = mutableListOf<ResolvedMaxAmount>()
            backgroundScope.launch(harness.mainDispatcher) { vm.maxAmountResolved.toList(resolved) }
            vm.recipient()

            vm.maxChipState.value.onClick()

            assertEquals(listOf(ResolvedMaxAmount(UNIFIED, MAX.amount)), resolved)
        }

    /** The same click action is published every time, so the chip does not recompose for a new lambda. */
    @Test
    fun theChipStateIsStableAcrossEmissions() =
        runTest {
            val vm = harness.newViewModel()
            vm.recipient()
            val before = vm.maxChipState.value

            harness.accounts.value = account(spendable = 2_000_000, pending = 0)

            assertEquals(before, vm.maxChipState.value)
        }

    @Test
    fun theMemoIsTakenWhenMaxIsTapped() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var sent: MemoContent? = null
            coEvery { harness.maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } coAnswers {
                sent = thirdArg()
                gate.await()
                MAX
            }
            val vm = harness.newViewModel()
            vm.recipient()
            vm.onMemoChanged("at tap")

            vm.onMaxClick()
            vm.onMemoChanged("typed later")
            gate.complete(Unit)

            assertEquals(MemoContent.fromString("at tap"), sent)
        }
}
