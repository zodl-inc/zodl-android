package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.type.AddressType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one memo rule shared by the Send form and Max: no memo for transparent or TEX recipients, the text
 * otherwise, and an empty text is no memo.
 */
class ProposalMemoPolicyTest {
    @Test
    fun transparentAndTexRecipientsGetNoMemo() {
        assertNull(ProposalMemoPolicy.memoFor(AddressType.Transparent, "hello"))
        assertNull(ProposalMemoPolicy.memoFor(AddressType.Tex, "hello"))
    }

    @Test
    fun shieldedCapableRecipientsKeepTheMemoText() {
        assertEquals("hello", ProposalMemoPolicy.memoFor(AddressType.Unified, "hello"))
        assertEquals("hello", ProposalMemoPolicy.memoFor(AddressType.Shielded, "hello"))
    }

    @Test
    fun anUnknownRecipientTypeKeepsTheMemoText() {
        assertEquals("hello", ProposalMemoPolicy.memoFor(null, "hello"))
    }

    @Test
    fun anEmptyTextIsNoMemo() {
        assertNull(ProposalMemoPolicy.memoFor(AddressType.Unified, ""))
        assertNull(ProposalMemoPolicy.memoFor(AddressType.Transparent, ""))
    }

    @Test
    fun isTransparentOrTexCoversExactlyTransparentAndTex() {
        assertTrue(AddressType.Transparent.isTransparentOrTex)
        assertTrue(AddressType.Tex.isTransparentOrTex)
        assertFalse(AddressType.Unified.isTransparentOrTex)
        assertFalse(AddressType.Shielded.isTransparentOrTex)
        assertFalse(AddressType.Invalid().isTransparentOrTex)
        assertFalse((null as AddressType?).isTransparentOrTex)
    }
}
