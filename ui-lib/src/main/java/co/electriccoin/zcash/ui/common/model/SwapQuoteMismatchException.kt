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
 * The hierarchy is closed and immutable — every field is a constructor `val`, so the whole report context
 * the mismatch sheet and its support email need travels on the rejection itself and nothing is ever
 * written to an exception after it was thrown. A layer that knows more about the rejected quote attaches
 * it by constructing a new rejection chained to this one — see [withQuoteContext] and [withReportContext].
 *
 * A failing check throws either a plain [Rejected] or the [AmountInconsistency] special case, which
 * carries the non-sensitive detail of its own check. Neither knows the request's two assets, and both
 * learn the [provider] only once a layer that read the provider's response supplies it. [Reported] is the
 * terminal form the repository builds from either of them: it is the only rejection that carries the
 * complete report context, with [provider], [originAsset] and [destinationAsset] non-null, and the only
 * one the mismatch sheet routes on — so the sheet's arguments need no null checks.
 *
 * [depositAddress] — the quote id support can hand the swap provider — stays nullable throughout,
 * including on [Reported]: a rejection thrown before the provider returned a quote has no quote id to
 * report, and the email says so.
 *
 * It stays an [IllegalArgumentException] so every existing fail-closed rejection path — the repository
 * catch-all, the status-check throws — keeps behaving exactly as before.
 */
sealed class SwapQuoteMismatchException(
    val type: SwapQuoteMismatchType,
    override val message: String,
    val depositAddress: String?,
    cause: Throwable?
) : IllegalArgumentException(message, cause) {
    /** The swap provider that returned the rejected quote, once a layer that read its response knows it. */
    open val provider: String? get() = null

    /** The asset the rejected request sells; carried by [Reported] alone. */
    open val originAsset: SwapAsset? get() = null

    /** The asset the rejected request buys; carried by [Reported] alone. */
    open val destinationAsset: SwapAsset? get() = null

    /** A quote rejected by one of the request-vs-response checks. */
    class Rejected(
        type: SwapQuoteMismatchType,
        message: String,
        depositAddress: String? = null,
        override val provider: String? = null,
        cause: Throwable? = null
    ) : SwapQuoteMismatchException(
            type = type,
            message = message,
            depositAddress = depositAddress,
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
        override val provider: String? = null,
        cause: Throwable? = null
    ) : SwapQuoteMismatchException(
            type = type,
            message = message,
            depositAddress = depositAddress,
            cause = cause
        )

    /**
     * A rejection carrying the complete report context the mismatch sheet and its support email read: the
     * provider that returned the quote, both sides of the request and, when the quote got far enough to
     * have one, the quote id. Produced by [withReportContext] and by nothing else, which is what makes
     * the sheet's arguments a compile-time guarantee rather than a runtime check.
     */
    class Reported(
        type: SwapQuoteMismatchType,
        message: String,
        depositAddress: String?,
        override val provider: String,
        override val originAsset: SwapAsset,
        override val destinationAsset: SwapAsset,
        cause: Throwable? = null
    ) : SwapQuoteMismatchException(
            type = type,
            message = message,
            depositAddress = depositAddress,
            cause = cause
        )

    /**
     * The same rejection carrying what the provider's response already says about the rejected quote,
     * keeping the form it was thrown in so the sanitized crash-monitoring signal still sees an
     * [AmountInconsistency] as one. Copied rather than mutated in place — the original is chained as the
     * cause, so the failing check's own throw site survives in the stack trace.
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

            is Reported -> {
                Reported(
                    type = type,
                    message = message,
                    depositAddress = depositAddress,
                    provider = provider,
                    originAsset = originAsset,
                    destinationAsset = destinationAsset,
                    cause = this
                )
            }
        }

    /**
     * The same rejection as a [Reported] one, carrying the complete report context: the quote id support
     * can look the quote up by, the provider that returned it and both sides of the request. Copied the
     * same way [withQuoteContext] is.
     */
    internal fun withReportContext(
        depositAddress: String?,
        provider: String,
        originAsset: SwapAsset,
        destinationAsset: SwapAsset
    ): Reported =
        Reported(
            type = type,
            message = message,
            depositAddress = depositAddress,
            provider = provider,
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            cause = this
        )
}

/**
 * Sanitized non-fatal reported to crash monitoring when a request-vs-response check rejects a quote
 * (MOB-1371, MOB-1340). Carries only the mismatch type — plus the field name and decimal precision for the
 * amount-consistency case — never the amounts (see the release log-redaction hardening), so it does not
 * leak transaction values to crash reporting.
 *
 * Reporting it means a future 1Click change surfaces as an observable "quotes blocked" signal instead of
 * silent breakage for users. The rejection itself still fails closed: it is stored as the quote's error
 * state, from where the mismatch sheet reports it.
 */
internal fun swapQuoteMismatchSignal(e: SwapQuoteMismatchException): Exception =
    if (e is SwapQuoteMismatchException.AmountInconsistency) {
        SwapQuoteMismatchRejectedSignal("type=${e.type}, field=${e.field}, decimals=${e.decimals}")
    } else {
        SwapQuoteMismatchRejectedSignal("type=${e.type}")
    }

private class SwapQuoteMismatchRejectedSignal(
    detail: String
) : Exception("Swap quote validation rejected a quote ($detail)")
