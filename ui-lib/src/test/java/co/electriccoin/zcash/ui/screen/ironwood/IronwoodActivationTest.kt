package co.electriccoin.zcash.ui.screen.ironwood

import cash.z.ecc.android.sdk.model.ZcashNetwork
import org.junit.Assert.assertEquals
import org.junit.Test

class IronwoodActivationTest {
    @Test
    fun mainnet_height_is_nu63_activation() {
        assertEquals(3_428_143L, IronwoodActivation.heightFor(ZcashNetwork.Mainnet).value)
    }

    @Test
    fun testnet_height_is_ironwood_activation() {
        assertEquals(4_134_000L, IronwoodActivation.heightFor(ZcashNetwork.Testnet).value)
    }
}
