package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the Ledger the user picked and bonded with while they open the Zcash app on it, so the
 * handshake screen that follows talks to the same device, together with the ZIP 32 account the user
 * chose for it next to the device list, which the handshake exports. Both are set and cleared
 * together. In memory only: the device carries its Bluetooth address, which stays out of navigation
 * arguments and saved state, and the handshake screen falls back to the wallet root when process
 * death empties it.
 */
interface LedgerSelectedDeviceRepository {
    fun set(
        device: LedgerBluetoothDevice,
        zip32AccountIndex: Zip32AccountIndex,
    )

    fun get(): LedgerBluetoothDevice?

    /**
     * The account chosen with the device [get] returns; null exactly when no device is held.
     */
    fun getZip32AccountIndex(): Zip32AccountIndex?

    fun clear()
}

class LedgerSelectedDeviceRepositoryImpl : LedgerSelectedDeviceRepository {
    private val selection = MutableStateFlow<Selection?>(null)

    override fun set(
        device: LedgerBluetoothDevice,
        zip32AccountIndex: Zip32AccountIndex,
    ) = selection.update { Selection(device, zip32AccountIndex) }

    override fun get(): LedgerBluetoothDevice? = selection.value?.device

    override fun getZip32AccountIndex(): Zip32AccountIndex? = selection.value?.zip32AccountIndex

    override fun clear() = selection.update { null }

    private data class Selection(
        val device: LedgerBluetoothDevice,
        val zip32AccountIndex: Zip32AccountIndex,
    )
}
