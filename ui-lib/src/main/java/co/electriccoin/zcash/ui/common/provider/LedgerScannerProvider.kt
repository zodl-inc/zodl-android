package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothScanner
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothTransport
import kotlinx.coroutines.flow.Flow

/**
 * The app's only entry point to the SDK's Bluetooth LE scanner. SDK Ledger types stop at this
 * layer and at [co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource]; nothing above
 * them sees a transport.
 */
interface LedgerScannerProvider {
    fun devices(): Flow<List<LedgerBluetoothDevice>>

    suspend fun connect(device: LedgerBluetoothDevice): LedgerBluetoothTransport
}

class LedgerScannerProviderImpl(
    context: Context
) : LedgerScannerProvider {
    private val scanner = LedgerBluetoothScanner(context)

    override fun devices(): Flow<List<LedgerBluetoothDevice>> = scanner.devices()

    override suspend fun connect(device: LedgerBluetoothDevice): LedgerBluetoothTransport = scanner.connect(device)
}
