package co.electriccoin.zcash.ui.common.model

import androidx.test.filters.SmallTest
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerSelectionTest {
    @Test
    @SmallTest
    fun customPersistedEndpointMigratesToManualMode() {
        val endpoint = LightWalletEndpoint(host = "custom.example.com", port = 9067, isSecure = true)

        val selection =
            ServerSelection.fromPersistedEndpoint(
                endpoint = endpoint,
                knownEndpoints = knownEndpoints
            )

        assertEquals(ConnectionMode.MANUAL, selection.mode)
        assertEquals(endpoint, selection.endpoint)
        assertTrue(selection.isCustom)
    }

    @Test
    @SmallTest
    fun knownPersistedEndpointMigratesToAutomaticMode() {
        val selection =
            ServerSelection.fromPersistedEndpoint(
                endpoint = knownEndpoints.first(),
                knownEndpoints = knownEndpoints
            )

        assertEquals(ConnectionMode.AUTOMATIC, selection.mode)
        assertNull(selection.endpoint)
        assertFalse(selection.isCustom)
    }

    @Test
    @SmallTest
    fun missingPersistedEndpointMigratesToAutomaticMode() {
        val selection =
            ServerSelection.fromPersistedEndpoint(
                endpoint = null,
                knownEndpoints = knownEndpoints
            )

        assertEquals(ConnectionMode.AUTOMATIC, selection.mode)
        assertNull(selection.endpoint)
        assertFalse(selection.isCustom)
    }

    @Test
    @SmallTest
    fun manualServerJsonRoundTripPreservesCustomClassification() {
        val endpoint = LightWalletEndpoint(host = "custom.example.com", port = 9067, isSecure = true)
        val selection = ServerSelection.manual(endpoint = endpoint, isCustom = true)

        val restored = ServerSelection.from(selection.toJson())

        assertEquals(selection, restored)
        assertTrue(restored.isCustom)
    }

    @Test
    @SmallTest
    fun corruptedPersistedJsonReturnsNull() {
        val selection = ServerSelection.fromPersistedJson("{")

        assertNull(selection)
    }

    companion object {
        private val knownEndpoints =
            listOf(
                LightWalletEndpoint(host = "zec.rocks", port = 443, isSecure = true)
            )
    }
}
