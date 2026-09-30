package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerSigningProgress
import cash.z.ecc.android.sdk.model.Pczt
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The connect's report that the device is being asked to open the Zcash app: when
 * [LedgerSigningState.OpeningZcashApp] is shown, and that a report from a session that is no longer
 * current, or one that is no longer connecting, changes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerProposalRepositoryConnectTest : LedgerProposalRepositoryTestBase() {
    @Test
    fun openingZcashAppIsShownBetweenConnectingAndPreparing() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            var beforeReport: LedgerSigningState? = null
            var afterReport: LedgerSigningState? = null
            var whenSigning: LedgerSigningState? = null
            coEvery { ledgerSigningDataSource.connect(any(), any()) } coAnswers {
                beforeReport = repository.signingState.value
                secondArg<() -> Unit>().invoke()
                afterReport = repository.signingState.value
            }
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                whenSigning = repository.signingState.value
                Pczt(byteArrayOf(5))
            }

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LedgerSigningState.Connecting, beforeReport)
            assertEquals(LedgerSigningState.OpeningZcashApp, afterReport)
            assertEquals(LedgerSigningState.Preparing, whenSigning)
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun aDeviceAlreadyInTheZcashAppGoesFromConnectingStraightToPreparing() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns Pczt(byteArrayOf(5))
            val seen = recordStates()

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertFalse(LedgerSigningState.OpeningZcashApp in seen)
            assertEquals(
                listOf(
                    LedgerSigningState.Scanning,
                    LedgerSigningState.Connecting,
                    LedgerSigningState.Preparing,
                    LedgerSigningState.Signed,
                ),
                seen.filterNotNull()
            )
        }

    @Test
    fun aSessionRunsThroughEveryPhaseInOrder() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            coEvery { ledgerSigningDataSource.connect(any(), any()) } coAnswers {
                secondArg<() -> Unit>().invoke()
            }
            val progress = slot<(LedgerSigningProgress) -> Unit>()
            coEvery { ledgerSigningDataSource.sign(any(), any(), capture(progress)) } coAnswers {
                progress.captured(LedgerSigningProgress.IdentifyingDevice)
                progress.captured(LedgerSigningProgress.Streaming(4, 9))
                progress.captured(LedgerSigningProgress.AwaitingReviewOnDevice)
                progress.captured(LedgerSigningProgress.Signing)
                Pczt(byteArrayOf(5))
            }
            val seen = recordStates()

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            repository.selectDevice("BB")
            runCurrent()

            assertEquals(
                listOf(
                    LedgerSigningState.Scanning,
                    LedgerSigningState.Selecting::class,
                    LedgerSigningState.Connecting,
                    LedgerSigningState.OpeningZcashApp,
                    LedgerSigningState.Preparing,
                    LedgerSigningState.Streaming(0, 0),
                    LedgerSigningState.Streaming(4, 9),
                    LedgerSigningState.AwaitingReview,
                    LedgerSigningState.Signing,
                    LedgerSigningState.Signed,
                ),
                seen.filterNotNull().map { if (it is LedgerSigningState.Selecting) it::class else it }
            )
        }

    @Test
    fun aReportFromACancelledSessionNeverReachesTheSessionThatReplacedIt() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            clearMocks(ledgerSigningDataSource, answers = false)
            val reports = mutableListOf<() -> Unit>()
            coEvery { ledgerSigningDataSource.connect(any(), any()) } coAnswers {
                reports += secondArg<() -> Unit>()
                awaitCancellation()
            }

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(LedgerSigningState.Connecting, repository.signingState.value)

            repository.cancelSigning()
            runCurrent()
            reports[0].invoke()
            assertNull(repository.signingState.value)

            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(LedgerSigningState.Connecting, repository.signingState.value)

            reports[0].invoke()
            assertEquals(LedgerSigningState.Connecting, repository.signingState.value)

            reports[1].invoke()
            assertEquals(LedgerSigningState.OpeningZcashApp, repository.signingState.value)
        }

    @Test
    fun aReportAfterTheSessionLeftConnectingChangesNothing() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            val report = slot<() -> Unit>()
            coEvery { ledgerSigningDataSource.connect(any(), capture(report)) } returns Unit
            var afterLateReport: LedgerSigningState? = null
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                report.captured.invoke()
                afterLateReport = repository.signingState.value
                Pczt(byteArrayOf(5))
            }

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LedgerSigningState.Preparing, afterLateReport)
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            report.captured.invoke()
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun aRepeatedReportKeepsOpeningZcashApp() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            clearMocks(ledgerSigningDataSource, answers = false)
            coEvery { ledgerSigningDataSource.connect(any(), any()) } coAnswers {
                secondArg<() -> Unit>().invoke()
                secondArg<() -> Unit>().invoke()
                awaitCancellation()
            }
            val seen = recordStates()

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LedgerSigningState.OpeningZcashApp, repository.signingState.value)
            assertEquals(1, seen.count { it == LedgerSigningState.OpeningZcashApp })
        }

    @Test
    fun theFiveMinuteCapWhileOpeningZcashAppFailsAsADisconnectAndClosesTheLink() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            clearMocks(ledgerSigningDataSource, answers = false)
            coEvery { ledgerSigningDataSource.connect(any(), any()) } coAnswers {
                secondArg<() -> Unit>().invoke()
                awaitCancellation()
            }

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(LedgerSigningState.OpeningZcashApp, repository.signingState.value)

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
    fun aDeclineAfterOpeningZcashAppFailsAndTryAgainReturnsThroughOpeningZcashApp() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            val declined = mockk<LedgerException.AppOpenRejected>(relaxed = true)
            var connects = 0
            val beforeReports = mutableListOf<LedgerSigningState?>()
            val afterReports = mutableListOf<LedgerSigningState?>()
            coEvery { ledgerSigningDataSource.connect(any(), any()) } coAnswers {
                connects++
                beforeReports += repository.signingState.value
                secondArg<() -> Unit>().invoke()
                afterReports += repository.signingState.value
                if (connects == 1) throw declined
            }
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns Pczt(byteArrayOf(5))

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.OPEN_APP_REJECTED, failed.issue.kind)
            assertEquals(LedgerIssueRetry.RECONNECT, failed.issue.retry)

            repository.retry()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(
                listOf<LedgerSigningState?>(LedgerSigningState.Connecting, LedgerSigningState.Connecting),
                beforeReports
            )
            assertEquals(
                listOf<LedgerSigningState?>(LedgerSigningState.OpeningZcashApp, LedgerSigningState.OpeningZcashApp),
                afterReports
            )
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun aPickedDeviceIsConnectedOnceAndLaterPicksAreIgnored() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            clearMocks(ledgerSigningDataSource, answers = false)
            coEvery { ledgerSigningDataSource.connect(any(), any()) } coAnswers { awaitCancellation() }

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(repository.signingState.value is LedgerSigningState.Selecting)

            repository.selectDevice("BB")
            repository.selectDevice("AA")
            runCurrent()
            assertEquals(LedgerSigningState.Connecting, repository.signingState.value)
            repository.selectDevice("AA")
            runCurrent()

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(any(), any()) }
            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "BB" }, any()) }
        }

    @Test
    fun thePickerFollowsTheDevicesInRangeWhileItIsOpen() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            devices.value = listOf(device("AA"), device("BB"), device("CC"))
            runCurrent()
            val grown = repository.signingState.value
            assertTrue(grown is LedgerSigningState.Selecting)
            assertEquals(listOf("AA", "BB", "CC"), grown.devices.map { it.identifier })

            devices.value = listOf(device("CC"))
            runCurrent()
            val shrunk = repository.signingState.value
            assertTrue(shrunk is LedgerSigningState.Selecting)
            assertEquals(listOf("CC"), shrunk.devices.map { it.identifier })
        }

    /**
     * Every state the session publishes, in order, including the ones the next one replaces before
     * the test looks.
     */
    private fun TestScope.recordStates(): List<LedgerSigningState?> {
        val seen = mutableListOf<LedgerSigningState?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.signingState.collect { seen += it }
        }
        return seen
    }
}
