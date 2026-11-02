@file:Suppress("MaxLineLength")

package co.electriccoin.zcash.ui.common.privateconnection

import cash.z.ecc.android.sdk.exception.CompactBlockProcessorException
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.health.OhttpHealthOutcome
import co.electriccoin.lightwallet.client.health.OhttpHealthRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PrivateConnectionProbeTest {
    private val classify = PrivateConnectionProbe.Companion::classify

    private fun invalid(reason: Throwable = IllegalStateException("down")) = ServerValidation.InValid(reason)

    @Test
    fun `a valid server is usable`() {
        assertEquals(OhttpHealthOutcome.Usable, classify(ServerValidation.Valid, emptyList()))
    }

    @Test
    fun `a probe that ran out of budget did not deliver`() {
        assertEquals(OhttpHealthOutcome.TransportUnavailable, classify(null, emptyList()))
    }

    @Test
    fun `a failed validation with no better evidence means the route did not deliver`() {
        assertEquals(OhttpHealthOutcome.TransportUnavailable, classify(invalid(), emptyList()))
    }

    @Test
    fun `a rate limit seen during the probe wins and is not an outage`() {
        val limited = OhttpHealthOutcome.RateLimited(9_000)
        assertEquals(
            limited,
            classify(invalid(), listOf(OhttpHealthOutcome.TransportUnavailable, OhttpHealthOutcome.RateLimited(5_000), limited)),
        )
        assertEquals(limited, classify(null, listOf(limited)))
    }

    @Test
    fun `a network or branch mismatch is a configuration problem, not an outage`() {
        val mismatch = CompactBlockProcessorException.MismatchedNetwork(clientNetwork = "main", serverNetwork = "test")
        assertEquals(OhttpHealthOutcome.ConfigurationFailure, classify(invalid(mismatch), emptyList()))
    }

    @Test
    fun `diagnostic evidence of a rejection is reported as it is`() {
        assertEquals(
            OhttpHealthOutcome.PolicyRejected,
            classify(invalid(), listOf(OhttpHealthOutcome.PolicyRejected)),
        )
    }

    @Test
    fun `recorded transport trouble outranks a mismatch it might have caused`() {
        val mismatch = CompactBlockProcessorException.MismatchedNetwork(clientNetwork = "main", serverNetwork = "test")
        assertEquals(
            OhttpHealthOutcome.TransportUnavailable,
            classify(invalid(mismatch), listOf(OhttpHealthOutcome.TransportUnavailable)),
        )
    }

    @Test
    fun `Tor gets the longer budget`() {
        val clearnet = OhttpHealthRoute("g", "r", viaTor = false)
        assertEquals(5.seconds, PrivateConnectionProbe.budget(clearnet))
        assertEquals(20.seconds, PrivateConnectionProbe.budget(clearnet.copy(viaTor = true)))
    }
}
