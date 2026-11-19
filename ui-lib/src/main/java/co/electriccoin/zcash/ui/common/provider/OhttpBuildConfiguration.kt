package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.Ohttp
import co.electriccoin.lightwallet.client.model.OhttpConfiguration
import co.electriccoin.lightwallet.client.model.OhttpNetworkConfiguration
import co.electriccoin.zcash.spackle.Twig

/**
 * The OHTTP configuration of this build, which the app passes to the SDK once at startup.
 *
 * The SDK carries no OHTTP endpoint or key. The app takes them from the `app-config` dependency
 * (`AppConfig.Ohttp`), which that repository's CI generates from secrets. An empty network (all four values empty)
 * has OHTTP off: it offers only the Public connection, and CMC, NEAR, Flexa and the OHTTP voting route, which need
 * the gateway, are unavailable or go the Public way. The values are never logged.
 */
object OhttpBuildConfiguration {
    /** The four values of one network as the build has them; all empty = OHTTP off for it. */
    data class NetworkValues(
        val gatewayHost: String,
        val relayUrl: String,
        val keysDnsName: String,
        val signingPublicKey: String,
    ) {
        override fun toString() = "NetworkValues(<redacted>)"

        internal fun toConfiguration(): OhttpNetworkConfiguration? =
            if (listOf(gatewayHost, relayUrl, keysDnsName, signingPublicKey).all { it.isBlank() }) {
                null
            } else {
                OhttpNetworkConfiguration(gatewayHost, relayUrl, keysDnsName, signingPublicKey)
            }
    }

    /** The SDK configuration of [mainnet] and [testnet], or null when the build has neither. */
    fun of(
        mainnet: NetworkValues,
        testnet: NetworkValues,
    ): OhttpConfiguration? {
        val main = mainnet.toConfiguration()
        val test = testnet.toConfiguration()
        return if (main == null && test == null) null else OhttpConfiguration(mainnet = main, testnet = test)
    }

    /**
     * Installs the configuration of [mainnet] and [testnet] in the SDK ([Ohttp.configure]).
     *
     * When the native library is already loaded, [configure] installs natively right away and throws
     * [IllegalArgumentException] on a refused value. Otherwise it installs in Kotlin and natively when the library
     * loads; a refusal at load turns OHTTP off and is logged inside the SDK. The SDK also throws
     * [IllegalStateException] when it read the OHTTP state before the first configure, or when a different
     * configuration is already installed.
     *
     * A refused configuration is logged by field and rule only (the SDK's message carries no value), and OHTTP
     * stays off: the app then behaves as a build without OHTTP instead of failing to start. An [IllegalStateException]
     * is logged and OHTTP stays as it is, so [android.app.Application.onCreate] completes instead of silently
     * crashing the process into a half state.
     *
     * @param configure the SDK call, replaced in tests
     */
    fun install(
        mainnet: NetworkValues,
        testnet: NetworkValues,
        configure: (OhttpConfiguration?) -> Unit = Ohttp::configure,
    ) {
        val installed =
            try {
                of(mainnet, testnet).also(configure)
            } catch (e: IllegalArgumentException) {
                Twig.error { "OHTTP configuration of this build refused, OHTTP stays off: ${e.message}" }
                configure(null)
                null
            } catch (e: IllegalStateException) {
                Twig.error(e) { "OHTTP configuration conflict or read before configure, OHTTP stays as it is" }
                null
            }
        Twig.info { "OHTTP: mainnet ${onOff(installed?.mainnet)}, testnet ${onOff(installed?.testnet)}" }
    }

    private fun onOff(network: OhttpNetworkConfiguration?) = if (network == null) "off" else "configured"
}
