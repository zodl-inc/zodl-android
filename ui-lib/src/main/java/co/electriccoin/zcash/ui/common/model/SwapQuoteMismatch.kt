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
 * The report context — [depositAddress] (the quote id support can hand the swap provider), [provider]
 * and both [SwapAsset]s of the request — is attached in place by whichever layer still has it, so the
 * original throw site's stack trace survives.
 *
 * It stays an [IllegalArgumentException] so every existing fail-closed rejection path — the repository
 * catch-all, the status-check throws — keeps behaving exactly as before.
 */
open class SwapQuoteMismatchException(
    val type: SwapQuoteMismatchType,
    message: String
) : IllegalArgumentException(message) {
    var depositAddress: String? = null
    var provider: String? = null
    var originAsset: SwapAsset? = null
    var destinationAsset: SwapAsset? = null
}
