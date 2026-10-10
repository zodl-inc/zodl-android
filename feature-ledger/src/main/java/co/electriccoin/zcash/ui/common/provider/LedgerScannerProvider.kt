package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothScanner
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothTransport
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long a Ledger scan, while connecting an account or signing, looks for a device before it
 * reports that none was found.
 */
val LEDGER_SCAN_TIMEOUT: Duration = 30.seconds

/**
 * The app's only entry point to the SDK's Bluetooth LE scanner. SDK Ledger types stop at this
 * layer and at [co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource]; nothing above
 * them sees a transport.
 */
interface LedgerScannerProvider {
    fun devices(): Flow<List<LedgerBluetoothDevice>>

    /**
     * Below API 31 a Bluetooth LE scan finds nothing while location is switched off.
     */
    fun isLocationOffForScan(): Boolean

    suspend fun connect(device: LedgerBluetoothDevice): LedgerBluetoothTransport
}

class LedgerScannerProviderImpl(
    private val context: Context
) : LedgerScannerProvider {
    private val scanner = LedgerBluetoothScanner(context)

    override fun devices(): Flow<List<LedgerBluetoothDevice>> = scanner.devices()

    override fun isLocationOffForScan(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            context
                .getSystemService(LocationManager::class.java)
                ?.let { !LocationManagerCompat.isLocationEnabled(it) } == true

    override suspend fun connect(device: LedgerBluetoothDevice): LedgerBluetoothTransport = scanner.connect(device)
}
