package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The account being paired again is held with the index its binding stored, or with none when the
 * binding holds no index, and both are replaced and cleared together.
 */
class LedgerRepairTargetRepositoryTest {
    private val first = AccountUuid.new(ByteArray(16) { it.toByte() })

    private val second = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })

    @Test
    fun aNewRepositoryHoldsNoTarget() {
        val repository = LedgerRepairTargetRepositoryImpl()

        assertNull(repository.get())
        assertNull(repository.getZip32AccountIndex())
    }

    @Test
    fun setHoldsTheAccountWithItsStoredIndex() {
        val repository = LedgerRepairTargetRepositoryImpl()

        repository.set(first, Zip32AccountIndex.new(4))

        assertEquals(first, repository.get())
        assertEquals(Zip32AccountIndex.new(4), repository.getZip32AccountIndex())
    }

    @Test
    fun anAccountWithoutAStoredIndexIsHeldWithNone() {
        val repository = LedgerRepairTargetRepositoryImpl()

        repository.set(first, null)

        assertEquals(first, repository.get())
        assertNull(repository.getZip32AccountIndex())
    }

    @Test
    fun setAgainReplacesTheIndexEvenWithNone() {
        val repository = LedgerRepairTargetRepositoryImpl()
        repository.set(first, Zip32AccountIndex.new(4))

        repository.set(second, null)

        assertEquals(second, repository.get())
        assertNull(repository.getZip32AccountIndex())
    }

    @Test
    fun clearForgetsTheAccountAndItsIndex() {
        val repository = LedgerRepairTargetRepositoryImpl()
        repository.set(first, Zip32AccountIndex.new(4))

        repository.clear()

        assertNull(repository.get())
        assertNull(repository.getZip32AccountIndex())
    }
}
