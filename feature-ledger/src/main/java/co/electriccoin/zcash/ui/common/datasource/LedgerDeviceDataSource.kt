package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.ledger.LedgerApduTransport
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDevice
import cash.z.ecc.android.sdk.ledger.LedgerZcashApp
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.spackle.Twig
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
     * pairing prompt and the device a code), asks the device which app it runs and closes the link
     * again. Only that query reaches the device, so the Zcash app need not be open on it.
     *
     * @return whether the device reported the Zcash app running; false when it runs another app or
     *         the query failed, which leaves the connection itself a success.
     */
    suspend fun connect(device: LedgerBluetoothDevice): Boolean

    /**
     * Connects to [device], asks it to open the Zcash app if it is elsewhere (the user confirms on
     * the device; the link may be reopened while the device switches apps) and closes every
     * transport it opened. Returns once the Zcash app runs; a declined or failed request throws.
     *
     * @throws LedgerPairingTimedOutException if the request, the user's confirmation included,
     *         takes longer than enrollment allows, as [pair] does.
     * @throws LedgerException for any failure reaching or talking to the device.
     */
    suspend fun openZcashApp(device: LedgerBluetoothDevice)

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
     * The phone has bonded with [device] by the time this runs ([connect]), so a failed connection
     * here is a device that went away, not a failed pairing.
     *
     * @throws LedgerPairingTimedOutException if the whole pairing, the user's approval included,
     *         takes longer than enrollment allows.
     * @throws LedgerException for any failure reaching or talking to the device.
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

    override suspend fun connect(device: LedgerBluetoothDevice): Boolean {
        val transport = ledgerScannerProvider.connect(device)
        return try {
            isZcashAppRunning(transport)
        } finally {
            withContext(NonCancellable) { closeQuietly(transport) }
        }
    }

    /**
     * The bond is in place once the link is up, so a query that fails only leaves the running app
     * unknown; the flow then asks the device to open the Zcash app as it would anyway.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun isZcashAppRunning(transport: LedgerApduTransport): Boolean =
        try {
            LedgerZcashApp.currentApp(transport).isZcash
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Twig.info { "Ledger connect: the running app is unknown: ${e.javaClass.simpleName}" }
            false
        }

    /**
     * Every transport opened here, the first one and each reconnect while the device switches apps,
     * is closed on the way out, and the request is capped as in [pair].
     */
    override suspend fun openZcashApp(device: LedgerBluetoothDevice) {
        val opened = mutableListOf<LedgerApduTransport>()
        val reconnect: suspend () -> LedgerApduTransport = {
            ledgerScannerProvider.connect(device).also { opened += it }
        }
        try {
            withTimeout(PAIRING_TIMEOUT) {
                LedgerZcashApp.ensureZcashAppOpen(reconnect(), reconnect).also { opened += it }
            }
        } catch (e: TimeoutCancellationException) {
            Twig.warn { "Ledger open app: timed out" }
            throw LedgerPairingTimedOutException(e)
        } finally {
            withContext(NonCancellable) { opened.distinct().forEach { closeQuietly(it) } }
        }
    }

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
                val connected = reconnect()
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

    /**
     * A failing close must not replace the outcome of the step that opened the transport.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeQuietly(transport: LedgerApduTransport) {
        try {
            transport.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Twig.warn { "Ledger enrollment: closing a transport failed: ${e.javaClass.simpleName}" }
        }
    }

    private companion object {
        /**
         * The cap on the whole pairing, and on the request to open the Zcash app, the user's
         * confirmation on the device included.
         */
        val PAIRING_TIMEOUT = 5.minutes
    }
}
