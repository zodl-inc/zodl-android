package co.electriccoin.zcash.ui.common.model

import androidx.annotation.StringRes
import co.electriccoin.zcash.ui.R

/**
 * Which request-vs-response check rejected a swap quote (MOB-1340).
 *
 * [reportLabelRes] names the failed check in the mismatch report email; it is never shown in the UI,
 * where the sheet stays deliberately generic.
 */
enum class SwapQuoteMismatchType(
    @field:StringRes val reportLabelRes: Int
) {
    RECIPIENT_ADDRESS(R.string.swap_mismatch_type_recipientAddress),
    REFUND_ADDRESS(R.string.swap_mismatch_type_refundAddress),
    ORIGIN_ASSET(R.string.swap_mismatch_type_originAsset),
    DESTINATION_ASSET(R.string.swap_mismatch_type_destinationAsset),
    SWAP_TYPE(R.string.swap_mismatch_type_swapType),
    SLIPPAGE_TOLERANCE(R.string.swap_mismatch_type_slippageTolerance),
    INPUT_AMOUNT(R.string.swap_mismatch_type_inputAmount),
    OUTPUT_AMOUNT(R.string.swap_mismatch_type_outputAmount),
    REQUESTED_AMOUNT(R.string.swap_mismatch_type_requestedAmount),
    SLIPPAGE_EXCEEDED(R.string.swap_mismatch_type_slippageExceeded),
    NON_POSITIVE_AMOUNT(R.string.swap_mismatch_type_nonPositiveAmount),
}

/**
 * Thrown when a swap quote disagrees with the request that produced it. [type] identifies the failed
 * check so the user-facing mismatch sheet can report it to support.
 *
 * The hierarchy is closed: a rejection is either a plain [Rejected] check failure or the
 * [AmountInconsistency] special case, which carries the non-sensitive detail of its own check. Both are
 * immutable — every field is a constructor `val`, so the whole report context the mismatch sheet and its
 * support email need travels on the rejection itself and nothing is ever written to an exception after it
 * was thrown. A layer that knows more about the rejected quote attaches it by constructing a new
 * rejection chained to this one — see [withQuoteContext] and [withReportContext].
 *
 * [depositAddress] (the quote id support can hand the swap provider), [provider], [originAsset] and
 * [destinationAsset] stay null until such a layer supplies them: a failing check throws before there is a
 * response to read the provider's side from, and knows nothing of the request's two assets.
 *
 * It stays an [IllegalArgumentException] so every existing fail-closed rejection path — the repository
 * catch-all, the status-check throws — keeps behaving exactly as before.
 */
sealed class SwapQuoteMismatchException(
    val type: SwapQuoteMismatchType,
    override val message: String,
    val depositAddress: String?,
    val provider: String?,
    val originAsset: SwapAsset?,
    val destinationAsset: SwapAsset?,
    cause: Throwable?
) : IllegalArgumentException(message, cause) {
    /** A quote rejected by one of the request-vs-response checks. */
    class Rejected(
        type: SwapQuoteMismatchType,
        message: String,
        depositAddress: String? = null,
        provider: String? = null,
        originAsset: SwapAsset? = null,
        destinationAsset: SwapAsset? = null,
        cause: Throwable? = null
    ) : SwapQuoteMismatchException(
            type = type,
            message = message,
            depositAddress = depositAddress,
            provider = provider,
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            cause = cause
        )

    /**
     * Thrown when the server's raw base-unit amount does not equal the exact decimal expansion of its
     * displayed `*Formatted` value.
     *
     * The exact-equality posture is intentional and must NOT be relaxed to a tolerance: it is the "trust
     * the quote 0% or 100%" stance (MOB-1371). It is kept as a distinct rejection — so it still flows
     * through the generic quote-rejection handling unchanged — that carries only the non-sensitive
     * [field] / [decimals]. The repository uses those to emit a sanitized crash-monitoring signal (never
     * the amounts), so that if the 1Click API ever starts returning rounded display values, the resulting
     * rejections surface as an observable "quotes blocked" signal instead of silent breakage for users.
     */
    class AmountInconsistency(
        type: SwapQuoteMismatchType,
        val field: String,
        val decimals: Int,
        message: String,
        depositAddress: String? = null,
        provider: String? = null,
        originAsset: SwapAsset? = null,
        destinationAsset: SwapAsset? = null,
        cause: Throwable? = null
    ) : SwapQuoteMismatchException(
            type = type,
            message = message,
            depositAddress = depositAddress,
            provider = provider,
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            cause = cause
        )

    /**
     * The same rejection carrying what the provider's response already says about the rejected quote,
     * keeping whatever assets it was given. Copied rather than mutated in place — the original is chained
     * as the cause, so the failing check's own throw site survives in the stack trace.
     */
    internal fun withQuoteContext(depositAddress: String?, provider: String): SwapQuoteMismatchException =
        copyWithContext(
            depositAddress = depositAddress,
            provider = provider,
            originAsset = originAsset,
            destinationAsset = destinationAsset
        )

    /**
     * The same rejection carrying the complete report context the mismatch sheet and its support email
     * read: the quote id support can look the quote up by, the provider that returned it and both sides of
     * the request. Copied the same way [withQuoteContext] is.
     */
    internal fun withReportContext(
        depositAddress: String?,
        provider: String,
        originAsset: SwapAsset,
        destinationAsset: SwapAsset
    ): SwapQuoteMismatchException =
        copyWithContext(
            depositAddress = depositAddress,
            provider = provider,
            originAsset = originAsset,
            destinationAsset = destinationAsset
        )

    private fun copyWithContext(
        depositAddress: String?,
        provider: String,
        originAsset: SwapAsset?,
        destinationAsset: SwapAsset?
    ): SwapQuoteMismatchException =
        when (this) {
            is Rejected -> {
                Rejected(
                    type = type,
                    message = message,
                    depositAddress = depositAddress,
                    provider = provider,
                    originAsset = originAsset,
                    destinationAsset = destinationAsset,
                    cause = this
                )
            }

            is AmountInconsistency -> {
                AmountInconsistency(
                    type = type,
                    field = field,
                    decimals = decimals,
                    message = message,
                    depositAddress = depositAddress,
                    provider = provider,
                    originAsset = originAsset,
                    destinationAsset = destinationAsset,
                    cause = this
                )
            }
        }
}
