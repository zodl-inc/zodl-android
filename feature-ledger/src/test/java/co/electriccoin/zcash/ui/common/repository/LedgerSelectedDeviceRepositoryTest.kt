package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The selected device and the account chosen with it are held, replaced and cleared together, so
 * the handshake never pairs one device with an account chosen for another.
 */
class LedgerSelectedDeviceRepositoryTest {
    @Test
    fun aNewRepositoryHoldsNothing() {
        val repository = LedgerSelectedDeviceRepositoryImpl()

        assertNull(repository.get())
        assertNull(repository.getZip32AccountIndex())
    }

    @Test
    fun setHoldsTheDeviceAndItsAccountTogether() {
        val repository = LedgerSelectedDeviceRepositoryImpl()
        val device = device("AA")

        repository.set(device, Zip32AccountIndex.new(7))

        assertSame(device, repository.get())
        assertEquals(Zip32AccountIndex.new(7), repository.getZip32AccountIndex())
    }

    @Test
    fun setAgainReplacesBothTheDeviceAndTheAccount() {
        val repository = LedgerSelectedDeviceRepositoryImpl()
        val second = device("BB")
        repository.set(device("AA"), Zip32AccountIndex.new(7))

        repository.set(second, Zip32AccountIndex.new(0))

        assertSame(second, repository.get())
        assertEquals(Zip32AccountIndex.new(0), repository.getZip32AccountIndex())
    }

    @Test
    fun clearForgetsBothTheDeviceAndTheAccount() {
        val repository = LedgerSelectedDeviceRepositoryImpl()
        repository.set(device("AA"), Zip32AccountIndex.new(7))

        repository.clear()

        assertNull(repository.get())
        assertNull(repository.getZip32AccountIndex())
    }

    private fun device(identifier: String) =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = identifier,
            rssi = -40,
        )
}
