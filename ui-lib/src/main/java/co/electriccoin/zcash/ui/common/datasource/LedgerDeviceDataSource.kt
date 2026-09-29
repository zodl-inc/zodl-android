package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.ledger.LedgerApduTransport
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDevice
import cash.z.ecc.android.sdk.ledger.LedgerZcashApp
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.LedgerScannerProvider
import kotlinx.coroutines.flow.Flow

interface LedgerDeviceDataSource {
    fun observeDevices(): Flow<List<LedgerBluetoothDevice>>

    /**
     * Connects to [device], opens the Zcash app on it if it is elsewhere (the user confirms on the
     * device; the link may be reopened while the device switches apps), asks it to export the
     * ZIP 32 account [zip32AccountIndex] (the user approves the export on the device) and closes the
     * transport again. Enrollment holds no transport open: a later signing session opens its own.
     */
    suspend fun pair(
        device: LedgerBluetoothDevice,
        zip32AccountIndex: Zip32AccountIndex = Zip32AccountIndex.new(0)
    ): LedgerAccountPairing
}

class LedgerDeviceDataSourceImpl(
    private val ledgerScannerProvider: LedgerScannerProvider,
) : LedgerDeviceDataSource {
    override fun observeDevices(): Flow<List<LedgerBluetoothDevice>> = ledgerScannerProvider.devices()

    override suspend fun pair(
        device: LedgerBluetoothDevice,
        zip32AccountIndex: Zip32AccountIndex
    ): LedgerAccountPairing {
        val connected = ledgerScannerProvider.connect(device)
        var transport: LedgerApduTransport = connected
        return try {
            transport = LedgerZcashApp.ensureZcashAppOpen(connected) { ledgerScannerProvider.connect(device) }
            LedgerDevice.new(transport, VersionInfo.NETWORK).pairAccount(zip32AccountIndex)
        } finally {
            transport.close()
        }
    }
}
