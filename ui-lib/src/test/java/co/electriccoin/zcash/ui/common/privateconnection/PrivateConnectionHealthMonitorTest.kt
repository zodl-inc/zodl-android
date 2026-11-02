package co.electriccoin.zcash.ui.common.privateconnection

import co.electriccoin.lightwallet.client.health.OhttpHealthObservation
import co.electriccoin.lightwallet.client.health.OhttpHealthOperation
import co.electriccoin.lightwallet.client.health.OhttpHealthOperationCompleted
import co.electriccoin.lightwallet.client.health.OhttpHealthOutcome
import co.electriccoin.lightwallet.client.health.OhttpHealthPurpose
import co.electriccoin.lightwallet.client.health.OhttpHealthRoute
import co.electriccoin.lightwallet.client.health.OhttpHealthScope
import co.electriccoin.lightwallet.client.health.OhttpHealthSnapshot
import co.electriccoin.lightwallet.client.health.OhttpHealthSource
import co.electriccoin.lightwallet.client.health.OhttpHealthStage
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Path
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.ProbeResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The executor around the policy, on virtual time: timers, probes, gate and ownership. */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivateConnectionHealthMonitorTest {
    private val route = OhttpHealthRoute("gateway.test", "https://relay.test", viaTor = false)
    private val evidence = MutableSharedFlow<OhttpHealthSnapshot>(extraBufferCapacity = 16)
    private val completions = MutableSharedFlow<OhttpHealthOperationCompleted>(extraBufferCapacity = 16)
    private val gate = MutableStateFlow(true)
    private var probeResult: (ProbeResult.() -> ProbeResult)? = null
    private var probes = 0
    private var probeOutcome: OhttpHealthOutcome = OhttpHealthOutcome.TransportUnavailable

    private fun TestScope.now() = testScheduler.currentTime * 1_000_000

    private fun TestScope.monitor() =
        PrivateConnectionHealthMonitor(
            scope = backgroundScope,
            observeEvidence = { evidence },
            completions = completions,
            probe = { r, generation ->
                probes += 1
                val start = now()
                ProbeResult(probeOutcome, r, generation, start, start + 1)
            },
            pathUpdates = flowOf(Path.AVAILABLE),
            torReady = flowOf(true),
            presentationAllowed = gate,
            clock = { now() },
        )

    private var version = 0L

    private fun TestScope.failure(
        outcome: OhttpHealthOutcome = OhttpHealthOutcome.TransportUnavailable,
        operation: OhttpHealthOperation? = null,
        scope: OhttpHealthScope = OhttpHealthScope.LIGHTWALLETD,
        usableRoute: Boolean = false,
    ): OhttpHealthSnapshot {
        version += 1
        val t = now()
        val observation =
            OhttpHealthObservation(
                source = OhttpHealthSource.NATIVE,
                route = route,
                scope = if (usableRoute) OhttpHealthScope.ROUTE else scope,
                outcome = outcome,
                stage = OhttpHealthStage.COMPLETE,
                version = version,
                requestId = version,
                startedAtNanos = t,
                observedAtNanos = t + 1,
                operation = operation,
                purpose = OhttpHealthPurpose.BACKGROUND,
            )
        return OhttpHealthSnapshot(version, listOf(observation))
    }

    private fun TestScope.success() = failure(OhttpHealthOutcome.Usable, usableRoute = true)

    @Test
    fun `a failure is confirmed by one probe after the grace and shown`() =
        runTest {
            val monitor = monitor().also { it.start(route) }
            runCurrent()
            advanceTimeBy(1_000)
            evidence.emit(failure())
            runCurrent()
            assertEquals(0, probes)
            advanceTimeBy(1_999)
            assertEquals(0, probes)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(1, probes)
            assertNotNull(monitor.outage.value)
            assertEquals(1L, monitor.outage.value?.episode?.id)
        }

    @Test
    fun `a fresh success inside the grace means no probe and no outage`() =
        runTest {
            val monitor = monitor().also { it.start(route) }
            runCurrent()
            advanceTimeBy(1_000)
            evidence.emit(failure())
            runCurrent()
            advanceTimeBy(500)
            evidence.emit(success())
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(0, probes)
            assertNull(monitor.outage.value)
        }

    @Test
    fun `a usable probe never shows an outage`() =
        runTest {
            probeOutcome = OhttpHealthOutcome.Usable
            val monitor = monitor().also { it.start(route) }
            runCurrent()
            advanceTimeBy(1_000)
            evidence.emit(failure())
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(1, probes)
            assertNull(monitor.outage.value)
        }

    @Test
    fun `the outage waits for the presentation gate`() =
        runTest {
            gate.value = false
            val monitor = monitor().also { it.start(route) }
            runCurrent()
            advanceTimeBy(1_000)
            evidence.emit(failure())
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(1, probes)
            assertNull(monitor.outage.value)
            gate.value = true
            runCurrent()
            assertNotNull(monitor.outage.value)
        }

    @Test
    fun `recovery clears a shown outage`() =
        runTest {
            val monitor = monitor().also { it.start(route) }
            runCurrent()
            advanceTimeBy(1_000)
            evidence.emit(failure())
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()
            assertNotNull(monitor.outage.value)
            advanceTimeBy(1_000)
            evidence.emit(success())
            runCurrent()
            assertNull(monitor.outage.value)
        }

    @Test
    fun `acknowledging closes the outage and only a later failed operation shows it again`() =
        runTest {
            val monitor = monitor().also { it.start(route) }
            runCurrent()
            advanceTimeBy(1_000)
            evidence.emit(failure())
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()
            assertNotNull(monitor.outage.value)

            monitor.acknowledge()
            runCurrent()
            assertNull(monitor.outage.value)

            // A background failure after the acknowledgement stays quiet.
            advanceTimeBy(1_000)
            evidence.emit(failure())
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertNull(monitor.outage.value)

            // A user operation that started afterwards and failed reopens it, naming the operation.
            val operation = OhttpHealthOperation("op-1", startedAtNanos = now())
            advanceTimeBy(1_000)
            val failed = failure(operation = operation)
            completions.emit(OhttpHealthOperationCompleted(operation, true, failed.observations))
            runCurrent()
            assertEquals(operation, monitor.outage.value?.operation)
        }

    @Test
    fun `stopping fences the timers and clears the outage`() =
        runTest {
            val monitor = monitor().also { it.start(route) }
            runCurrent()
            advanceTimeBy(1_000)
            evidence.emit(failure())
            runCurrent()
            monitor.stop()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(0, probes)
            assertNull(monitor.outage.value)
            assertTrue(true)
        }
}
