package co.electriccoin.zcash.ui.common.model

/** Direction of a swap relative to ZEC: selling ZEC for another asset, or buying ZEC. */
enum class SwapDirection { SWAP_FROM_ZEC, SWAP_INTO_ZEC }

/** The [SwapMode] a swap in this direction is quoted and priced in. */
fun SwapDirection.toSwapMode(): SwapMode =
    when (this) {
        SwapDirection.SWAP_FROM_ZEC -> SwapMode.EXACT_INPUT
        SwapDirection.SWAP_INTO_ZEC -> SwapMode.FLEX_INPUT
    }
