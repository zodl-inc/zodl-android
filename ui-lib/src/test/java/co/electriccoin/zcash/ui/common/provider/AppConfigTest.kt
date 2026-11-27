package co.electriccoin.zcash.ui.common.provider

import com.zodl.appconfig.AppConfig
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Checks the values of the `app-config` dependency on every build. It replaces the former release-only
 * CI script that checked the OHTTP secrets: the values now ship in a published artifact, so the check runs where
 * the artifact is consumed. A failure names the field and the rule, never the value.
 */
class AppConfigTest {
    private class Network(
        val name: String,
        val gatewayHost: String,
        val relayUrl: String,
        val keysDnsName: String,
        val signingPublicKey: String,
    )

    private val mainnet =
        Network(
            name = "mainnet",
            gatewayHost = AppConfig.Ohttp.GATEWAY_HOST_MAINNET,
            relayUrl = AppConfig.Ohttp.RELAY_URL_MAINNET,
            keysDnsName = AppConfig.Ohttp.KEYS_DNS_NAME_MAINNET,
            signingPublicKey = AppConfig.Ohttp.SIGNING_PUBKEY_MAINNET,
        )

    private val testnet =
        Network(
            name = "testnet",
            gatewayHost = AppConfig.Ohttp.GATEWAY_HOST_TESTNET,
            relayUrl = AppConfig.Ohttp.RELAY_URL_TESTNET,
            keysDnsName = AppConfig.Ohttp.KEYS_DNS_NAME_TESTNET,
            signingPublicKey = AppConfig.Ohttp.SIGNING_PUBKEY_TESTNET,
        )

    private fun Network.values() = listOf(gatewayHost, relayUrl, keysDnsName, signingPublicKey)

    @Test
    fun all_eight_values_are_set() {
        listOf(mainnet, testnet).forEach { n ->
            n.values().forEachIndexed { index, value ->
                assertTrue(value.isNotEmpty(), "${n.name} value #$index is empty")
            }
        }
    }

    @Test
    fun values_have_the_expected_format() {
        listOf(mainnet, testnet).forEach { n ->
            assertTrue(DNS_NAME.matches(n.gatewayHost), "${n.name} gateway host is not a lowercase DNS name")
            assertTrue(DNS_NAME.matches(n.keysDnsName), "${n.name} keys DNS name is not a lowercase DNS name")
            assertTrue(HTTPS_URL.matches(n.relayUrl), "${n.name} relay URL is not an https URL with a host")
            assertTrue(HEX_64.matches(n.signingPublicKey), "${n.name} signing key is not 64 lowercase hex characters")
        }
    }

    @Test
    fun mainnet_and_testnet_differ() {
        assertNotEquals(mainnet.gatewayHost, testnet.gatewayHost, "gateway host is shared")
        assertNotEquals(mainnet.relayUrl, testnet.relayUrl, "relay URL is shared")
        assertNotEquals(mainnet.keysDnsName, testnet.keysDnsName, "keys DNS name is shared")
        assertNotEquals(mainnet.signingPublicKey, testnet.signingPublicKey, "signing key is shared")
    }

    @Test
    fun relays_are_the_independent_ones() {
        listOf(mainnet, testnet).forEach { n ->
            assertTrue(
                LightWalletEndpointProvider.isIndependentRelay(n.relayUrl),
                "${n.name} relay digest is not in INDEPENDENT_RELAY_URL_SHA256: the release would not be marked private"
            )
        }
    }

    private companion object {
        val DNS_NAME = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
        val HTTPS_URL = Regex("^https://[^/\\s]+(/\\S*)?$")
        val HEX_64 = Regex("^[0-9a-f]{64}$")
    }
}
