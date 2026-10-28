package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.PercentDecimal

// TODO [#292]: Should be moved to SDK-EXT-UI module.
// TODO [#292]: https://github.com/Electric-Coin-Company/zashi-android/issues/292
data class WalletSnapshot(
    val status: Synchronizer.Status,
    val progress: PercentDecimal,
    val synchronizerError: SynchronizerError?,
    val isSpendable: Boolean,
    val restoringState: WalletRestoringState,
    val blocksRemaining: Long = -1L,
    /** Target time (ZIP 208/218, so 25 s per block from NU7) of the blocks not yet scanned; -1 if unknown. */
    val behindSeconds: Long = -1L,
)
