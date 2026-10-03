package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Zatoshi

/**
 * Opaque reference to a gift card that has been parsed and is held by a
 * [co.electriccoin.zcash.ui.common.repository.GiftCardRepository]. It never carries the card's link or secret, so
 * it is safe to keep in UI state.
 */
@JvmInline
value class GiftCardHandle(
    val id: String
)

/**
 * Who issued a gift card link.
 */
enum class GiftCardOrigin {
    ZODL,
    VIZOR
}

/**
 * What a gift card link says about itself. The link's spending secret is deliberately not part of this type.
 *
 * @param statedAmount the amount written into the link by its issuer, if any. It is informational only: the
 * redeemable amount is what [GiftCardStatus.Ready.spendable] reports after the card has been checked on chain.
 * @param message an optional note from whoever made the card, shown to the user as a message from the sender.
 */
data class GiftCardSummary(
    val handle: GiftCardHandle,
    val origin: GiftCardOrigin,
    val birthdayHeight: Long,
    val statedAmount: Zatoshi?,
    val message: String?,
)

/**
 * The on-chain state of a gift card, as found by a sync of the card's own (temporary) wallet.
 */
sealed interface GiftCardStatus {
    /**
     * The card holds [spendable] funds that can be swept now.
     */
    data class Ready(
        val spendable: Zatoshi
    ) : GiftCardStatus

    /**
     * Funds were found but cannot be spent yet.
     *
     * @param confirmationsRemaining how many more blocks are needed, when the implementation knows it.
     */
    data class Pending(
        val pending: Zatoshi,
        val confirmationsRemaining: Int?
    ) : GiftCardStatus

    /**
     * Nothing to redeem: the card was already redeemed, or has not been funded yet.
     */
    data object Empty : GiftCardStatus
}

/**
 * Failures surfaced by a [co.electriccoin.zcash.ui.common.repository.GiftCardRepository].
 *
 * Messages are fixed strings: they must never include any part of a gift card link, which carries a spending
 * secret.
 */
sealed class GiftCardException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    /** The text is not a gift card link, or the link is malformed or of an unsupported version. */
    class InvalidLink(
        cause: Throwable? = null
    ) : GiftCardException("Invalid gift card link", cause)

    /** The card was made for a different Zcash network than this wallet's. */
    class WrongNetwork : GiftCardException("Gift card is for a different network")

    /** The handle does not refer to a card held by the repository, e.g. after process death. */
    class UnknownHandle : GiftCardException("Unknown gift card")

    /** Gift card redemption is not available in this build. */
    class NotAvailable : GiftCardException("Gift card redemption is not available")
}
