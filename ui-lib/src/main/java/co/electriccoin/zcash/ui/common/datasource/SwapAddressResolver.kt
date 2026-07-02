package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.model.DynamicSwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.ZcashShieldedSwapAddress
import co.electriccoin.zcash.ui.common.model.ZcashSwapAddress
import co.electriccoin.zcash.ui.common.model.ZcashTransparentSwapAddress
import co.electriccoin.zcash.ui.common.model.isZCashAsset
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider

/**
 * Resolves a raw address string from a provider's quote/status response into the typed [SwapAddress]
 * the rest of the app expects: a [ZcashSwapAddress] (shielded/transparent, validated against the live
 * wallet) when the ZEC side of the swap owns that address, or a [DynamicSwapAddress] (foreign chain,
 * opaque to us) otherwise. Shared by every [SwapDataSource] implementation — the resolution rule only
 * depends on which side of the swap is ZEC, not on the provider.
 */
class SwapAddressResolver(
    private val synchronizerProvider: SynchronizerProvider
) {
    /** The address funds are deposited to: ours if [originAsset] is ZEC, the provider's otherwise. */
    suspend fun depositAddress(address: String, originAsset: SwapAsset): SwapAddress =
        if (originAsset.isZCashAsset) zcashSwapAddress(address) else DynamicSwapAddress(address)

    /** The address funds are ultimately sent to — the opposite side of [depositAddress]. */
    suspend fun destinationAddress(address: String, originAsset: SwapAsset): SwapAddress =
        if (originAsset.isZCashAsset) DynamicSwapAddress(address) else zcashSwapAddress(address)

    /** The address funds are refunded to on failure — same side as [depositAddress]. */
    suspend fun refundAddress(address: String, originAsset: SwapAsset): SwapAddress =
        if (originAsset.isZCashAsset) zcashSwapAddress(address) else DynamicSwapAddress(address)

    private suspend fun zcashSwapAddress(address: String): ZcashSwapAddress =
        when (synchronizerProvider.getSynchronizer().validateAddress(address)) {
            AddressType.Unified,
            AddressType.Shielded -> ZcashShieldedSwapAddress(address)

            AddressType.Tex,
            AddressType.Transparent -> ZcashTransparentSwapAddress(address)

            is AddressType.Invalid -> throw IllegalArgumentException("Zcash address is invalid")
        }
}
