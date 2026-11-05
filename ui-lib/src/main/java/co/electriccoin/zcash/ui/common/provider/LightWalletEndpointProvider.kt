package co.electriccoin.zcash.ui.common.provider

import android.app.Application
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.util.OhttpCodecFactory
import cash.z.ecc.sdk.type.fromResources
import co.electriccoin.lightwallet.client.LightWalletClient
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.lightwallet.client.usesIndependentRelay
import co.electriccoin.zcash.spackle.Twig
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

// TODO [#1273]: Add ChooseServer Tests #1273
// TODO [#1273]: https://github.com/Electric-Coin-Company/zashi-android/issues/1273
class LightWalletEndpointProvider(
    private val application: Application
) {
    private val isMainnet: Boolean
        get() = ZcashNetwork.fromResources(application) == ZcashNetwork.Mainnet

    /**
     * Every built-in endpoint, default first: the OHTTP gateway (tier 0, the default and the
     * preferred transport), when the SDK was built with one, followed by the clearnet lightwalletd
     * tier. This is what the server picker lists and what the fastest-servers benchmark measures.
     * Automatic mode never chooses among these by speed — see
     * [co.electriccoin.zcash.ui.common.model.ServerFallbackPolicy]: it stays on OHTTP and, when the
     * gateway is unreachable, reports that to the user instead of switching to clearnet on its own.
     */
    fun getEndpoints(): List<LightWalletEndpoint> = listOfNotNull(getOhttpEndpoint()) + getFallbackEndpoints()

    /**
     * The OHTTP (RFC 9458) gateway of this network, or null when the SDK was built without OHTTP for it. The
     * SDK routes gRPC HPKE-encrypted through a relay to it: the relay sees the device IP but not the content,
     * the gateway/lightwalletd see the content but not the IP. Its host is the one compiled into the SDK
     * ([LightWalletClient.ohttpGatewayHost]); the app has none of its own.
     */
    fun getOhttpEndpoint(): LightWalletEndpoint? =
        LightWalletClient.ohttpGatewayHost(isTestnet = !isMainnet)?.let {
            LightWalletEndpoint(host = it, port = GATEWAY_PORT, isSecure = true)
        }

    /** Whether the SDK was built with an OHTTP gateway for this network. */
    fun hasOhttpGateway(): Boolean = getOhttpEndpoint() != null

    /**
     * The OHTTP gateway ([getOhttpEndpoint]). Without one, the first clearnet server: the wallet then uses the
     * normal clearnet server selection instead of an endpoint the SDK cannot route.
     */
    fun getDefaultEndpoint(): LightWalletEndpoint = getOhttpEndpoint() ?: getFallbackEndpoints().first()

    /**
     * Clearnet lightwalletd servers (the pre-OHTTP built-in list). Used ONLY when the user picks
     * one explicitly in Manual mode; Automatic mode never falls back to them on its own. They see
     * the device IP unless Tor is on; with Tor on the sync engine routes every call to them — block
     * download included — through Tor.
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

    /** True for a Zodl OHTTP gateway, i.e. an endpoint the SDK reaches over OHTTP. */
    fun isOhttpGateway(endpoint: LightWalletEndpoint): Boolean = isOhttpGatewayHost(endpoint.host)

    /**
     * The host of the relay this build reaches [gatewayHost] through, or null when it has none (OHTTP is then
     * off for that gateway) or it cannot be determined. It comes from the same codec as [usesIndependentRelay].
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun relayHostFor(gatewayHost: String): String? =
        try {
            OhttpCodecFactory.create().relayUrlFor(gatewayHost)?.let { URI(it).host }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Twig.warn(e) { "Could not tell which OHTTP relay is in use" }
            null
        }

    /**
     * False when this build reaches [gatewayHost] through a relay its operator also runs (a build-time relay
     * override such as the relay simulator), or when that cannot be determined.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun usesIndependentRelay(gatewayHost: String): Boolean =
        try {
            OhttpCodecFactory.create().usesIndependentRelay(gatewayHost)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Twig.warn(e) { "Could not tell whether the OHTTP relay is independent" }
            false
        }

    fun getDecommissionedHosts(): Set<String> =
        setOf(
            "jp.zec.stardust.rest",
            "eu2.zec.stardust.rest",
        )

    companion object {
        /**
         * Exactly the SDK's own decision (exact host match, no prefix or suffix matching), so a custom
         * host that the SDK would dial as plain gRPC is never labelled private.
         */
        fun isOhttpGatewayHost(host: String): Boolean =
            LightWalletClient.isOhttpGateway(LightWalletEndpoint(host = host, port = GATEWAY_PORT, isSecure = true))

        /** Whether [host] is the testnet gateway compiled into the SDK. */
        fun isTestnetGatewayHost(host: String): Boolean = host == LightWalletClient.ohttpGatewayHost(isTestnet = true)

        private const val GATEWAY_PORT = 443

        /** The gateway hosts compiled into the SDK, mainnet then testnet; empty when it was built without OHTTP. */
        val gatewayHosts: List<String>
            get() = listOfNotNull(LightWalletClient.ohttpGatewayHost(false), LightWalletClient.ohttpGatewayHost(true))
    }
}
