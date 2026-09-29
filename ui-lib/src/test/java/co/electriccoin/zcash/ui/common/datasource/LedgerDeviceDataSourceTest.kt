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
import co.electriccoin.zcash.ui.common.provider.LedgerScannerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * [LedgerDeviceDataSourceImpl.pair] brings the device to the Zcash app before pairing, pairs over
 * the transport that step returns, and closes the transport it ends up holding.
 *
 * `LedgerDevice.new` loads the SDK's native backend and `LedgerZcashApp` talks to a device, so both
 * are mocked at their object boundary.
 */
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
            coEvery { ledgerDevice.pairAccount(account) } returns pairing

            val result = dataSource.pair(device(), account)

            assertSame(pairing, result)
            coVerifyOrder {
                ledgerScannerProvider.connect(any())
                LedgerZcashApp.ensureZcashAppOpen(connected, any())
                ledgerScannerProvider.connect(any())
                LedgerDevice.new(reconnected, any())
                ledgerDevice.pairAccount(account)
                reconnected.close()
            }
        }

    @Test
    fun anOpenZcashAppPairsOverTheFirstTransport() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } returns connected
            coEvery { ledgerDevice.pairAccount(account) } returns pairing

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
            coEvery { ledgerDevice.pairAccount(account) } throws mockk<LedgerException.Disconnected>(relaxed = true)

            assertFailsWith<LedgerException.Disconnected> { dataSource.pair(device(), account) }

            coVerify(exactly = 1) { reconnected.close() }
        }

    private fun device() =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA",
            rssi = -40,
        )
}
