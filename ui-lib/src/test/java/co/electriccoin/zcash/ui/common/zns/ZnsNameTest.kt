package co.electriccoin.zcash.ui.common.zns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZnsNameTest {
    @Test
    fun aNameWithEitherSuffixIsRecognizedInAnyCase() {
        assertEquals("alice", ZnsName.fromRecipientInput("alice.zcash")?.value)
        assertEquals("alice", ZnsName.fromRecipientInput("Alice.ZEC")?.value)
        assertEquals("alice", ZnsName.fromRecipientInput("  alice.zec ")?.value)
        assertEquals("a1", ZnsName.fromRecipientInput("A1.Zcash")?.value)
    }

    @Test
    fun theDisplayNameUsesTheCanonicalSuffix() {
        assertEquals("alice.zcash", ZnsName.fromRecipientInput("alice.zec")?.displayName)
    }

    @Test
    fun textWithoutASuffixIsNeverAName() {
        assertNull(ZnsName.fromRecipientInput("alice"))
        assertNull(ZnsName.fromRecipientInput("u1abcdef"))
        assertNull(ZnsName.fromRecipientInput(""))
        assertNull(ZnsName.fromRecipientInput("alice.zcash.com"))
    }

    @Test
    fun namesOutsideTheRegistryRulesAreRejected() {
        assertNull(ZnsName.fromRecipientInput(".zcash"))
        assertNull(ZnsName.fromRecipientInput("al-ice.zcash"))
        assertNull(ZnsName.fromRecipientInput("al_ice.zcash"))
        assertNull(ZnsName.fromRecipientInput("a.b.zcash"))
        assertNull(ZnsName.fromRecipientInput("ålice.zcash"))
        assertNull(ZnsName.fromRecipientInput("${"a".repeat(64)}.zcash"))
        assertEquals(63, ZnsName.fromRecipientInput("${"a".repeat(63)}.zcash")?.value?.length)
    }
}
