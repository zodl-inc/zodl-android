package co.electriccoin.zcash.ui.screen.transactiondetail

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.design.util.imageRes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The swap quote icon helpers resolve a ZEC asset to the dedicated ZEC artwork — the round token icon
 * and the shielded/transparent chain icon — and fall back to the asset's own token/chain icons for any
 * other asset. ZEC detection is via the `isZCashAsset` extension.
 */
class SwapAssetIconTest {
    private val zec = SwapAssetTestFixture.zecAsset()
    private val generic = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")

    @Test
    fun `zec asset uses the dedicated round ZEC token icon`() {
        assertEquals(imageRes(R.drawable.ic_zec_round_full), zec.swapQuoteTokenIcon())
    }

    @Test
    fun `non-zec asset uses its own token icon`() {
        assertEquals(generic.tokenIcon, generic.swapQuoteTokenIcon())
    }

    @Test
    fun `zec asset uses the shielded chain icon when shielded`() {
        assertEquals(
            imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded),
            zec.swapQuoteChainIcon(isShielded = true)
        )
    }

    @Test
    fun `zec asset uses the transparent chain icon when not shielded`() {
        assertEquals(
            imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
            zec.swapQuoteChainIcon(isShielded = false)
        )
    }

    @Test
    fun `non-zec asset uses its own chain icon regardless of shielding`() {
        assertEquals(generic.chainIcon, generic.swapQuoteChainIcon(isShielded = true))
        assertEquals(generic.chainIcon, generic.swapQuoteChainIcon(isShielded = false))
    }
}
