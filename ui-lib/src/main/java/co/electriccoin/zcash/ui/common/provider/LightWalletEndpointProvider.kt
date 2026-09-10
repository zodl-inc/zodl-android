package co.electriccoin.zcash.ui.common.provider

import android.app.Application
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.sdk.type.fromResources
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint

// TODO [#1273]: Add ChooseServer Tests #1273
// TODO [#1273]: https://github.com/Electric-Coin-Company/zashi-android/issues/1273
class LightWalletEndpointProvider(
    private val application: Application
) {
    // OHTTP build: ONLY the OHTTP gateways are offered. Any clearnet lightwalletd in this list
    // would be picked by the SDK's fastest-server selection / the sync engine's endpoint probe
    // and receive the bulk sync in plaintext, defeating the privacy guarantee. Users who add a
    // custom server explicitly opt out of OHTTP for that server.
    fun getEndpoints(): List<LightWalletEndpoint> =
        if (ZcashNetwork.fromResources(application) == ZcashNetwork.Mainnet) {
            listOf(
                // OHTTP (RFC 9458): the SDK routes gRPC HPKE-encrypted through a relay to this
                // gateway. Relay sees IP but not content; gateway/LWD see content but not IP.
                LightWalletEndpoint(host = "ohttp-gateway.zodl.com", port = 443, isSecure = true),
            )
        } else {
            listOf(
                // OHTTP (RFC 9458) testnet gateway — served via relay-simulator-testnet.zodl.com
                LightWalletEndpoint(host = "ohttp-gateway-testnet.zodl.com", port = 443, isSecure = true),
            )
        }

    fun getDefaultEndpoint() = getEndpoints().first()

    fun getDecommissionedHosts(): Set<String> =
        setOf(
            "jp.zec.stardust.rest",
            "eu2.zec.stardust.rest",
        )
}
