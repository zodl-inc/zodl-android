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
    private val isMainnet: Boolean
        get() = ZcashNetwork.fromResources(application) == ZcashNetwork.Mainnet

    /**
     * Every built-in endpoint, default first: the OHTTP gateway (tier 0, the default and the
     * preferred transport) followed by the clearnet lightwalletd fallback tier. This is what the
     * server picker lists and what the fastest-servers benchmark measures. Automatic mode never
     * chooses among these by speed alone — see [co.electriccoin.zcash.ui.common.model.ServerFallbackPolicy]:
     * OHTTP is used whenever it is reachable, and a clearnet server only while it is not.
     */
    fun getEndpoints(): List<LightWalletEndpoint> = listOf(getDefaultEndpoint()) + getFallbackEndpoints()

    /**
     * The OHTTP (RFC 9458) gateway. The SDK routes gRPC HPKE-encrypted through a relay to it:
     * the relay sees the device IP but not the content, the gateway/lightwalletd see the content
     * but not the IP.
     */
    fun getDefaultEndpoint(): LightWalletEndpoint =
        if (isMainnet) {
            LightWalletEndpoint(host = "ohttp-gateway.example", port = 443, isSecure = true)
        } else {
            // Served via relay-simulator-testnet.example.
            LightWalletEndpoint(host = "ohttp-gateway-testnet.example", port = 443, isSecure = true)
        }

    /**
     * Clearnet lightwalletd servers (the pre-OHTTP built-in list). Used ONLY as the automatic
     * fallback while the OHTTP gateway cannot be reached, or when the user picks one explicitly in
     * Manual mode. They see the device IP unless Tor is on; with Tor on the sync engine routes every
     * call to them — block download included — through Tor.
     */
    fun getFallbackEndpoints(): List<LightWalletEndpoint> =
        if (isMainnet) {
            listOf(
                LightWalletEndpoint(host = "zec.rocks", port = 443, isSecure = true),
                LightWalletEndpoint(host = "na.zec.rocks", port = 443, isSecure = true),
                LightWalletEndpoint(host = "sa.zec.rocks", port = 443, isSecure = true),
                LightWalletEndpoint(host = "eu.zec.rocks", port = 443, isSecure = true),
                LightWalletEndpoint(host = "ap.zec.rocks", port = 443, isSecure = true),
                LightWalletEndpoint(host = "us.zec.stardust.rest", port = 443, isSecure = true),
                LightWalletEndpoint(host = "eu.zec.stardust.rest", port = 443, isSecure = true),
            )
        } else {
            listOf(
                LightWalletEndpoint(host = "testnet.zec.rocks", port = 443, isSecure = true),
            )
        }

    /** True for a Zodl OHTTP gateway (`ohttp-gateway*.example`), i.e. an endpoint the SDK reaches over OHTTP. */
    fun isOhttpGateway(endpoint: LightWalletEndpoint): Boolean = isOhttpGatewayHost(endpoint.host)

    fun getDecommissionedHosts(): Set<String> =
        setOf(
            "jp.zec.stardust.rest",
            "eu2.zec.stardust.rest",
        )

    companion object {
        fun isOhttpGatewayHost(host: String): Boolean {
            val normalized = host.lowercase()
            return normalized.startsWith("ohttp-gateway") && normalized.endsWith(".example")
        }
    }
}
