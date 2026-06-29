package co.electriccoin.zcash.ui.common.model

/**
 * Discriminates the swap liquidity provider a [SwapQuote] / swap came from. [value] is the stable,
 * persisted token written into `TransactionSwapMetadata.provider` (a String) — keep it stable so existing
 * metadata keeps resolving. It is intentionally decoupled from the SwapKit API provider strings:
 * [MAYA] maps to the `MAYACHAIN_STREAMING` quote provider and the `MAYACHAIN` `/tokens` namespace.
 */
enum class SwapProvider(
    val value: String
) {
    NEAR("near"),
    MAYA("maya"),
    ;

    companion object {
        /** Parses a persisted [value] back to a [SwapProvider]; defaults to [NEAR] (the legacy provider). */
        fun from(value: String): SwapProvider =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: NEAR
    }
}
