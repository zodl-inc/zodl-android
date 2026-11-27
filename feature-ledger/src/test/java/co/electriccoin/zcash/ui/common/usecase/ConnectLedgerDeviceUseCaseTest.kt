package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Connecting bonds with the device and remembers it, with the account the user chose, for the
 * handshake, but only once the link was actually opened, and passes on whether the device runs the
 * Zcash app already.
 */
class ConnectLedgerDeviceUseCaseTest {
    private val device =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA:BB",
            rssi = -40,
        )

    private val account = Zip32AccountIndex.new(3)

    @Test
    fun aSuccessfulConnectionRemembersTheDeviceForTheHandshake() =
        runTest {
            val dataSource =
                mockk<LedgerDeviceDataSource> {
                    coEvery { connect(any()) } returns false
                }
            val repository = LedgerSelectedDeviceRepositoryImpl()

            assertFalse(ConnectLedgerDeviceUseCase(dataSource, repository).invoke(device, account))

            coVerify(exactly = 1) { dataSource.connect(device) }
            assertSame(device, repository.get())
            assertEquals(account, repository.getZip32AccountIndex())
        }

    @Test
    fun aDeviceAlreadyInTheZcashAppIsReportedAndRemembered() =
        runTest {
            val dataSource =
                mockk<LedgerDeviceDataSource> {
                    coEvery { connect(any()) } returns true
                }
            val repository = LedgerSelectedDeviceRepositoryImpl()

            assertTrue(ConnectLedgerDeviceUseCase(dataSource, repository).invoke(device, account))

            assertSame(device, repository.get())
        }

    @Test
    fun aFailedConnectionRemembersNothing() =
        runTest {
            val failure = mockk<LedgerException.PairingRefused>(relaxed = true)
            val dataSource =
                mockk<LedgerDeviceDataSource> {
                    coEvery { connect(any()) } throws failure
                }
            val repository = LedgerSelectedDeviceRepositoryImpl()

            assertFailsWith<LedgerException.PairingRefused> {
                ConnectLedgerDeviceUseCase(dataSource, repository).invoke(device, account)
            }
            assertNull(repository.get())
            assertNull(repository.getZip32AccountIndex())
        }

    @Test
    fun clearingTheRepositoryForgetsTheDevice() =
        runTest {
            val dataSource =
                mockk<LedgerDeviceDataSource> {
                    coEvery { connect(any()) } returns false
                }
            val repository = LedgerSelectedDeviceRepositoryImpl()
            ConnectLedgerDeviceUseCase(dataSource, repository).invoke(device, account)

            repository.clear()

            assertNull(repository.get())
            assertNull(repository.getZip32AccountIndex())
        }
}
