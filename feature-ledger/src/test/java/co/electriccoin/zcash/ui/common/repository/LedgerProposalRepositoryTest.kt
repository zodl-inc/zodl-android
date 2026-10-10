package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.ledger.LedgerSigningProgress
import cash.z.ecc.android.sdk.model.Pczt
import co.electriccoin.zcash.ui.common.datasource.LedgerBindingUnusableException
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.model.SubmitResult
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The Ledger signing session: the connect/sign state machine, retry over a kept link versus a
 * rescan, cancellation, submission, and the generation guard that keeps a session that already
 * unwound from overwriting the one that replaced it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerProposalRepositoryTest : LedgerProposalRepositoryTestBase() {
    @Test
    fun progressMapsToTheSigningStatesAsItArrives() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true

            val progress = slot<(LedgerSigningProgress) -> Unit>()
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), capture(progress)) } coAnswers {
                progress.captured(LedgerSigningProgress.IdentifyingDevice)
                assertEquals(LedgerSigningState.Streaming(0, 0), repository.signingState.value)

                progress.captured(LedgerSigningProgress.Streaming(3, 10))
                assertEquals(LedgerSigningState.Streaming(3, 10), repository.signingState.value)

                progress.captured(LedgerSigningProgress.AwaitingReviewOnDevice)
                assertEquals(LedgerSigningState.AwaitingReview, repository.signingState.value)

                progress.captured(LedgerSigningProgress.Signing)
                assertEquals(LedgerSigningState.Signing, repository.signingState.value)

                progress.captured(LedgerSigningProgress.Complete)
                assertEquals(LedgerSigningState.Signing, repository.signingState.value)

                signed
            }

            repository.startSigning()
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun submitPassesTheProofsPcztAndTheSignedPcztToSubmitTransaction() =
        runTest(dispatcher) {
            val proofsPczt = Pczt(byteArrayOf(7))
            givenPczt(ledgerAccount(), proofsPczt = proofsPczt)
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns signed
            val submitResult = mockk<SubmitResult>(relaxed = true)
            coEvery { proposalDataSource.submitTransaction(proofsPczt, signed) } returns submitResult

            repository.startSigning()
            runCurrent()
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)

            val result = repository.submit()

            assertEquals(submitResult, result)
            coVerify(exactly = 1) { proposalDataSource.submitTransaction(proofsPczt, signed) }
        }

    @Test
    fun userRejectedFailsWithSameLinkAndRetrySignsOverTheSameLinkWithoutARescan() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            var signCalls = 0
            val signed = Pczt(byteArrayOf(5))
            val rejected = mockk<LedgerException.UserRejected>(relaxed = true) { every { isRestartable } returns true }
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                signCalls++
                if (signCalls == 1) throw rejected else signed
            }

            repository.startSigning()
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.REJECTED, failed.issue.kind)
            assertEquals(LedgerIssueRetry.SAME_LINK, failed.issue.retry)

            repository.retry()
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any(), any()) }
        }

    @Test
    fun disconnectedFailsWithReconnectAndRetryRescans() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            var linked = false
            every { ledgerSigningDataSource.isLinked } answers { linked }
            coEvery { ledgerSigningDataSource.connect(any(), any()) } answers { linked = true }
            var signCalls = 0
            val signed = Pczt(byteArrayOf(5))
            val disconnected = mockk<LedgerException.Disconnected>(relaxed = true)
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                signCalls++
                if (signCalls == 1) {
                    linked = false
                    throw disconnected
                } else {
                    signed
                }
            }

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueRetry.RECONNECT, failed.issue.retry)

            repository.retry()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            coVerify(exactly = 2) { ledgerSigningDataSource.connect(any(), any()) }
        }

    @Test
    fun cancelSigningClosesTheLinkAndKeepsTheProposalPcztAndProofs() =
        runTest(dispatcher) {
            val proofsPczt = Pczt(byteArrayOf(7))
            val proofsGate = CompletableDeferred<Unit>()
            var proofsCancelled = false
            val pczt =
                givenPczt(
                    ledgerAccount(),
                    proofsPczt = proofsPczt,
                    proofsGate = proofsGate,
                    onProofsCancelled = { proofsCancelled = true }
                )
            runCurrent()
            clearMocks(ledgerSigningDataSource, answers = false)

            repository.startSigning()
            runCurrent()

            repository.cancelSigning()
            runCurrent()

            assertNull(repository.signingState.value)
            coVerify(exactly = 1) { ledgerSigningDataSource.close() }
            assertSame(pczt, repository.getProposalPCZT())
            assertNotNull(repository.transactionProposal.value)
            assertFalse(proofsCancelled)

            every { ledgerSigningDataSource.isLinked } returns true
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns signed
            val submitResult = mockk<SubmitResult>(relaxed = true)
            coEvery { proposalDataSource.submitTransaction(proofsPczt, signed) } returns submitResult
            repository.startSigning()
            runCurrent()
            proofsGate.complete(Unit)
            runCurrent()

            assertEquals(submitResult, repository.submit())
            coVerify(exactly = 1) { proposalDataSource.addProofsToPczt(any()) }
        }

    @Test
    fun cancellingOrClearingWithNoSessionOrLinkLeavesTheLinkAlone() =
        runTest(dispatcher) {
            repository.cancelSigning()
            repository.clear()
            runCurrent()

            coVerify(exactly = 0) { ledgerSigningDataSource.close() }
            assertNull(repository.signingState.value)
        }

    @Test
    fun clearResetsTheProposalPcztProofsAndSigningState() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            repository.clear()
            runCurrent()

            assertNull(repository.transactionProposal.value)
            assertNull(repository.submitState.value)
            assertNull(repository.getProposalPCZT())
            assertNull(repository.signingState.value)
        }

    @Test
    fun submitAwaitsTheProofsJobAndSurfacesItsError() =
        runTest(dispatcher) {
            val proofsError = mockk<PcztException.AddProofsToPcztException>(relaxed = true)
            val proofsGate = CompletableDeferred<Unit>()
            givenPczt(ledgerAccount(), proofsError = proofsError, proofsGate = proofsGate)
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns Pczt(byteArrayOf(5))

            repository.startSigning()
            runCurrent()
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)

            val submitting = async { runCatching { repository.submit() } }
            runCurrent()
            assertFalse(submitting.isCompleted)

            proofsGate.complete(Unit)
            runCurrent()

            assertTrue(submitting.isCompleted)
            assertSame(proofsError, submitting.await().exceptionOrNull())
        }

    @Test
    fun createPcztFromProposalStartsACleanSubmissionCycle() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns Pczt(byteArrayOf(5))
            val submitResult = mockk<SubmitResult>(relaxed = true)
            coEvery { proposalDataSource.submitTransaction(any<Pczt>(), any<Pczt>()) } returns submitResult
            repository.startSigning()
            runCurrent()
            repository.submit()
            assertNotNull(repository.submitState.value)

            repository.createPCZTFromProposal()
            runCurrent()

            assertNull(repository.submitState.value)
            assertNull(repository.signingState.value)
            assertFailsWith<IllegalStateException> { repository.submit() }
        }

    /**
     * The retry arrives while the failed session is still inside its own publish of the failure,
     * i.e. while its job is still active.
     */

    @Test
    fun retryWhileTheFailedSessionIsStillUnwindingStartsANewSession() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            var signCalls = 0
            val signed = Pczt(byteArrayOf(5))
            val rejected = mockk<LedgerException.UserRejected>(relaxed = true)
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                signCalls++
                if (signCalls == 1) throw rejected else signed
            }
            var retried = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.signingState.collect { state ->
                    if (state is LedgerSigningState.Failed && !retried) {
                        retried = true
                        repository.retry()
                    }
                }
            }

            repository.startSigning()
            runCurrent()

            assertTrue(retried)
            assertEquals(2, signCalls)
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun aCorruptStoredBindingFailsWithUnboundAndOffersNoRetry() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } throws
                LedgerBindingUnusableException(IllegalArgumentException("corrupt"))

            repository.startSigning()
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.UNBOUND, failed.issue.kind)
            assertEquals(LedgerIssueRetry.NONE, failed.issue.retry)
        }

    @Test
    fun anUnboundAccountFailsWithUnboundAndOffersNoRetry() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount(bound = true))
            runCurrent()
            coEvery { accountDataSource.getSelectedAccount() } returns ledgerAccount(bound = false)

            repository.startSigning()
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.UNBOUND, failed.issue.kind)
            assertEquals(LedgerIssueRetry.NONE, failed.issue.retry)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any(), any()) }
        }

    /**
     * Simulates a session that is cancelled and immediately replaced while its own `sign` call is
     * still outstanding, by draining the replacement to completion from inside the outstanding
     * call's mock before it finally resolves — the "late" resolution can then only be published if
     * the generation guard is missing.
     */

    @Test
    fun aLateFailureFromACancelledSessionNeverOverwritesTheSessionThatReplacedIt() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            var signCalls = 0
            val signed = Pczt(byteArrayOf(5))
            val disconnected = mockk<LedgerException.Disconnected>(relaxed = true)
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                signCalls++
                if (signCalls == 1) {
                    repository.cancelSigning()
                    repository.startSigning()
                    dispatcher.scheduler.runCurrent()
                    throw disconnected
                } else {
                    signed
                }
            }

            repository.startSigning()
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            assertEquals(2, signCalls)
        }

    @Test
    fun aSignedSessionWithoutAProposalFailsWithoutARetry() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns Pczt(byteArrayOf(5))
            repository.startSigning()
            runCurrent()
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)

            repository.failSignedSessionWithoutProposal()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.UNKNOWN, failed.issue.kind)
            assertEquals(LedgerIssueRetry.NONE, failed.issue.retry)
        }

    @Test
    fun failingASessionThatIsNotSignedChangesNothing() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            repository.startSigning()
            runCurrent()
            assertEquals(LedgerSigningState.Scanning, repository.signingState.value)

            repository.failSignedSessionWithoutProposal()

            assertEquals(LedgerSigningState.Scanning, repository.signingState.value)
        }

    @Test
    fun signingWithoutAPcztFailsWithoutARetry() =
        runTest(dispatcher) {
            repository.startSigning()
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.UNKNOWN, failed.issue.kind)
            assertEquals(LedgerIssueRetry.NONE, failed.issue.retry)
        }
}
