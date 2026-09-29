package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import cash.z.ecc.android.sdk.ledger.LedgerSigningProgress
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerBindingUnusableException
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerSigningDataSource
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerIssue
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The Ledger signing session: scanning and device selection, the connect/sign state machine, retry
 * over a kept link versus a rescan, cancellation, and the generation guard that keeps a session that
 * already unwound from overwriting the one that replaced it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerProposalRepositoryTest {
    private val dispatcher = StandardTestDispatcher()

    private val accountDataSource = mockk<AccountDataSource>()
    private val proposalDataSource = mockk<ProposalDataSource>()
    private val ledgerDeviceDataSource = mockk<LedgerDeviceDataSource>()
    private val ledgerSigningDataSource = mockk<LedgerSigningDataSource>(relaxed = true)

    private val devices = MutableStateFlow<List<LedgerBluetoothDevice>>(emptyList())

    private val repository =
        LedgerProposalRepositoryImpl(
            accountDataSource = accountDataSource,
            proposalDataSource = proposalDataSource,
            ledgerDeviceDataSource = ledgerDeviceDataSource,
            ledgerSigningDataSource = ledgerSigningDataSource,
        ).apply { scope = CoroutineScope(dispatcher) }

    init {
        every { ledgerDeviceDataSource.observeDevices() } returns devices
    }

    @Test
    fun aLoneDeviceConnectsAfterTheSettleDelay() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns signed

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "AA" }) }
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun twoDevicesOffersSelectingInsteadOfAutoConnecting() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            val state = repository.signingState.value
            assertTrue(state is LedgerSigningState.Selecting)
            assertEquals(2, state.devices.size)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any()) }
        }

    @Test
    fun selectDeviceConnectsThePickedDevice() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns signed

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(repository.signingState.value is LedgerSigningState.Selecting)

            repository.selectDevice("BB")
            runCurrent()

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "BB" }) }
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun twentySecondsWithoutADeviceFailsWithNoDevices() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            repository.startSigning()
            runCurrent()
            advanceTimeBy(21.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.NO_DEVICES, failed.issue.kind)
        }

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
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any()) }
        }

    @Test
    fun disconnectedFailsWithReconnectAndRetryRescans() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            var linked = false
            every { ledgerSigningDataSource.isLinked } answers { linked }
            coEvery { ledgerSigningDataSource.connect(any()) } answers { linked = true }
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
            coVerify(exactly = 2) { ledgerSigningDataSource.connect(any()) }
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
    fun aConnectStillRunningAfterFiveMinutesFailsAsADisconnectAndClosesTheLink() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            clearMocks(ledgerSigningDataSource, answers = false)
            coEvery { ledgerSigningDataSource.connect(any()) } coAnswers { awaitCancellation() }

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(LedgerSigningState.Connecting, repository.signingState.value)

            advanceTimeBy(5.minutes)
            runCurrent()

            assertEquals(
                LedgerSigningState.Failed(LedgerIssue.disconnectedWhileSigning),
                repository.signingState.value
            )
            coVerify(exactly = 1) { ledgerSigningDataSource.close() }
            coVerify(exactly = 0) { ledgerSigningDataSource.sign(any(), any(), any()) }
        }

    @Test
    fun aDeviceTappedTheMomentThePickerOpensIsNotLost() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns Pczt(byteArrayOf(5))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.signingState.collect { state ->
                    if (state is LedgerSigningState.Selecting) repository.selectDevice("BB")
                }
            }

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "BB" }) }
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
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
    fun aLoneDeviceVanishingWhileTheListSettlesKeepsWaitingUntilTheScanTimesOut() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(500.milliseconds)
            devices.value = emptyList()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LedgerSigningState.Scanning, repository.signingState.value)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any()) }

            advanceTimeBy(20.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.NO_DEVICES, failed.issue.kind)
        }

    @Test
    fun selectingNeverPrintsTheDeviceIdentifiers() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            devices.value = listOf(device("AA:11"), device("BB:22"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            repository.selectDevice("AA:11")
            runCurrent()
            repository.cancelSigning()
            runCurrent()
            devices.value = listOf(device("AA:11"), device("BB:22"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            val state = repository.signingState.value
            assertTrue(state is LedgerSigningState.Selecting)
            assertNotNull(state.selectedIdentifier)
            val printed = state.toString()
            assertFalse(printed.contains("AA:11"))
            assertFalse(printed.contains("BB:22"))
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
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any()) }
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
    fun afterTheWrongLedgerTheRescanOffersThePickerEvenForALoneDevice() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            var linked = false
            every { ledgerSigningDataSource.isLinked } answers { linked }
            coEvery { ledgerSigningDataSource.connect(any()) } answers { linked = true }
            var signCalls = 0
            val signed = Pczt(byteArrayOf(5))
            val mismatch = mockk<LedgerException.DeviceMismatch>(relaxed = true)
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                signCalls++
                linked = false
                if (signCalls == 1) throw mismatch else signed
            }

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.WRONG_DEVICE, failed.issue.kind)

            repository.retry()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            val selecting = repository.signingState.value
            assertTrue(selecting is LedgerSigningState.Selecting)
            assertEquals(1, selecting.devices.size)
            assertNull(selecting.selectedIdentifier)
            coVerify(exactly = 1) { ledgerSigningDataSource.connect(any()) }

            repository.selectDevice("AA")
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            coVerify(exactly = 2) { ledgerSigningDataSource.connect(any()) }

            repository.cancelSigning()
            runCurrent()
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            coVerify(exactly = 3) { ledgerSigningDataSource.connect(any()) }
        }

    @Test
    fun aPickerThatStaysEmptyForTheScanTimeoutFailsWithNoDevices() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(repository.signingState.value is LedgerSigningState.Selecting)

            devices.value = emptyList()
            runCurrent()
            advanceTimeBy(19.seconds)
            runCurrent()

            val stillSelecting = repository.signingState.value
            assertTrue(stillSelecting is LedgerSigningState.Selecting)
            assertEquals(0, stillSelecting.devices.size)

            advanceTimeBy(2.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.NO_DEVICES, failed.issue.kind)
            assertEquals(LedgerIssueRetry.RECONNECT, failed.issue.retry)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any()) }
        }

    @Test
    fun aDeviceReturningToAnEmptiedPickerKeepsItOpen() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            devices.value = emptyList()
            runCurrent()
            advanceTimeBy(10.seconds)
            devices.value = listOf(device("AA"))
            runCurrent()
            advanceTimeBy(30.seconds)
            runCurrent()

            assertTrue(repository.signingState.value is LedgerSigningState.Selecting)
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

    private suspend fun givenPczt(
        account: LedgerAccount,
        pczt: Pczt = Pczt(byteArrayOf(1)),
        proofsPczt: Pczt = Pczt(byteArrayOf(9)),
        proofsError: PcztException.AddProofsToPcztException? = null,
        proofsGate: CompletableDeferred<Unit>? = null,
        onProofsCancelled: () -> Unit = {},
    ): Pczt {
        coEvery { accountDataSource.getSelectedAccount() } returns account
        coEvery { proposalDataSource.createProposal(any(), any()) } returns
            RegularTransactionProposal(
                destination = WalletAddress.Unified.new(RECIPIENT),
                amount = Zatoshi(1234L),
                memo = Memo(""),
                proposal = mockk<Proposal>()
            )
        coEvery { proposalDataSource.createPcztFromProposal(any(), any()) } returns pczt
        coEvery { proposalDataSource.addProofsToPczt(any()) } coAnswers {
            try {
                proofsGate?.await()
            } catch (e: CancellationException) {
                onProofsCancelled()
                throw e
            }
            if (proofsError != null) throw proofsError
            proofsPczt
        }
        repository.createProposal(
            ZecSend(
                destination = WalletAddress.Unified.new(RECIPIENT),
                amount = Zatoshi(1234L),
                memo = Memo(""),
                proposal = null
            )
        )
        repository.createPCZTFromProposal()
        return pczt
    }

    private fun device(identifier: String) =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = identifier,
            rssi = -40,
        )

    private fun ledgerAccount(bound: Boolean = true) =
        LedgerAccount(
            sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() })),
            unifiedAddress = "u1secret",
            transparentAddress = "t1secret",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            deviceIdentity = if (bound) "tpk0-deadbeef" else null,
            zip32AccountIndex = if (bound) Zip32AccountIndex.new(0L) else null,
        )

    private companion object {
        const val RECIPIENT = "recipient"
    }
}
