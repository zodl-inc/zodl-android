package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerAccountBinding
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothTransport
import cash.z.ecc.android.sdk.ledger.LedgerDeviceIdentity
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import cash.z.ecc.android.sdk.ledger.LedgerRunningApp
import cash.z.ecc.android.sdk.ledger.LedgerZcashApp
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.provider.LedgerScannerProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [LedgerSigningDataSourceImpl] owns the Bluetooth link across one sign attempt: it closes the link
 * on success and on every failure the device cannot be asked to retry over, keeps it open after a
 * refusal that can be repeated, and never lets [LedgerSigningDataSource.sign] run without one open.
 *
 * `LedgerDeviceIdentity.new` validates its input through the SDK's native backend, so it is mocked
 * at the companion boundary here rather than exercised for real — the same technique
 * `WalletRepositoryImplTest` uses for `PersistableWallet.new`.
 */
class LedgerSigningDataSourceTest {
    private val ledgerScannerProvider = mockk<LedgerScannerProvider>()
    private val synchronizer = mockk<Synchronizer>()
    private val synchronizerProvider =
        mockk<SynchronizerProvider>().also {
            coEvery { it.getSynchronizer() } returns synchronizer
        }

    private val dataSource: LedgerSigningDataSource =
        LedgerSigningDataSourceImpl(
            ledgerScannerProvider = ledgerScannerProvider,
            synchronizerProvider = synchronizerProvider,
        )

    @BeforeTest
    fun setUp() {
        mockkObject(LedgerDeviceIdentity.Companion)
        coEvery { LedgerDeviceIdentity.new(any()) } returns mockk(relaxed = true)
        mockkObject(LedgerZcashApp)
        coEvery { LedgerZcashApp.currentApp(any()) } returns runningApp(isZcash = true)
        coEvery { LedgerZcashApp.ensureZcashAppOpen(any(), any()) } answers { firstArg() }
    }

    @AfterTest
    fun tearDown() = unmockkAll()

    @Test
    fun aSuccessfulSignClosesAndDropsTheLink() =
        runTest {
            val transport = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns transport
            val signed = Pczt(byteArrayOf(1))
            coEvery { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) } returns signed

