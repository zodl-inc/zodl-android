package co.electriccoin.zcash.ui.common.model

/**
 * Which request-vs-response check rejected a swap quote (MOB-1340).
 *
 * [reportLabel] is English support-facing copy for the mismatch report email only — it is never shown
 * in the UI (the sheet is deliberately generic), so it is a Kotlin property rather than a string resource.
 */
enum class SwapQuoteMismatchType(
    val reportLabel: String
) {
    RECIPIENT_ADDRESS("Recipient address"),
    REFUND_ADDRESS("Refund address"),
    ORIGIN_ASSET("Origin asset"),
    DESTINATION_ASSET("Destination asset"),
    SWAP_TYPE("Swap type"),
    SLIPPAGE_TOLERANCE("Slippage tolerance"),
    INPUT_AMOUNT("Input amount"),
    OUTPUT_AMOUNT("Output amount"),
    REQUESTED_AMOUNT("Requested amount"),
    SLIPPAGE_EXCEEDED("Slippage exceeded"),
    NON_POSITIVE_AMOUNT("Non-positive amount"),
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
