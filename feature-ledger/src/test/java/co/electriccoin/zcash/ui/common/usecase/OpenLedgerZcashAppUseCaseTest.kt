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

/**
 * Opening the Zcash app talks to the device the user picked, reports a missing device without
 * touching any, and lets the device's refusals through to the caller.
 */
class OpenLedgerZcashAppUseCaseTest {
    private val device =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA:BB",
            rssi = -40,
        )

    @Test
    fun theSelectedDeviceIsAskedToOpenTheApp() =
        runTest {
            val dataSource = mockk<LedgerDeviceDataSource>(relaxed = true)
            val repository = LedgerSelectedDeviceRepositoryImpl().apply { set(device, Zip32AccountIndex.new(0)) }

            val result = OpenLedgerZcashAppUseCase(dataSource, repository).invoke()

            assertEquals(OpenLedgerZcashAppResult.Opened, result)
            coVerify(exactly = 1) { dataSource.openZcashApp(device) }
        }

    @Test
    fun noSelectedDeviceIsReportedWithoutTalkingToAnyDevice() =
        runTest {
            val dataSource = mockk<LedgerDeviceDataSource>(relaxed = true)

            val result = OpenLedgerZcashAppUseCase(dataSource, LedgerSelectedDeviceRepositoryImpl()).invoke()

            assertEquals(OpenLedgerZcashAppResult.NoDevice, result)
            coVerify(exactly = 0) { dataSource.openZcashApp(any()) }
        }

    @Test
    fun aDeclinedRequestReachesTheCaller() =
        runTest {
            val dataSource =
                mockk<LedgerDeviceDataSource> {
                    coEvery { openZcashApp(any()) } throws mockk<LedgerException.AppOpenRejected>(relaxed = true)
                }
            val repository = LedgerSelectedDeviceRepositoryImpl().apply { set(device, Zip32AccountIndex.new(0)) }

            assertFailsWith<LedgerException.AppOpenRejected> {
                OpenLedgerZcashAppUseCase(dataSource, repository).invoke()
            }
        }
}
