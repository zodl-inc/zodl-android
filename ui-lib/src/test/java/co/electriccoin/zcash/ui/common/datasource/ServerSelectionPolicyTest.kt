package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Shared server-selection policy (MOB-1145). A migrated user (null preference) pinned to a custom
 * endpoint must be treated as manual and must not be silently broadcast to every bundled server.
 * This is the single source of truth used by both AutomaticServerRepository and the submission path.
 */
class ServerSelectionPolicyTest {
    private val bundled =
        listOf(
            endpoint("zec.rocks"),
            endpoint("na.zec.rocks")
        )

    @Test
    fun explicitAutomaticPreferenceIsAutomatic() {
        assertEquals(
            true,
            resolveIsServerSelectionAutomatic(
                isAutomaticPreference = true,
                currentEndpoint = endpoint("custom.example.com"),
                knownEndpoints = bundled
            )
        )
    }

    @Test
    fun explicitManualPreferenceIsManual() {
        assertEquals(
            false,
            resolveIsServerSelectionAutomatic(
                isAutomaticPreference = false,
                currentEndpoint = bundled.first(),
                knownEndpoints = bundled
            )
        )
    }

    @Test
    fun nullPreferenceWithCustomEndpointIsManual() {
        // The regression guard: a migrated wallet pinned to a custom server must NOT broadcast to all
        // bundled servers just because the automatic flag was never written.
        assertEquals(
            false,
            resolveIsServerSelectionAutomatic(
                isAutomaticPreference = null,
                currentEndpoint = endpoint("custom.example.com"),
                knownEndpoints = bundled
            )
        )
    }

    @Test
    fun nullPreferenceWithBundledEndpointIsAutomatic() {
        assertEquals(
            true,
            resolveIsServerSelectionAutomatic(
                isAutomaticPreference = null,
                currentEndpoint = bundled.last(),
                knownEndpoints = bundled
            )
        )
    }

    @Test
    fun nullPreferenceWithoutWalletIsAutomatic() {
        assertEquals(
            true,
            resolveIsServerSelectionAutomatic(
                isAutomaticPreference = null,
                currentEndpoint = null,
                knownEndpoints = bundled
            )
        )
    }

    @Test
    fun bundledEndpointIsNotCustom() {
        assertEquals(false, resolveIsEndpointCustom(endpoint = bundled.first(), knownEndpoints = bundled))
    }

    @Test
    fun nonBundledEndpointIsCustom() {
        assertEquals(
            true,
            resolveIsEndpointCustom(endpoint = endpoint("custom.example.com"), knownEndpoints = bundled)
        )
    }

    private fun endpoint(host: String) =
        LightWalletEndpoint(
            host = host,
            port = 443,
            isSecure = true
        )
}
