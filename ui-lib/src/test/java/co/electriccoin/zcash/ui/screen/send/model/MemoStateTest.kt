package co.electriccoin.zcash.ui.screen.send.model

import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.type.AddressType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Send form's memo rules. A memo typed for a shielded recipient stays in the (then hidden) field when the
 * recipient changes to a transparent or TEX one; it must neither be sent nor block sending.
 */
class MemoStateTest {
    private val tooLong = MemoState.new("x".repeat(Memo.MAX_MEMO_LENGTH_BYTES + 1))

    @Test
    fun texDropsMemo() {
        assertEquals("", MemoState.new("hello").textToSend(AddressType.Tex))
    }

    @Test
    fun transparentDropsMemo() {
        assertEquals("", MemoState.new("hello").textToSend(AddressType.Transparent))
    }

    @Test
    fun shieldedCapableRecipientsKeepTheMemo() {
        assertEquals("hello", MemoState.new("hello").textToSend(AddressType.Unified))
        assertEquals("hello", MemoState.new("hello").textToSend(AddressType.Shielded))
    }

    @Test
    fun aTooLongMemoDoesNotBlockSendingToTex() {
        assertTrue(tooLong is MemoState.TooLong)
        assertTrue(tooLong.allowsSendTo(AddressType.Tex))
        assertTrue(tooLong.allowsSendTo(AddressType.Transparent))
    }

    @Test
    fun aTooLongMemoBlocksSendingToAShieldedCapableRecipient() {
        assertFalse(tooLong.allowsSendTo(AddressType.Unified))
        assertFalse(tooLong.allowsSendTo(null))
    }

    @Test
    fun aCorrectMemoAllowsSendingAnywhere() {
        assertTrue(MemoState.new("hello").allowsSendTo(AddressType.Unified))
        assertTrue(MemoState.new("hello").allowsSendTo(AddressType.Tex))
    }
}
