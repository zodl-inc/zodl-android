package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.type.AddressType

/**
 * Whether a recipient of this type is a transparent or TEX address, i.e. one that cannot receive a memo and
 * cannot be sent a zero amount. A null type (no recipient yet) is neither.
 */
val AddressType?.isTransparentOrTex: Boolean
    get() = this == AddressType.Transparent || this == AddressType.Tex

/**
 * The single memo rule shared by every proposal path (Send form review and Max), so the proposal priced for
 * Max cannot disagree with the one built at review.
 */
object ProposalMemoPolicy {
    /**
     * @return the memo to propose with, or null when there must be none: the recipient is transparent or TEX
     * (a memo would be rejected as `TransparentMemo`), or the text is empty.
     */
    fun memoFor(recipientType: AddressType?, memoText: String): String? =
        memoText.takeUnless { recipientType.isTransparentOrTex || it.isEmpty() }
}