            dataSource.connect(device()) { }
            val result = dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount()) { }

            assertSame(signed, result)
            coVerify(exactly = 1) { transport.close() }
            assertFalse(dataSource.isLinked)
        }

    @Test
    fun aUserRejectedFailureKeepsTheLinkOpen() =
        runTest {
            val transport = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns transport
            val rejected = mockk<LedgerException.UserRejected>(relaxed = true) { every { isRestartable } returns true }
            coEvery { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) } throws rejected

            dataSource.connect(device()) { }
            assertFailsWith<LedgerException.UserRejected> {
                dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount()) { }
            }

            coVerify(exactly = 0) { transport.close() }
            assertTrue(dataSource.isLinked)
        }

    @Test
    fun aDisconnectedFailureClosesTheLink() =
        runTest {
            val transport = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns transport
            val disconnected = mockk<LedgerException.Disconnected>(relaxed = true)
            coEvery { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) } throws disconnected

            dataSource.connect(device()) { }
            assertFailsWith<LedgerException.Disconnected> {
                dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount()) { }
            }

            coVerify(exactly = 1) { transport.close() }
            assertFalse(dataSource.isLinked)
        }

    @Test
    fun aTimeoutFailureClosesTheLink() =
        runTest {
            val transport = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns transport
            val timeout = mockk<LedgerException.Timeout>(relaxed = true)
            coEvery { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) } throws timeout

            dataSource.connect(device()) { }
            assertFailsWith<LedgerException.Timeout> {
                dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount()) { }
            }

            coVerify(exactly = 1) { transport.close() }
            assertFalse(dataSource.isLinked)
        }

    @Test
    fun cancellationDuringSignClosesTheLink() =
        runTest {
            val transport = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns transport
            coEvery { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) } throws
                CancellationException("boom")

            dataSource.connect(device()) { }
            assertFailsWith<CancellationException> {
                dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount()) { }
            }

            coVerify(exactly = 1) { transport.close() }
            assertFalse(dataSource.isLinked)
        }

    @Test
    fun aCorruptStoredBindingClosesTheLinkAndThrowsLedgerBindingUnusableException() =
        runTest {
            val transport = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns transport
            coEvery { LedgerDeviceIdentity.new(any()) } throws IllegalArgumentException("corrupt")

            dataSource.connect(device()) { }
            assertFailsWith<LedgerBindingUnusableException> {
                dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount()) { }
            }

            coVerify(exactly = 1) { transport.close() }
            coVerify(exactly = 0) { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) }
            assertFalse(dataSource.isLinked)
        }

    /**
     * The device signs for the account it was paired at: the binding handed to the SDK carries the
     * account's stored index, whichever one the user chose.
     */
    @Test
    fun signingDerivesAgainstTheAccountsStoredIndex() =
        runTest {
            val transport = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns transport
            val binding = slot<LedgerAccountBinding>()
            coEvery {
                synchronizer.signPcztWithLedger(any(), any(), capture(binding), any(), any())
            } returns Pczt(byteArrayOf(1))

            dataSource.connect(device()) { }
            dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount(Zip32AccountIndex.new(7))) { }

            assertEquals(Zip32AccountIndex.new(7), binding.captured.zip32AccountIndex)
        }

    @Test
    fun anAccountWithoutAStoredIndexIsAnUnusableBindingAndNothingIsSigned() =
        runTest {
            val transport = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns transport

            dataSource.connect(device()) { }
            assertFailsWith<LedgerBindingUnusableException> {
                dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount(zip32AccountIndex = null)) { }
            }

            coVerify(exactly = 0) { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) { transport.close() }
        }

    @Test
    fun signWithoutALinkThrowsLedgerLinkMissingException() =
        runTest {
            assertFailsWith<LedgerLinkMissingException> {
                dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount()) { }
            }

            coVerify(exactly = 0) { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) }
        }

    @Test
    fun connectClosesAPreviousLinkFirst() =
        runTest {
            val first = mockk<LedgerBluetoothTransport>(relaxed = true)
            val second = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returnsMany listOf(first, second)

            dataSource.connect(device("AA")) { }
            dataSource.connect(device("BB")) { }

            coVerify(exactly = 1) { first.close() }
            coVerify(exactly = 0) { second.close() }
            assertTrue(dataSource.isLinked)
        }

    @Test
    fun signingUsesTheLinkTheZcashAppWasConfirmedOn() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            val afterAppSwitch = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } returns afterAppSwitch
            coEvery { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) } returns Pczt(byteArrayOf(1))

            dataSource.connect(device()) { }
            dataSource.sign(Pczt(byteArrayOf(0)), ledgerAccount()) { }

            coVerify(exactly = 1) { LedgerZcashApp.ensureZcashAppOpen(connected, any()) }
            coVerify(exactly = 1) {
                synchronizer.signPcztWithLedger(any(), any(), any(), transport = afterAppSwitch, onProgress = any())
            }
            coVerify(exactly = 1) { afterAppSwitch.close() }
        }

    @Test
    fun aZcashAppThatCannotBeOpenedClosesTheLinkBeforeAnySigning() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            val declined = mockk<LedgerException.AppOpenRejected>(relaxed = true)
            coEvery { LedgerZcashApp.ensureZcashAppOpen(any(), any()) } throws declined

            val thrown = assertFailsWith<LedgerException.AppOpenRejected> { dataSource.connect(device()) { } }

            assertSame(declined, thrown)
            coVerify(exactly = 1) { connected.close() }
            assertFalse(dataSource.isLinked)
            coVerify(exactly = 0) { synchronizer.signPcztWithLedger(any(), any(), any(), any(), any()) }
        }

    @Test
    fun aDeviceInAnotherAppReportsOpeningTheZcashAppOnceBeforeTheOpen() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery { LedgerZcashApp.currentApp(connected) } returns runningApp(isZcash = false)
            var reports = 0
            var reportsWhenOpening = -1
            coEvery { LedgerZcashApp.ensureZcashAppOpen(connected, any()) } answers {
                reportsWhenOpening = reports
                firstArg()
            }

            dataSource.connect(device()) { reports++ }

            assertEquals(1, reports)
            assertEquals(1, reportsWhenOpening)
            coVerify(exactly = 1) { LedgerZcashApp.currentApp(connected) }
            coVerify(exactly = 1) { LedgerZcashApp.ensureZcashAppOpen(connected, any()) }
            assertTrue(dataSource.isLinked)
        }

    @Test
    fun aDeviceAlreadyInTheZcashAppReportsNothing() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            var reports = 0

            dataSource.connect(device()) { reports++ }

            assertEquals(0, reports)
            coVerify(exactly = 1) { LedgerZcashApp.ensureZcashAppOpen(connected, any()) }
            assertTrue(dataSource.isLinked)
        }

    @Test
    fun aFailedAppQueryReportsNothingAndStillOpensTheAppOnTheSameLink() =
        runTest {
            listOf(
                mockk<LedgerException.Timeout>(relaxed = true),
                mockk<LedgerException.Disconnected>(relaxed = true),
                mockk<LedgerException.DeviceRefused>(relaxed = true),
                IllegalStateException("query"),
            ).forEach { failure ->
                val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
                coEvery { ledgerScannerProvider.connect(any()) } returns connected
                coEvery { LedgerZcashApp.currentApp(connected) } throws failure
                var reports = 0

                dataSource.connect(device()) { reports++ }

                assertEquals(0, reports, "reports after ${failure.javaClass.simpleName}")
                coVerify(exactly = 1) { LedgerZcashApp.ensureZcashAppOpen(connected, any()) }
                coVerify(exactly = 0) { connected.close() }
                assertTrue(dataSource.isLinked)
            }
        }

    @Test
    fun cancellationDuringTheAppQueryClosesTheLinkAndRethrows() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery { LedgerZcashApp.currentApp(connected) } throws CancellationException("cancelled")
            var reports = 0

            assertFailsWith<CancellationException> { dataSource.connect(device()) { reports++ } }

            assertEquals(0, reports)
            coVerify(exactly = 1) { connected.close() }
            coVerify(exactly = 0) { LedgerZcashApp.ensureZcashAppOpen(any(), any()) }
            assertFalse(dataSource.isLinked)
        }

    @Test
    fun aDeclinedOpenAfterTheReportClosesTheLinkAndThrowsAppOpenRejected() =
        runTest {
            val connected = mockk<LedgerBluetoothTransport>(relaxed = true)
            coEvery { ledgerScannerProvider.connect(any()) } returns connected
            coEvery { LedgerZcashApp.currentApp(connected) } returns runningApp(isZcash = false)
            val declined = mockk<LedgerException.AppOpenRejected>(relaxed = true)
            coEvery { LedgerZcashApp.ensureZcashAppOpen(any(), any()) } throws declined
            var reports = 0

            val thrown = assertFailsWith<LedgerException.AppOpenRejected> { dataSource.connect(device()) { reports++ } }

            assertSame(declined, thrown)
            assertEquals(1, reports)
            coVerify(exactly = 1) { connected.close() }
            assertFalse(dataSource.isLinked)
        }

    private fun runningApp(isZcash: Boolean) =
        mockk<LedgerRunningApp> {
            every { this@mockk.isZcash } returns isZcash
        }

    private fun device(identifier: String = "AA") =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = identifier,
            rssi = -40,
        )

    private fun ledgerAccount(zip32AccountIndex: Zip32AccountIndex? = Zip32AccountIndex.new(0L)) =
        LedgerAccount(
            sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() })),
            unifiedAddress = "u1secret",
            transparentAddress = "t1secret",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            deviceIdentity = "tpk0-deadbeef",
            zip32AccountIndex = zip32AccountIndex,
        )
}
