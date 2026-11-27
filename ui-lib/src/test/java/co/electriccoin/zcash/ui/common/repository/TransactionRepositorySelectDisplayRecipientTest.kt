package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.TransactionRecipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionRepositorySelectDisplayRecipientTest {
    @Test
    fun selectDisplayRecipient_prefersExternalRegardlessOfOrder() {
        val external = TransactionRecipient(addressValue = "external-address", accountUuid = null)
        val internal = TransactionRecipient(addressValue = "internal-address", accountUuid = accountUuid())

        assertEquals("external-address", selectDisplayRecipient(listOf(internal, external)))
        assertEquals("external-address", selectDisplayRecipient(listOf(external, internal)))
    }

    @Test
    fun selectDisplayRecipient_internalOnly_returnsItsStoredAddress() {
        val internal = TransactionRecipient(addressValue = "internal-address", accountUuid = accountUuid())

        assertEquals("internal-address", selectDisplayRecipient(listOf(internal)))
    }

    @Test
    fun selectDisplayRecipient_internalWithNullAddressOnly_returnsNull() {
        val internal = TransactionRecipient(addressValue = null, accountUuid = accountUuid())

        assertNull(selectDisplayRecipient(listOf(internal)))
    }

    @Test
    fun selectDisplayRecipient_emptyList_returnsNull() {
        assertNull(selectDisplayRecipient(emptyList()))
    }

    private fun accountUuid(): AccountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
}
