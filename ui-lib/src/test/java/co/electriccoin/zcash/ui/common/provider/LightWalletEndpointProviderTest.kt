package co.electriccoin.zcash.ui.common.provider

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LightWalletEndpointProviderTest {
    @Test
    fun onlyTheExactGatewayHostsArePrivate() {
        assertTrue(LightWalletEndpointProvider.isOhttpGatewayHost("ohttp-gateway.example"))
        assertTrue(LightWalletEndpointProvider.isOhttpGatewayHost("ohttp-gateway-testnet.example"))

        assertFalse(LightWalletEndpointProvider.isOhttpGatewayHost("ohttp-gateway-eu.example"))
        assertFalse(LightWalletEndpointProvider.isOhttpGatewayHost("ohttp-gateway.evil.example"))
        assertFalse(LightWalletEndpointProvider.isOhttpGatewayHost("ohttp-gateway.example.example.org"))
        assertFalse(LightWalletEndpointProvider.isOhttpGatewayHost("zec.rocks"))
    }
}
