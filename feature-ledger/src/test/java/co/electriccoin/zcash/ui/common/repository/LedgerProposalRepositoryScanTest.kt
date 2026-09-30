package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.model.Pczt
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.provider.LEDGER_SCAN_TIMEOUT
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The Ledger signing session's way to a device: scanning and its wait, the device picker, the
 * location check below API 31 and the capped connect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerProposalRepositoryScanTest : LedgerProposalRepositoryTestBase() {
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

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "AA" }, any()) }
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
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any(), any()) }
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

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "BB" }, any()) }
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun theScanTimeoutWithoutADeviceFailsWithNoDevices() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            repository.startSigning()
            runCurrent()
            advanceTimeBy(LEDGER_SCAN_TIMEOUT + 1.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.NO_DEVICES, failed.issue.kind)
        }

    @Test
    fun aConnectStillRunningAfterFiveMinutesFailsAsADisconnectAndClosesTheLink() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            clearMocks(ledgerSigningDataSource, answers = false)
            coEvery { ledgerSigningDataSource.connect(any(), any()) } coAnswers { awaitCancellation() }

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

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "BB" }, any()) }
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun locationOffBelowApi31FailsWithTheBluetoothAccessIssueInsteadOfScanning() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerDeviceDataSource.isLocationOffForScan() } returns true

            repository.startSigning()
            runCurrent()

            assertEquals(LedgerSigningState.Failed(LedgerIssue.locationOff), repository.signingState.value)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any(), any()) }
            verify(exactly = 0) { ledgerDeviceDataSource.observeDevices() }
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
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any(), any()) }

            advanceTimeBy(LEDGER_SCAN_TIMEOUT)
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
    fun afterTheWrongLedgerTheRescanOffersThePickerEvenForALoneDevice() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            var linked = false
            every { ledgerSigningDataSource.isLinked } answers { linked }
            coEvery { ledgerSigningDataSource.connect(any(), any()) } answers { linked = true }
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
            coVerify(exactly = 1) { ledgerSigningDataSource.connect(any(), any()) }

            repository.selectDevice("AA")
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            coVerify(exactly = 2) { ledgerSigningDataSource.connect(any(), any()) }

            repository.cancelSigning()
            runCurrent()
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            coVerify(exactly = 3) { ledgerSigningDataSource.connect(any(), any()) }
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
            advanceTimeBy(LEDGER_SCAN_TIMEOUT - 1.seconds)
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
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any(), any()) }
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
            advanceTimeBy(LEDGER_SCAN_TIMEOUT + 10.seconds)
            runCurrent()

            assertTrue(repository.signingState.value is LedgerSigningState.Selecting)
        }
}
