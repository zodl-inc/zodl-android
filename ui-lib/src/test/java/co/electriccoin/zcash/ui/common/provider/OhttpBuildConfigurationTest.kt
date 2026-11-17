package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.model.OhttpConfiguration
import co.electriccoin.zcash.ui.common.provider.OhttpBuildConfiguration.NetworkValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OhttpBuildConfigurationTest {
    private val mainnet =
        NetworkValues(
            gatewayHost = "ohttp-gateway.example",
            relayUrl = "https://relay-mainnet.example/relay",
            keysDnsName = "mainnet.ohttp-keys.example",
            signingPublicKey = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
        )
    private val testnet =
        NetworkValues(
            gatewayHost = "ohttp-gateway-testnet.example",
            relayUrl = "https://relay-testnet.example/relay",
            keysDnsName = "testnet.ohttp-keys.example",
            signingPublicKey = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
        )
    private val none = NetworkValues("", "", "", "")

    @Test
    fun aBuildWithoutValuesHasOhttpOff() {
        assertNull(OhttpBuildConfiguration.of(mainnet = none, testnet = none))
    }

    @Test
    fun eachNetworkWithValuesIsConfiguredAndTheOtherOff() {
        val onlyMainnet = assertNotNull(OhttpBuildConfiguration.of(mainnet = mainnet, testnet = none))
        assertEquals(mainnet.gatewayHost, onlyMainnet.mainnet?.gatewayHost)
        assertNull(onlyMainnet.testnet)
        val both = assertNotNull(OhttpBuildConfiguration.of(mainnet = mainnet, testnet = testnet))
        assertEquals(testnet.relayUrl, both.testnet?.relayUrl)
    }

    @Test
    fun theValuesAreNeverShown() {
        val shown = mainnet.toString() + OhttpBuildConfiguration.of(mainnet, testnet).toString()
        listOf(mainnet, testnet).forEach { values ->
            listOf(values.gatewayHost, values.relayUrl, values.keysDnsName, values.signingPublicKey).forEach {
                assertFalse(shown.contains(it), shown)
            }
        }
    }

    @Test
    fun theConfigurationIsPassedToTheSdk() {
        val installed = mutableListOf<OhttpConfiguration?>()
        OhttpBuildConfiguration.install(mainnet, testnet) { installed += it }
        assertEquals(listOf(OhttpBuildConfiguration.of(mainnet, testnet)), installed)
    }

    @Test
    fun aRefusedConfigurationLeavesOhttpOffInsteadOfFailingTheStart() {
        val installed = mutableListOf<OhttpConfiguration?>()
        OhttpBuildConfiguration.install(mainnet, testnet) {
            installed += it
            require(it == null) { "mainnet: OHTTP configuration: relay URL: invalid" }
        }
        assertEquals(2, installed.size)
        assertNull(installed.last())
    }

    @Test
    fun aNonValidationErrorLeavesOhttpOffWithoutCrashing() {
        val installed = mutableListOf<OhttpConfiguration?>()
        OhttpBuildConfiguration.install(mainnet, testnet) {
            installed += it
            if (it != null) error("OHTTP configuration already read")
        }
        assertEquals(1, installed.size)
        assertNotNull(installed.first())
    }

    @Test
    fun onlyTheListedRelaysCountAsIndependent() {
        val listed = "https://relay-independent.example/relay"
        val digests = setOf(LightWalletEndpointProvider.sha256Hex(listed))

        assertTrue(LightWalletEndpointProvider.isIndependentRelay(listed, digests))
        // A different value, even a trailing slash or another letter case, is reported as not private.
        assertFalse(LightWalletEndpointProvider.isIndependentRelay("$listed/", digests))
        assertFalse(LightWalletEndpointProvider.isIndependentRelay(listed.replaceFirst("https", "HTTPS"), digests))
        assertFalse(LightWalletEndpointProvider.isIndependentRelay("https://relay-simulator.example/relay", digests))
        // The example relays of the tests are not among the production ones.
        assertFalse(LightWalletEndpointProvider.isIndependentRelay(mainnet.relayUrl))
    }
}
