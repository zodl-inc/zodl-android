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
 * check so the user-facing mismatch sheet can report it to support; [depositAddress] is the quote id
 * we can hand the swap provider, attached by whichever layer still has the quote in hand.
 *
 * It stays an [IllegalArgumentException] so every existing fail-closed rejection path — the repository
 * catch-all, the status-check throws — keeps behaving exactly as before.
 */
open class SwapQuoteMismatchException(
    val type: SwapQuoteMismatchType,
    message: String,
    val depositAddress: String? = null
) : IllegalArgumentException(message) {
    /** This exception with [address] attached, or itself when there is nothing new to attach. */
    open fun withDepositAddress(address: String?): SwapQuoteMismatchException =
        if (address == null || depositAddress != null) {
            this
        } else {
            SwapQuoteMismatchException(type = type, message = message.orEmpty(), depositAddress = address)
        }
}

/**
 * Maps a validator's asset slot name ("originAsset", "origin", "destinationAsset", "destination") to
 * the mismatch type reported for it.
 */
internal fun swapAssetMismatchType(name: String): SwapQuoteMismatchType =
    if (name.startsWith("origin", ignoreCase = true)) {
        SwapQuoteMismatchType.ORIGIN_ASSET
    } else {
        SwapQuoteMismatchType.DESTINATION_ASSET
    }
