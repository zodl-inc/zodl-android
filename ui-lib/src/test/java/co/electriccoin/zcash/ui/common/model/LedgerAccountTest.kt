package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Ledger account's ZIP 32 index comes from its stored binding, because the SDK account has none
 * — the device never reveals its seed fingerprint — and the interface default would dereference a
 * null. Its printed form redacts the device identity, which is linkable to a transparent address.
 */
class LedgerAccountTest {
    private val sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() }))

    @Test
    fun theZip32IndexComesFromTheBindingNotTheSdkAccount() {
        val sdkAccountWithoutIndex =
            mockk<Account> {
                every { accountUuid } returns AccountUuid.new(ByteArray(16) { it.toByte() })
                every { hdAccountIndex } returns null
            }

        val account = ledger(sdkAccount = sdkAccountWithoutIndex, deviceIdentity = "tpk0-x", index = 5L)

        assertEquals(Zip32AccountIndex.new(5L), account.hdAccountIndex)
    }

    @Test
    fun anUnboundAccountRefusesToReportAnIndexRatherThanGuessZero() {
        val unbound = ledger(deviceIdentity = null, index = null)

        assertFalse(unbound.isBound)
        val thrown = assertFailsWith<IllegalStateException> { unbound.hdAccountIndex }
        assertEquals("Ledger account has no stored binding", thrown.message)
    }

    @Test
    fun aBoundAccountReportsItsStoredIndex() {
        val bound = ledger(deviceIdentity = "tpk0-deadbeef", index = 5L)

        assertTrue(bound.isBound)
        assertEquals(Zip32AccountIndex.new(5L), bound.hdAccountIndex)
    }

    @Test
    fun theDeviceIdentityAndAddressesNeverReachTheLog() {
        val printed = ledger(deviceIdentity = "tpk0-deadbeef").toString()

        assertFalse(printed.contains("tpk0-deadbeef"))
        assertFalse(printed.contains("u1secret"))
        assertFalse(printed.contains("t1secret"))
    }

    @Test
    fun aLedgerAccountHasNoSaplingAddressOrBalance() {
        val account = ledger()

        assertNull(account.saplingAddress)
        assertNull(account.saplingBalance)
    }

    @Test
    fun walletsSortAsZodlThenKeystoneThenLedger() {
        val zashi = zashi()
        val keystone = keystone()
        val ledger = ledger()

        assertEquals(
            listOf<WalletAccount>(zashi, keystone, ledger),
            listOf<WalletAccount>(ledger, zashi, keystone).sortedDescending()
        )
    }

    private fun zashi() =
        ZashiAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
        )

    private fun keystone() =
        KeystoneAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
        )

    private fun ledger(
        sdkAccount: Account = this.sdkAccount,
        deviceIdentity: String? = "tpk0-deadbeef",
        index: Long? = 0L,
    ) = LedgerAccount(
        sdkAccount = sdkAccount,
        unifiedAddress = "u1secret",
        transparentAddress = "t1secret",
        orchardBalance = null,
        ironwoodBalance = null,
        transparentBalance = null,
        isSelected = false,
        deviceIdentity = deviceIdentity,
        zip32AccountIndex = index?.let { Zip32AccountIndex.new(it) },
    )
}
