package co.electriccoin.zcash.ui.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccountIdOffsetTest {
    @Test
    fun offsetIsWithinSixteenBitRange() {
        val offset = accountIdOffset("a1b2c3d4")
        assertTrue(offset in 0..0xFFFF, "offset $offset out of range")
    }

    @Test
    fun offsetIsDeterministicForSameId() {
        assertEquals(accountIdOffset("deadbeef"), accountIdOffset("deadbeef"))
    }

    @Test
    fun offsetDiffersForTwoRepresentativeAccounts() {
        // Two representative account key ids (Zashi vs Keystone).
        assertNotEquals(accountIdOffset("0011223344556677"), accountIdOffset("8899aabbccddeeff"))
    }
}
