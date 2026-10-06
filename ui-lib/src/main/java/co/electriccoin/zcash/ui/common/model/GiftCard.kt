package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Zatoshi

/**
 * Joins the "Gift Card" label and the card's message in the redeem memo; the redeem screen shows the message the same
 * way.
 */
internal const val GIFT_CARD_MEMO_SEPARATOR = " · "

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
     * The card holds [spendable] funds that can be swept now. A redemption pays the network fee out of them, so the
     * user receives [redeemable].
     */
    data class Ready(
        val spendable: Zatoshi,
        val redeemable: Zatoshi
    ) : GiftCardStatus

    /**
     * Funds were found but cannot be spent yet.
     */
    data class Pending(
        val pending: Zatoshi
    ) : GiftCardStatus

    /**
     * Nothing to redeem: the card was already redeemed, has not been funded yet, or holds no more than the fee a
     * redemption would pay.
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
    class WrongNetwork(
        cause: Throwable? = null
    ) : GiftCardException("Gift card is for a different network", cause)

    /** The handle does not refer to a card held by the repository, e.g. after process death. */
    class UnknownHandle(
        cause: Throwable? = null
    ) : GiftCardException("Unknown gift card", cause)

    /** Gift card redemption is not available in this build. */
    class NotAvailable : GiftCardException("Gift card redemption is not available")

    /**
     * Another redemption of the same card is still using the card's temporary wallet (for example, one still being
     * closed). Retrying, or closing and opening the link again, resolves it.
     */
    class InUse(
        cause: Throwable? = null
    ) : GiftCardException("Gift card is in use", cause)

    /** A redemption was attempted before the card was checked; the card must be checked again first. */
    class NotChecked(
        cause: Throwable? = null
    ) : GiftCardException("Gift card was not checked", cause)

    /** The card holds nothing spendable above the fee a redemption would pay. */
    class NothingToRedeem(
        cause: Throwable? = null
    ) : GiftCardException("Gift card has nothing to redeem", cause)

    /**
     * The card's temporary wallet could not be created or synced, e.g. because the server could not be reached.
     * Checking the card again may succeed.
     */
    class CheckFailed(
        cause: Throwable? = null
    ) : GiftCardException("Gift card could not be checked", cause)

    /**
     * The redemption failed once its transaction may already have been created: the network did not accept it, or
     * creating or submitting it failed. The card has been reset: a new check reports its true state, and the
     * redemption can be attempted again.
     */
    class SubmitFailed(
        cause: Throwable? = null
    ) : GiftCardException("Gift card redemption was not submitted", cause)
}

/**
 * A successful redemption.
 *
 * @param txId the id of the submitted transaction.
 * @param received what the user's wallet receives, after the network fee; `null` when it is not known.
 */
data class GiftCardRedemption(
    val txId: String,
    val received: Zatoshi?
)

/**
 * The state of one gift card redemption, as held by
 * [co.electriccoin.zcash.ui.common.repository.GiftCardRepository] for as long as the redemption lasts, independently
 * of any screen.
 *
 * @param summary what the card's link says about the card; `null` until the link has been parsed.
 */
data class GiftCardSession(
    val summary: GiftCardSummary?,
    val phase: GiftCardPhase
)

/**
 * Where a [GiftCardSession] is.
 */
sealed interface GiftCardPhase {
    /** The link is being parsed, or the card is being checked on chain. */
    data object Checking : GiftCardPhase

    /** The card can be redeemed now. */
    data class Ready(
        val spendable: Zatoshi,
        val redeemable: Zatoshi
    ) : GiftCardPhase

    /**
     * Funds were found but cannot be spent yet. The card is checked again periodically while the session is observed.
     *
     * @param isRechecking whether a check the user asked for is running.
     */
    data class Pending(
        val pending: Zatoshi,
        val isRechecking: Boolean = false
    ) : GiftCardPhase

    /** Nothing to redeem. */
    data object Empty : GiftCardPhase

    /** The redemption is being created and submitted. It cannot be cancelled. */
    data object Redeeming : GiftCardPhase

    /** The redemption was submitted. */
    data class Redeemed(
        val redemption: GiftCardRedemption
    ) : GiftCardPhase

    /** The session failed; [GiftCardFailure.isRetryable] tells whether it can be retried. */
    data class Failed(
        val failure: GiftCardFailure
    ) : GiftCardPhase
}

/**
 * Why a [GiftCardSession] failed.
 */
enum class GiftCardFailure(
    val isRetryable: Boolean
) {
    /** The link is not a valid gift card link. */
    INVALID_LINK(isRetryable = false),

    /** The card is for another network than the wallet's. */
    WRONG_NETWORK(isRetryable = false),

    /** The link is no longer held by the app, e.g. after process death. */
    LINK_UNAVAILABLE(isRetryable = false),

    /** Gift card redemption is not available in this build. */
    NOT_AVAILABLE(isRetryable = false),

    /** The card could not be checked; checking it again may succeed. */
    CHECK_FAILED(isRetryable = true),

    /** The redemption failed; it is retried through a fresh check. */
    REDEEM_FAILED(isRetryable = true)
}
