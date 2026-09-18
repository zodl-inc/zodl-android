package co.electriccoin.zcash.ui.common.model.near

import kotlin.test.Test
import kotlin.test.assertEquals

class NearAssetIdTest {
    @Test
    fun mapsRoutedBitcoinIdBackToTheRequestedId() {
        assertEquals("nep141:btc.omft.near", requestedAssetId("1cs_v1:btc:native:coin"))
    }

    @Test
    fun leavesRequestedIdsUnchanged() {
        assertEquals("nep141:btc.omft.near", requestedAssetId("nep141:btc.omft.near"))
    }

    @Test
    fun leavesUnmappedRoutedIdsUnchanged() {
        val baseUsdc = "1cs_v1:base:erc20:0x833589fcd6edb6e08f4c7c32d4f71b54bda02913"
        assertEquals(baseUsdc, requestedAssetId(baseUsdc))
    }
}
