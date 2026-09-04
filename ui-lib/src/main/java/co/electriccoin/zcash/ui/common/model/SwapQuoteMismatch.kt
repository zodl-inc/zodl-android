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
 * immutable — the report context the mismatch sheet needs is assembled into a [SwapQuoteMismatch] value
 * by the repository, so nothing is ever attached to an exception after it was thrown.
 *
 * [depositAddress] (the quote id support can hand the swap provider) and [provider] hold what the
 * provider's response already says about the rejected quote; both stay null until there is a response to
 * take them from, and are set by the layer that has it — see [withQuoteContext].
 *
 * It stays an [IllegalArgumentException] so every existing fail-closed rejection path — the repository
 * catch-all, the status-check throws — keeps behaving exactly as before.
 */
sealed class SwapQuoteMismatchException(
    val type: SwapQuoteMismatchType,
    override val message: String,
    val depositAddress: String?,
    val provider: String?,
    cause: Throwable?
) : IllegalArgumentException(message, cause) {
    /** A quote rejected by one of the request-vs-response checks. */
    class Rejected(
        type: SwapQuoteMismatchType,
        message: String,
        depositAddress: String? = null,
        provider: String? = null,
        cause: Throwable? = null
    ) : SwapQuoteMismatchException(
            type = type,
            message = message,
            depositAddress = depositAddress,
            provider = provider,
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
        cause: Throwable? = null
    ) : SwapQuoteMismatchException(
            type = type,
            message = message,
            depositAddress = depositAddress,
            provider = provider,
            cause = cause
        )

    /**
     * The same rejection carrying the report context the provider's response supplies. Copied rather than
     * mutated in place — the original is chained as the cause, so the failing check's own throw site
     * survives in the stack trace.
     */
    internal fun withQuoteContext(depositAddress: String?, provider: String): SwapQuoteMismatchException =
        when (this) {
            is Rejected -> {
                Rejected(
                    type = type,
                    message = message,
                    depositAddress = depositAddress,
                    provider = provider,
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
                    cause = this
                )
            }
        }
}

/**
 * The report context of a rejected swap quote (MOB-1340), assembled once by the repository from the
 * rejection and the request that produced it: the [provider] that returned the quote, the
 * [depositAddress] support can look the quote up by, both sides of the request and the failed check.
 *
 * An immutable value carried by the quote's error state, so the mismatch sheet and its support email read
 * a snapshot rather than an exception that could still be written to.
 */
data class SwapQuoteMismatch(
    val type: SwapQuoteMismatchType,
    val provider: String,
    val depositAddress: String?,
    val originAsset: SwapAsset,
    val destinationAsset: SwapAsset
)
