package co.electriccoin.zcash.ui.common.zns

import cash.z.ecc.android.sdk.model.ZcashNetwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ZnsRegistryTest {
    @Test
    fun mainnetTriesTheZnsHostFirstThenTheFallback() {
        val registry = ZnsRegistry.of(ZcashNetwork.Mainnet)

        assertEquals(listOf("https://main.zcashnames.com", "https://light.zcash.me/zns-mainnet"), registry.endpoints)
        assertTrue(registry.viewingKey.startsWith("uview1"))
    }

    @Test
    fun testnetUsesItsOwnHostAndRegistry() {
        val registry = ZnsRegistry.of(ZcashNetwork.Testnet)

        assertEquals(listOf("https://light.zcash.me/zns-testnet"), registry.endpoints)
        assertTrue(registry.viewingKey.startsWith("uviewtest1"))
        assertNotEquals(ZnsRegistry.MAINNET.viewingKey, registry.viewingKey)
    }

    @Test
    fun everyHostIsHttps() {
        val hosts = ZnsRegistry.MAINNET.endpoints + ZnsRegistry.TESTNET.endpoints
        hosts.forEach { assertTrue(it.startsWith("https://"), it) }
    }
}
