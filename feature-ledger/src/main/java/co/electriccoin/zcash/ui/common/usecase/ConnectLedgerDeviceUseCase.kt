package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository

/**
 * Bonds the phone with [device] if it has not yet, without touching the Zcash app on it, and
 * remembers the device for the handshake that follows once the user has opened that app. Returns
 * whether the device reported the Zcash app running already, so the open-the-app step can be
 * skipped; false also when that is unknown.
 */
class ConnectLedgerDeviceUseCase(
    private val ledgerDeviceDataSource: LedgerDeviceDataSource,
    private val ledgerSelectedDeviceRepository: LedgerSelectedDeviceRepository,
) {
    suspend operator fun invoke(device: LedgerBluetoothDevice): Boolean {
        val isZcashAppRunning = ledgerDeviceDataSource.connect(device)
        ledgerSelectedDeviceRepository.set(device)
        return isZcashAppRunning
    }
}
