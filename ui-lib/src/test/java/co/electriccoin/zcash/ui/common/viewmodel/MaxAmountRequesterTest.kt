package co.electriccoin.zcash.ui.common.viewmodel

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountUnavailableException
import co.electriccoin.zcash.ui.common.usecase.GetMaxSendAmountUseCase
import co.electriccoin.zcash.ui.common.usecase.MaxSendTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The requester runs in `backgroundScope`, whose work only `runCurrent` (not `advanceUntilIdle`) executes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaxAmountRequesterTest {
    private val getMaxSendAmount = mockk<GetMaxSendAmountUseCase>()
    private val failures = mutableListOf<MaxSendAmountFailure>()
    private val resolved = mutableListOf<MaxSendAmount>()

    private fun TestScope.requester() =
        MaxAmountRequester(scope = backgroundScope, getMaxSendAmount = getMaxSendAmount, onFailed = { failures += it })

    private fun MaxAmountRequester.requestMax() = request(MaxSendTarget.OwnTransparentReceiver) { resolved += it }

    /** Answers each call with the next deferred, so a test decides when (and whether) every request completes. */
    private fun answers(vararg results: CompletableDeferred<MaxSendAmount>) {
        val queue = ArrayDeque(results.toList())
        coEvery { getMaxSendAmount(any()) } coAnswers { queue.removeFirst().await() }
    }

    @Test
    fun aFailedTapEndsTheIntentOfTheEarlierOne() =
        runTest {
            val unavailable = MaxSendAmountUnavailableException(MaxSendAmountFailure.OTHER, RuntimeException())
            coEvery { getMaxSendAmount(any()) } returnsMany listOf(MAX) andThenThrows unavailable
            val requester = requester()
            requester.request(MaxSendTarget.OwnTransparentReceiver, spendableAtTap = Zatoshi(110_000)) { }
            runCurrent()
            assertEquals(MAX.amount, requester.activeBaseline.value)

            requester.request(MaxSendTarget.OwnTransparentReceiver, spendableAtTap = Zatoshi(110_000)) { }
            runCurrent()

            assertNull(requester.activeBaseline.value)
            assertNull(requester.activeReserve.value)
            assertEquals(listOf(MaxSendAmountFailure.OTHER), failures)
        }

    @Test
    fun aRequesterThatKeepsItsIntentOnFailureKeepsTheEarlierTapsBaseline() =
        runTest {
            val unavailable = MaxSendAmountUnavailableException(MaxSendAmountFailure.OTHER, RuntimeException())
            coEvery { getMaxSendAmount(any()) } returnsMany listOf(MAX) andThenThrows unavailable
            val requester =
                MaxAmountRequester(
                    scope = backgroundScope,
                    getMaxSendAmount = getMaxSendAmount,
                    onFailed = { failures += it },
                    endsIntentOnFailure = false
                )
            requester.request(MaxSendTarget.OwnTransparentReceiver) { }
            runCurrent()

            requester.request(MaxSendTarget.OwnTransparentReceiver) { }
            runCurrent()

            assertEquals(MAX.amount, requester.activeBaseline.value)
            assertEquals(listOf(MaxSendAmountFailure.OTHER), failures)
        }

    @Test
    fun aConsumerThatThrowsIsReportedAsAFailedMaxInsteadOfCrashing() =
        runTest {
            answers(CompletableDeferred(MAX))
            val requester = requester()
            var failureCalls = 0

            requester.request(MaxSendTarget.OwnTransparentReceiver, onFailure = { failureCalls++ }) {
                error("consumer failed")
            }
            runCurrent()

            assertEquals(listOf(MaxSendAmountFailure.OTHER), failures)
            assertEquals(1, failureCalls)
            assertNull(requester.activeBaseline.value)
            assertFalse(requester.isInFlight.value)
        }

    @Test
    fun theFlagIsRaisedBeforeTheRequestRuns() =
        runTest {
            answers(CompletableDeferred())
            val requester = requester()

            assertTrue(requester.requestMax())

            // Nothing has been dispatched yet
            assertTrue(requester.isInFlight.value)
            coVerify(exactly = 0) { getMaxSendAmount(any()) }
        }

    @Test
    fun theResultIsDeliveredAndTheFlagCleared() =
        runTest {
            answers(CompletableDeferred(MAX))
            val requester = requester()

            requester.requestMax()
            runCurrent()

            assertEquals(listOf(MAX), resolved)
            assertFalse(requester.isInFlight.value)
            assertTrue(failures.isEmpty())
        }

    @Test
    fun theReserveIsTheSpendableAtTapMinusTheBaseline() =
        runTest {
            answers(CompletableDeferred(MAX))
            val requester = requester()

            requester.request(MaxSendTarget.OwnTransparentReceiver, spendableAtTap = Zatoshi(110_000)) { }
            runCurrent()

            assertEquals(Zatoshi(90_000), requester.activeBaseline.value)
            assertEquals(Zatoshi(20_000), requester.activeReserve.value)
        }

    @Test
    fun clearIntentEndsTheReserveToo() =
        runTest {
            answers(CompletableDeferred(MAX))
            val requester = requester()
            requester.request(MaxSendTarget.OwnTransparentReceiver, spendableAtTap = Zatoshi(110_000)) { }
            runCurrent()

            requester.clearIntent()

            assertNull(requester.activeBaseline.value)
            assertNull(requester.activeReserve.value)
        }

    @Test
    fun noReserveIsRecordedWithoutASpendableAtTap() =
        runTest {
            answers(CompletableDeferred(MAX))
            val requester = requester()

            requester.requestMax()
            runCurrent()

            assertNull(requester.activeReserve.value)
        }

    @Test
    fun aSecondTapWhileInFlightIsIgnored() =
        runTest {
            val first = CompletableDeferred<MaxSendAmount>()
            answers(first)
            val requester = requester()

            assertTrue(requester.requestMax())
            assertFalse(requester.requestMax())
            runCurrent()
            assertFalse(requester.requestMax())
            first.complete(MAX)
            runCurrent()

            coVerify(exactly = 1) { getMaxSendAmount(any()) }
            assertEquals(listOf(MAX), resolved)
        }

    @Test
    fun aTapAfterCompletionStartsANewRequest() =
        runTest {
            answers(CompletableDeferred(MAX), CompletableDeferred(OTHER_MAX))
            val requester = requester()

            requester.requestMax()
            runCurrent()
            assertTrue(requester.requestMax())
            runCurrent()

            assertEquals(listOf(MAX, OTHER_MAX), resolved)
        }

    @Test
    fun cancelDropsTheResultAndClearsTheFlag() =
        runTest {
            val first = CompletableDeferred<MaxSendAmount>()
            answers(first)
            val requester = requester()

            requester.requestMax()
            runCurrent()
            requester.cancel()

            assertFalse(requester.isInFlight.value)
            first.complete(MAX)
            runCurrent()
            assertTrue(resolved.isEmpty())
            assertTrue(failures.isEmpty())
        }

    @Test
    fun cancelWithNothingRunningIsHarmless() =
        runTest {
            val requester = requester()

            requester.cancel()

            assertFalse(requester.isInFlight.value)
        }

    /**
     * The cancelled request has not run its `finally` yet when the successor starts (cancellation is cooperative); it
     * must not lower the successor's flag.
     */
    @Test
    fun aCancelledRequestDoesNotClearItsSuccessorsFlag() =
        runTest {
            val first = CompletableDeferred<MaxSendAmount>()
            val second = CompletableDeferred<MaxSendAmount>()
            answers(first, second)
            val requester = requester()

            requester.requestMax()
            runCurrent()
            requester.cancel()
            assertTrue(requester.requestMax())
            // Lets the cancelled request unwind while the successor is suspended
            runCurrent()

            assertTrue(requester.isInFlight.value)
            second.complete(OTHER_MAX)
            runCurrent()
            assertEquals(listOf(OTHER_MAX), resolved)
            assertFalse(requester.isInFlight.value)
        }

    @Test
    fun insufficientFundsIsReportedAsAFailure() =
        runTest {
            coEvery { getMaxSendAmount(any()) } throws InsufficientFundsException()
            val requester = requester()

            requester.requestMax()
            runCurrent()

            assertEquals(listOf(MaxSendAmountFailure.OTHER), failures)
            assertTrue(resolved.isEmpty())
            assertFalse(requester.isInFlight.value)
        }

    @Test
    fun anUnavailableMaxIsReportedAsAFailure() =
        runTest {
            coEvery { getMaxSendAmount(any()) } throws
                MaxSendAmountUnavailableException(MaxSendAmountFailure.TOO_MANY_NOTES)
            val requester = requester()

            requester.requestMax()
            runCurrent()

            assertEquals(listOf(MaxSendAmountFailure.TOO_MANY_NOTES), failures)
            assertFalse(requester.isInFlight.value)
        }

    @Test
    fun everyUnavailableReasonIsPassedThrough() =
        runTest {
            MaxSendAmountFailure.entries.forEach { reason ->
                coEvery { getMaxSendAmount(any()) } throws MaxSendAmountUnavailableException(reason)
                val requester = requester()

                requester.requestMax()
                runCurrent()
            }

            assertEquals(MaxSendAmountFailure.entries.toList(), failures)
        }

    @Test
    fun aFailureDoesNotDeliverAResult() =
        runTest {
            coEvery { getMaxSendAmount(any()) } throws MaxSendAmountUnavailableException(MaxSendAmountFailure.OTHER)
            val requester = requester()

            requester.requestMax()
            runCurrent()

            assertTrue(resolved.isEmpty())
        }

    @Test
    fun cancellationIsNotReportedAsAFailure() =
        runTest {
            coEvery { getMaxSendAmount(any()) } throws CancellationException("cancelled")
            val requester = requester()

            requester.requestMax()
            runCurrent()

            assertTrue(failures.isEmpty())
            assertTrue(resolved.isEmpty())
            assertFalse(requester.isInFlight.value)
        }

    @Test
    fun theTargetIsPassedThrough() =
        runTest {
            val target = MaxSendTarget.Recipient(address = "addr", type = mockk(), memoText = "memo")
            coEvery { getMaxSendAmount(target) } returns MAX
            val requester = requester()

            requester.request(target) { resolved += it }
            runCurrent()

            assertEquals(listOf(MAX), resolved)
        }

    @Test
    fun aSuccessfulMaxPinsTheBaselineToTheFilledAmount() =
        runTest {
            answers(CompletableDeferred(MAX))
            val requester = requester()
            assertNull(requester.activeBaseline.value)

            requester.requestMax()
            runCurrent()

            assertEquals(MAX.amount, requester.activeBaseline.value)
        }

    @Test
    fun aFailedMaxPinsNoBaseline() =
        runTest {
            coEvery { getMaxSendAmount(any()) } throws MaxSendAmountUnavailableException(MaxSendAmountFailure.OTHER)
            val requester = requester()

            requester.requestMax()
            runCurrent()

            assertNull(requester.activeBaseline.value)
        }

    @Test
    fun aCancelledRequestThatStillReturnsPinsNothing() =
        runTest {
            val first = CompletableDeferred<MaxSendAmount>()
            // A call that outlives its cancellation, as a non cancellable native call would
            coEvery { getMaxSendAmount(any()) } coAnswers { withContext(NonCancellable) { first.await() } }
            val requester = requester()
            var delivered = false

            requester.request(MaxSendTarget.OwnTransparentReceiver) { delivered = true }
            runCurrent()
            requester.cancel()
            first.complete(MAX)
            runCurrent()

            assertNull(requester.activeBaseline.value)
            assertNull(requester.activeReserve.value)
            assertFalse(delivered)
        }

    @Test
    fun aCancelledRequestThatFailsLateReportsNothing() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            // A call that outlives its cancellation and then fails, as a non cancellable native call would
            coEvery { getMaxSendAmount(any()) } coAnswers {
                withContext(NonCancellable) { gate.await() }
                throw MaxSendAmountUnavailableException(MaxSendAmountFailure.TOO_MANY_NOTES, RuntimeException())
            }
            val requester = requester()
            var onFailureCalled = false

            requester.request(MaxSendTarget.OwnTransparentReceiver, onFailure = { onFailureCalled = true }) { }
            runCurrent()
            requester.cancel()
            gate.complete(Unit)
            runCurrent()

            assertTrue(failures.isEmpty())
            assertFalse(onFailureCalled)
            assertFalse(requester.isInFlight.value)
        }

    @Test
    fun clearIntentEndsTheBaselineSoRefreshesStop() =
        runTest {
            answers(CompletableDeferred(MAX))
            val requester = requester()
            requester.requestMax()
            runCurrent()

            requester.clearIntent()

            assertNull(requester.activeBaseline.value)
        }

    @Test
    fun aNewMaxTapRepinsTheBaseline() =
        runTest {
            answers(CompletableDeferred(MAX), CompletableDeferred(OTHER_MAX))
            val requester = requester()
            requester.requestMax()
            runCurrent()
            requester.requestMax()
            runCurrent()

            assertEquals(OTHER_MAX.amount, requester.activeBaseline.value)
        }

    private companion object {
        val MAX = MaxSendAmount(amount = Zatoshi(90_000), fee = Zatoshi(10_000))
        val OTHER_MAX = MaxSendAmount(amount = Zatoshi(80_000), fee = Zatoshi(10_000))
    }
}
