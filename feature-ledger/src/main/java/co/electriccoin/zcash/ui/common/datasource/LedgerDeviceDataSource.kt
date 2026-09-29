package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.ledger.LedgerApduTransport
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDevice
import cash.z.ecc.android.sdk.ledger.LedgerZcashApp
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.LedgerBondingFailedException
import co.electriccoin.zcash.ui.common.model.LedgerPairingTimedOutException
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.LedgerScannerProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

interface LedgerDeviceDataSource {
    fun observeDevices(): Flow<List<LedgerBluetoothDevice>>

    /**
     * Whether a scan cannot find anything because location is off, which only matters below API 31.
     */
    fun isLocationOffForScan(): Boolean

    /**
     * Opens a link to [device], bonding with it first if the phone has not yet (the OS shows its
     * pairing prompt and the device a code), and closes it again. No command reaches the device, so
     * the Zcash app need not be open on it.
     */
    suspend fun connect(device: LedgerBluetoothDevice)

    /**
     * Connects to [device], opens the Zcash app on it if it is elsewhere (the user confirms on the
     * device; the link may be reopened while the device switches apps), asks it to export the
     * ZIP 32 account [zip32AccountIndex] (the user approves the export on the device) and closes
     * every transport it opened. Enrollment holds no transport open: a later signing session opens
     * its own.
     *
     * The reads before the export get a short deadline and one retry over a fresh connection;
     * nothing is retried once the export was sent.
     *
     * @throws LedgerBondingFailedException if connecting to the device, the step that bonds it,
     *         fails.
     * @throws LedgerPairingTimedOutException if the whole pairing, the user's approval included,
     *         takes longer than enrollment allows.
     * @throws LedgerException for any failure once the link is up.
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

    override fun isLocationOffForScan(): Boolean = ledgerScannerProvider.isLocationOffForScan()

    override suspend fun connect(device: LedgerBluetoothDevice) = ledgerScannerProvider.connect(device).close()

    /**
     * Every transport opened here, the first one and each reconnect, is closed on the way out; the
     * SDK leaves them all to the caller, and closing one twice is harmless.
     */
    override suspend fun pair(
        device: LedgerBluetoothDevice,
        zip32AccountIndex: Zip32AccountIndex
    ): LedgerAccountPairing {
        val opened = mutableListOf<LedgerApduTransport>()
        val reconnect: suspend () -> LedgerApduTransport = {
            ledgerScannerProvider.connect(device).also { opened += it }
        }
        return try {
            withTimeout(PAIRING_TIMEOUT) {
                val connected = bond(device).also { opened += it }
                val appTransport = LedgerZcashApp.ensureZcashAppOpen(connected, reconnect).also { opened += it }
                LedgerDevice
                    .new(appTransport, VersionInfo.NETWORK)
                    .pairAccount(
                        zip32AccountIndex = zip32AccountIndex,
                        readTimeout = LedgerDevice.DEFAULT_PAIRING_READ_TIMEOUT,
                        reconnect = reconnect,
                    )
            }
        } catch (e: TimeoutCancellationException) {
            Twig.warn { "Ledger pairing: timed out" }
            throw LedgerPairingTimedOutException(e)
        } finally {
            withContext(NonCancellable) { opened.distinct().forEach { closeQuietly(it) } }
        }
    }

    private suspend fun bond(device: LedgerBluetoothDevice): LedgerApduTransport =
        try {
            ledgerScannerProvider.connect(device)
        } catch (e: LedgerException) {
            throw LedgerBondingFailedException(e)
        }

    /**
     * A failing close must not replace the outcome of the pairing.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeQuietly(transport: LedgerApduTransport) {
        try {
            transport.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Twig.warn { "Ledger pairing: closing a transport failed: ${e.javaClass.simpleName}" }
        }
    }

    private companion object {
        /**
         * The cap on the whole pairing, the user's approval on the device included.
         */
        val PAIRING_TIMEOUT = 5.minutes
    }
}
