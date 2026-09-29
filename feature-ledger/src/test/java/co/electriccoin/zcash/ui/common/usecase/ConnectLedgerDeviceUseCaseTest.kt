package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepositoryImpl
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Connecting bonds with the device and remembers it for the handshake, but only once the link was
 * actually opened.
 */
class ConnectLedgerDeviceUseCaseTest {
    private val device =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA:BB",
            rssi = -40,
        )

    @Test
    fun aSuccessfulConnectionRemembersTheDeviceForTheHandshake() =
        runTest {
            val dataSource =
                mockk<LedgerDeviceDataSource> {
                    coEvery { connect(any()) } just Runs
                }
            val repository = LedgerSelectedDeviceRepositoryImpl()

            ConnectLedgerDeviceUseCase(dataSource, repository).invoke(device)

            coVerify(exactly = 1) { dataSource.connect(device) }
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
                ConnectLedgerDeviceUseCase(dataSource, repository).invoke(device)
            }
            assertNull(repository.get())
        }

    @Test
    fun clearingTheRepositoryForgetsTheDevice() =
        runTest {
            val dataSource =
                mockk<LedgerDeviceDataSource> {
                    coEvery { connect(any()) } just Runs
                }
            val repository = LedgerSelectedDeviceRepositoryImpl()
            ConnectLedgerDeviceUseCase(dataSource, repository).invoke(device)

            repository.clear()

            assertNull(repository.get())
        }
}
