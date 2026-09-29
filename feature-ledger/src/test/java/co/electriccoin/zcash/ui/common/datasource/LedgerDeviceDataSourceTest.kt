package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.ledger.LedgerApduTransport
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothTransport
import cash.z.ecc.android.sdk.ledger.LedgerDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import cash.z.ecc.android.sdk.ledger.LedgerZcashApp
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.model.LedgerPairingTimedOutException
import co.electriccoin.zcash.ui.common.provider.LedgerScannerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [LedgerDeviceDataSourceImpl.pair] brings the device to the Zcash app before pairing, pairs over
 * the transport that step returns with the read deadline and a reconnect, closes every transport it
 * opened, and caps the whole pairing at five minutes. The phone bonded with the device on the scan
 * screen already, so a failure to connect is passed on as the SDK raised it.
 * [LedgerDeviceDataSourceImpl.openZcashApp] does the app step alone and closes the same way.
 *
 * `LedgerDevice.new` loads the SDK's native backend and `LedgerZcashApp` talks to a device, so both
 * are mocked at their object boundary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerDeviceDataSourceTest {
    private val ledgerScannerProvider = mockk<LedgerScannerProvider>()
    private val dataSource = LedgerDeviceDataSourceImpl(ledgerScannerProvider)
    private val ledgerDevice = mockk<LedgerDevice>()
    private val pairing = mockk<LedgerAccountPairing>()
    private val account = Zip32AccountIndex.new(0)

    @BeforeTest
    fun setUp() {
        mockkObject(LedgerZcashApp, LedgerDevice.Companion)
        coEvery { LedgerDevice.new(any(), any()) } returns ledgerDevice
    }

    @AfterTest
    fun tearDown() = unmockkAll()

    @Test
    fun pairingRunsOverTheTransportTheAppSwitchReturns() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            val reconnected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returnsMany listOf(connected, reconnected)
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } coAnswers {
                secondArg<suspend () -> LedgerApduTransport>().invoke()
            }
            coEvery { ledgerDevice.pairAccount(account, any(), any()) } returns pairing

            val result = dataSource.pair(device(), account)

            assertSame(pairing, result)
            coVerifyOrder {
                ledgerScannerProvider.connect(any())
                LedgerZcashApp.ensureZcashAppOpen(connected, any())
                ledgerScannerProvider.connect(any())
                LedgerDevice.new(reconnected, any())
                ledgerDevice.pairAccount(account, any(), any())
                reconnected.close()
            }
        }

    @Test
    fun anOpenZcashAppPairsOverTheFirstTransport() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } returns connected
            coEvery { ledgerDevice.pairAccount(account, any(), any()) } returns pairing

            dataSource.pair(device(), account)

            coVerify(exactly = 1) { ledgerScannerProvider.connect(any()) }
            coVerify(exactly = 1) { LedgerDevice.new(connected, any()) }
            coVerify(exactly = 1) { connected.close() }
        }

    @Test
    fun aFailedAppSwitchClosesTheFirstTransportAndNeverPairs() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery {
                LedgerZcashApp.ensureZcashAppOpen(connected, any())
            } throws mockk<LedgerException.AppNotInstalled>(relaxed = true)

            assertFailsWith<LedgerException.AppNotInstalled> { dataSource.pair(device(), account) }

            coVerify(exactly = 1) { connected.close() }
            coVerify(exactly = 0) { LedgerDevice.new(any(), any()) }
        }

    @Test
    fun aFailedPairingClosesTheTransportTheAppSwitchReturned() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            val reconnected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } returns reconnected
            coEvery {
                ledgerDevice.pairAccount(account, any(), any())
            } throws mockk<LedgerException.Disconnected>(relaxed = true)

            assertFailsWith<LedgerException.Disconnected> { dataSource.pair(device(), account) }

            coVerify(exactly = 1) { reconnected.close() }
        }

    @Test
    fun pairingPassesTheReadDeadlineAndAReconnectWhoseTransportIsClosedToo() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            val reconnected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returnsMany listOf(connected, reconnected)
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } returns connected
            val readTimeout = slot<Duration>()
            val reconnect = slot<suspend () -> LedgerApduTransport>()
            coEvery {
                ledgerDevice.pairAccount(account, capture(readTimeout), capture(reconnect))
            } coAnswers {
                reconnect.captured.invoke()
                pairing
            }

            val result = dataSource.pair(device(), account)

            assertSame(pairing, result)
            assertEquals(10.seconds, readTimeout.captured)
            assertEquals(LedgerDevice.DEFAULT_PAIRING_READ_TIMEOUT, readTimeout.captured)
            coVerify(exactly = 2) { ledgerScannerProvider.connect(any()) }
            coVerify(exactly = 1) { connected.close() }
            coVerify(exactly = 1) { reconnected.close() }
        }

    @Test
    fun aPairingStillWaitingAfterFiveMinutesTimesOutAndClosesEveryTransport() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            val reconnected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returnsMany listOf(connected, reconnected)
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } coAnswers {
                secondArg<suspend () -> LedgerApduTransport>().invoke()
            }
            coEvery { ledgerDevice.pairAccount(account, any(), any()) } coAnswers { awaitCancellation() }

            assertFailsWith<LedgerPairingTimedOutException> { dataSource.pair(device(), account) }

            assertEquals(5.minutes.inWholeMilliseconds, currentTime)
            coVerify(exactly = 1) { connected.close() }
            coVerify(exactly = 1) { reconnected.close() }
        }

    @Test
    fun aFailureToConnectSurfacesAsItIsAndNothingElseRuns() =
        runTest {
            val lost = mockk<LedgerException.ConnectionFailed>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } throws lost

            val failure = assertFailsWith<LedgerException.ConnectionFailed> { dataSource.pair(device(), account) }

            assertSame(lost, failure)
            coVerify(exactly = 0) { LedgerZcashApp.ensureZcashAppOpen(any(), any()) }
            coVerify(exactly = 0) { LedgerDevice.new(any(), any()) }
        }

    @Test
    fun aFailedReconnectSurfacesAsItIsAndClosesTheFirstTransport() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            val lost = mockk<LedgerException.ConnectionFailed>(relaxed = true)
            var connects = 0
            coEvery { ledgerScannerProvider.connect(any()) } coAnswers {
                connects++
                if (connects == 1) connected else throw lost
            }
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } coAnswers {
                secondArg<suspend () -> LedgerApduTransport>().invoke()
            }

            val failure = assertFailsWith<LedgerException.ConnectionFailed> { dataSource.pair(device(), account) }

            assertSame(lost, failure)
            coVerify(exactly = 1) { connected.close() }
        }

    @Test
    fun openingTheAppClosesEveryTransportTheAppSwitchOpened() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            val reconnected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returnsMany listOf(connected, reconnected)
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } coAnswers {
                secondArg<suspend () -> LedgerApduTransport>().invoke()
            }

            dataSource.openZcashApp(device())

            coVerifyOrder {
                ledgerScannerProvider.connect(any())
                LedgerZcashApp.ensureZcashAppOpen(connected, any())
                ledgerScannerProvider.connect(any())
                reconnected.close()
            }
            coVerify(exactly = 1) { connected.close() }
            coVerify(exactly = 0) { LedgerDevice.new(any(), any()) }
        }

    @Test
    fun openingAnAlreadyOpenAppClosesTheFirstTransport() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } returns connected

            dataSource.openZcashApp(device())

            coVerify(exactly = 1) { ledgerScannerProvider.connect(any()) }
            coVerify(exactly = 1) { connected.close() }
        }

    @Test
    fun aDeclinedAppOpenClosesTheFirstTransportAndRethrows() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery {
                LedgerZcashApp.ensureZcashAppOpen(connected, any())
            } throws mockk<LedgerException.AppOpenRejected>(relaxed = true)

            assertFailsWith<LedgerException.AppOpenRejected> { dataSource.openZcashApp(device()) }

            coVerify(exactly = 1) { connected.close() }
        }

    private fun device() =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA",
            rssi = -40,
        )
}
