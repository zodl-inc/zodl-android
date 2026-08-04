package co.electriccoin.zcash.ui.screen.ironwood

import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork

/**
 * Hardcoded Ironwood (NU6.3) shielded-pool activation heights.
 *
 * The Android SDK exposes only [ZcashNetwork.saplingActivationHeight]; the NU6.3 activation height is
 * not surfaced through any JNI binding, so these final/tagged values live app-side.
 *
 * - Mainnet `3_428_143` (~2026-07-28 13:00 UTC; tagged NU6.3 activation, Zebra 6.0.0)
 * - Testnet `4_134_000`
 */
object IronwoodActivation {
    private const val MAINNET_HEIGHT = 3_428_143L
    private const val TESTNET_HEIGHT = 4_134_000L

    fun heightFor(network: ZcashNetwork): BlockHeight =
        BlockHeight.new(
            if (network == ZcashNetwork.Mainnet) MAINNET_HEIGHT else TESTNET_HEIGHT
        )
}
