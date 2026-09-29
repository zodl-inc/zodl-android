package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the Ledger the user picked and bonded with while they open the Zcash app on it, so the
 * handshake screen that follows talks to the same device. In memory only: the device carries its
 * Bluetooth address, which stays out of navigation arguments and saved state, and the handshake
 * screen falls back to the wallet root when process death empties it.
 */
interface LedgerSelectedDeviceRepository {
    fun set(device: LedgerBluetoothDevice)

    fun get(): LedgerBluetoothDevice?

    fun clear()
}

class LedgerSelectedDeviceRepositoryImpl : LedgerSelectedDeviceRepository {
    private val device = MutableStateFlow<LedgerBluetoothDevice?>(null)

    override fun set(device: LedgerBluetoothDevice) = this.device.update { device }

    override fun get(): LedgerBluetoothDevice? = device.value

    override fun clear() = device.update { null }
}
