@file:Suppress("MaxLineLength")

package co.electriccoin.zcash.ui.common.privateconnection

import co.electriccoin.lightwallet.client.health.OhttpHealthObservation
import co.electriccoin.lightwallet.client.health.OhttpHealthOperation
import co.electriccoin.lightwallet.client.health.OhttpHealthOutcome
import co.electriccoin.lightwallet.client.health.OhttpHealthPurpose
import co.electriccoin.lightwallet.client.health.OhttpHealthRoute
import co.electriccoin.lightwallet.client.health.OhttpHealthScope
import co.electriccoin.lightwallet.client.health.OhttpHealthSource
import co.electriccoin.lightwallet.client.health.OhttpHealthStage
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Availability
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Command
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Episode
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Event
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.ProbeResult
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.State
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The second batch of scenarios ported from the iOS `PrivateConnectionHealthTests`: the edge cases that
 * review of the iOS stack found one by one (double delivery, foreign evidence, mixed terminal causes,
 * pauses, re-arming). Same nanosecond constants as the iOS tests.
 */
class PrivateConnectionHealthEdgeCasesTest {
    private val generation = 1L
    private val route = OhttpHealthRoute("gateway.test", "https://relay.test", viaTor = false)

    private fun state() = State(route, generation)

    private fun observation(
        outcome: OhttpHealthOutcome = OhttpHealthOutcome.TransportUnavailable,
        start: Long = 0,
        end: Long = 0,
        version: Long = 1,
        requestId: Long? = null,
        source: OhttpHealthSource = OhttpHealthSource.CLIENT,
        scope: OhttpHealthScope = OhttpHealthScope.ROUTE,
        purpose: OhttpHealthPurpose = OhttpHealthPurpose.BACKGROUND,
        operation: OhttpHealthOperation? = null,
        stage: OhttpHealthStage = OhttpHealthStage.COMPLETE,
    ) = OhttpHealthObservation(
        source = source,
        route = route,
        scope = scope,
        outcome = outcome,
        stage = stage,
        version = version,
        requestId = requestId ?: version,
        startedAtNanos = start,
        observedAtNanos = end,
        operation = operation,
        purpose = purpose,
    )

    private fun result(
        outcome: OhttpHealthOutcome,
        start: Long = 2_000_000_001,
        end: Long = 3_000_000_000,
    ) = ProbeResult(outcome, route, generation, start, end)

    private fun operation(startedAt: Long) = OhttpHealthOperation(UUID.randomUUID().toString(), startedAt)

    private fun probeId(commands: List<Command>): Long =
        assertNotNull(commands.filterIsInstance<Command.Probe>().firstOrNull(), "no probe in $commands").id

    private fun beginConfirmation(state: State): Long {
        val commands = state.handle(Event.Observation(observation()), 0)
        val grace = assertNotNull(commands.filterIsInstance<Command.ScheduleGrace>().firstOrNull())
        return probeId(state.handle(Event.GraceExpired(grace.id), 2_000_000_000))
    }

    private fun confirm(state: State): List<Command> {
        val id = beginConfirmation(state)
        return state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 3_000_000_000)
    }

    private fun present(id: Long, confirmedAt: Long, operation: OhttpHealthOperation? = null) =
        Command.Present(Episode(id, confirmedAt), operation)

    @Test
    fun `a diagnostic double delivery never skips the grace or confirms twice`() {
        for (resultFirst in listOf(false, true)) {
            val state = state()
            val id = probeId(state.handle(Event.FallbackTick, 0))
            val notification = observation(end = 1_000_000_000, purpose = OhttpHealthPurpose.DIAGNOSTIC)
            if (!resultFirst) assertTrue(state.handle(Event.Observation(notification), 1_000_000_000).isEmpty())
            assertEquals(
                listOf(Command.ScheduleGrace(2, 3_000_000_000)),
                state.handle(
                    Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable, start = 0, end = 1_000_000_000)),
                    1_000_000_000,
                ),
            )
            if (resultFirst) assertTrue(state.handle(Event.Observation(notification), 1_000_000_001).isEmpty())
            state.handle(Event.GraceExpired(2), 3_000_000_000)
            val head =
                observation(
                    OhttpHealthOutcome.Usable,
                    start = 3_000_000_001,
                    end = 3_500_000_000,
                    version = 2,
                    purpose = OhttpHealthPurpose.DIAGNOSTIC,
                    stage = OhttpHealthStage.RESPONSE_HEAD,
                )
            assertTrue(state.handle(Event.Observation(head), 3_500_000_000).isEmpty())
            val body =
                observation(
                    start = 3_000_000_001,
                    end = 4_000_000_000,
                    version = 3,
                    purpose = OhttpHealthPurpose.DIAGNOSTIC,
                    stage = OhttpHealthStage.RESPONSE_BODY,
                )
            if (!resultFirst) assertTrue(state.handle(Event.Observation(body), 4_000_000_000).isEmpty())
            assertEquals(
                1,
                state
                    .handle(
                        Event.ProbeCompleted(
                            2,
                            result(OhttpHealthOutcome.TransportUnavailable, start = 3_000_000_001, end = 4_000_000_000),
                        ),
                        4_000_000_000,
                    ).size,
            )
            if (resultFirst) assertTrue(state.handle(Event.Observation(body), 4_000_000_001).isEmpty())
            assertTrue(
                state.handle(Event.ProbeCompleted(2, result(OhttpHealthOutcome.TransportUnavailable)), 5_000_000_000).isEmpty(),
            )
        }
    }

    @Test
    fun `a probe result of another generation cannot consume the current investigation`() {
        val state = state()
        val id = beginConfirmation(state)
        val foreign = result(OhttpHealthOutcome.TransportUnavailable).copy(generation = 99)
        assertTrue(state.handle(Event.ProbeCompleted(id, foreign), 3_000_000_000).isEmpty())
        assertEquals(id, state.investigationId)
        assertEquals(
            1,
            state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 3_000_000_000).size,
        )
    }

    @Test
    fun `a probe of a foreign route cannot confirm or heal`() {
        val state = state()
        val id = beginConfirmation(state)
        val foreign = result(OhttpHealthOutcome.Usable).copy(route = route.copy(gatewayHost = "another.test"))
        assertTrue(state.handle(Event.ProbeCompleted(id, foreign), 3_000_000_000).isEmpty())
        assertEquals(id, state.investigationId)
    }

    @Test
    fun `an additional failure during the confirmation does not defer or invalidate it`() {
        val state = state()
        val id = beginConfirmation(state)
        state.handle(Event.Observation(observation(end = 2_500_000_000, version = 2)), 2_500_000_000)
        assertEquals(
            listOf(present(1, 3_000_000_000)),
            state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 3_000_000_000),
        )
        assertEquals(Availability.UNAVAILABLE, state.availability)
        assertEquals(null, state.investigationId)
        assertEquals(null, state.graceDeadline)
    }

    @Test
    fun `a diagnostic pause is applied even when its result has not arrived`() {
        val state = state()
        val id = probeId(state.handle(Event.ClassificationRequested, 0))
        val pause =
            observation(OhttpHealthOutcome.RateLimited(9_000_000_000), end = 1_000_000_000, purpose = OhttpHealthPurpose.DIAGNOSTIC)
        assertEquals(listOf(Command.CancelProbe), state.handle(Event.Observation(pause), 1_000_000_000))
        assertTrue(
            state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 3_000_000_000).isEmpty(),
        )
        assertTrue(state.handle(Event.FallbackTick, 8_000_000_000).isEmpty())
        assertEquals(listOf(Command.Probe(2)), state.handle(Event.FallbackTick, 9_000_000_000))
    }

    @Test
    fun `a new failed operation rearms after an inconclusive probe without replaying it`() {
        val state = state()
        val id = probeId(state.handle(Event.ClassificationRequested, 0))
        state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.Inconclusive)), 3_000_000_000)
        val operation = operation(4_000_000_000)
        val failure = observation(start = 4_000_000_000, end = 5_000_000_000, version = 2, operation = operation)
        assertEquals(
            listOf(Command.ScheduleGrace(2, 7_000_000_000)),
            state.handle(Event.OperationCompleted(operation, true, listOf(failure)), 5_000_000_000),
        )
        assertEquals(operation, state.pendingPresentation)
    }

    @Test
    fun `a success then a new failure rearms after an inconclusive probe`() {
        val state = state()
        val id = probeId(state.handle(Event.ClassificationRequested, 0))
        state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.Inconclusive)), 3_000_000_000)
        state.handle(
            Event.Observation(observation(OhttpHealthOutcome.Usable, start = 4_000_000_000, end = 5_000_000_000, version = 2)),
            5_000_000_000,
        )
        assertEquals(
            listOf(Command.ScheduleGrace(2, 9_000_000_000)),
            state.handle(Event.Observation(observation(start = 6_000_000_000, end = 7_000_000_000, version = 3)), 7_000_000_000),
        )
    }

    @Test
    fun `stale and duplicate source evidence does not extend the failure watermark`() {
        val state = state()
        state.handle(Event.Observation(observation(end = 10, version = 2)), 10)
        assertTrue(state.handle(Event.Observation(observation(end = 100, version = 1)), 100).isEmpty())
        assertEquals(
            listOf(Command.CancelGrace),
            state.handle(Event.Observation(observation(OhttpHealthOutcome.Usable, start = 11, end = 101, version = 3)), 101),
        )
        assertEquals(Availability.HEALTHY, state.availability)
    }

    @Test
    fun `retained categories use request freshness, not receipt order or source version`() {
        for (successFirst in listOf(false, true)) {
            for (crossSource in listOf(false, true)) {
                val state = state()
                val success = observation(OhttpHealthOutcome.Usable, start = 0, end = 20, version = 100)
                val failure =
                    observation(
                        start = 1,
                        end = 10,
                        version = 1,
                        source = if (crossSource) OhttpHealthSource.NATIVE else OhttpHealthSource.CLIENT,
                    )
                if (successFirst) {
                    state.handle(Event.Observation(success), 20)
                    assertEquals(
                        listOf(Command.ScheduleGrace(1, 2_000_000_010)),
                        state.handle(Event.Observation(failure), 21),
                    )
                } else {
                    state.handle(Event.Observation(failure), 10)
                    assertTrue(state.handle(Event.Observation(success), 20).isEmpty())
                }
                assertEquals(Availability.SUSPECTED, state.availability, "successFirst=$successFirst crossSource=$crossSource")
            }
        }
    }

    @Test
    fun `unrelated ordinary outcomes do not mask an active investigation`() {
        listOf(
            OhttpHealthOutcome.Cancelled,
            OhttpHealthOutcome.BackendUnavailable,
            OhttpHealthOutcome.PolicyRejected,
            OhttpHealthOutcome.ConfigurationFailure,
        ).forEach { outcome ->
            val state = state()
            val id = beginConfirmation(state)
            assertTrue(
                state
                    .handle(
                        Event.Observation(
                            observation(outcome, start = 2_000_000_001, end = 2_500_000_000, version = 2, scope = OhttpHealthScope.SWAPS),
                        ),
                        2_500_000_000,
                    ).isEmpty(),
            )
            assertEquals(id, state.investigationId)
            assertEquals(
                1,
                state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 3_000_000_000).size,
            )
        }
    }

    @Test
    fun `a diagnostic notification cannot hide ordinary evidence of the same category`() {
        val state = state()
        state.handle(Event.Observation(observation()), 0)
        assertTrue(
            state
                .handle(
                    Event.Observation(
                        observation(OhttpHealthOutcome.Usable, start = 1, end = 2, version = 10, purpose = OhttpHealthPurpose.DIAGNOSTIC),
                    ),
                    2,
                ).isEmpty(),
        )
        assertEquals(
            listOf(Command.CancelGrace),
            state.handle(Event.Observation(observation(OhttpHealthOutcome.Usable, start = 1, end = 2, version = 2)), 3),
        )
    }

    @Test
    fun `a real failure during a classification starts the grace at its own evidence time`() {
        val state = state()
        val id = probeId(state.handle(Event.ClassificationRequested, 0))
        assertEquals(
            listOf(Command.CancelProbe, Command.ScheduleGrace(2, 3_000_000_000)),
            state.handle(Event.Observation(observation(end = 1_000_000_000)), 1_000_000_000),
        )
        assertTrue(
            state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 3_000_000_000).isEmpty(),
        )
        assertEquals(listOf(Command.Probe(2)), state.handle(Event.GraceExpired(2), 3_000_000_000))
    }

    @Test
    fun `a later terminal outcome of the same request supersedes an earlier transport failure`() {
        listOf(
            OhttpHealthOutcome.PolicyRejected,
            OhttpHealthOutcome.ConfigurationFailure,
            OhttpHealthOutcome.Usable,
            OhttpHealthOutcome.Cancelled,
            OhttpHealthOutcome.BackendUnavailable,
        ).forEach { outcome ->
            val state = state()
            confirm(state)
            state.handle(Event.Acknowledged, 4_000_000_000)
            val operation = operation(5_000_000_000)
            val failure = observation(start = 5_000_000_000, end = 6_000_000_000, version = 2, requestId = 42, operation = operation)
            val terminal =
                observation(outcome, start = 5_000_000_000, end = 7_000_000_000, version = 3, requestId = 42, operation = operation)
            assertTrue(
                state.handle(Event.OperationCompleted(operation, true, listOf(terminal, failure)), 8_000_000_000).isEmpty(),
                outcome.toString(),
            )
        }
    }

    @Test
    fun `mixed terminal causes of several requests are ineligible without guessing a final one`() {
        listOf(
            OhttpHealthOutcome.PolicyRejected,
            OhttpHealthOutcome.ConfigurationFailure,
            OhttpHealthOutcome.Usable,
            OhttpHealthOutcome.Cancelled,
        ).forEach { outcome ->
            val state = state()
            confirm(state)
            state.handle(Event.Acknowledged, 4_000_000_000)
            val operation = operation(5_000_000_000)
            val other = observation(outcome, start = 5_000_000_000, end = 6_000_000_000, version = 2, requestId = 41, operation = operation)
            val failure = observation(start = 7_000_000_000, end = 8_000_000_000, version = 3, requestId = 42, operation = operation)
            assertTrue(
                state.handle(Event.OperationCompleted(operation, true, listOf(other, failure)), 9_000_000_000).isEmpty(),
                outcome.toString(),
            )
        }
    }

    @Test
    fun `a later body failure and several terminal transport failures stay eligible`() {
        val state = state()
        confirm(state)
        state.handle(Event.Acknowledged, 4_000_000_000)
        val operation = operation(5_000_000_000)
        val head =
            observation(
                OhttpHealthOutcome.Usable,
                start = 5_000_000_000,
                end = 6_000_000_000,
                version = 2,
                requestId = 41,
                operation = operation,
                stage = OhttpHealthStage.RESPONSE_HEAD,
            )
        val body =
            observation(
                start = 5_000_000_000,
                end = 7_000_000_000,
                version = 3,
                requestId = 41,
                operation = operation,
                stage = OhttpHealthStage.RESPONSE_BODY,
            )
        val another = observation(start = 7_000_000_000, end = 8_000_000_000, version = 4, requestId = 42, operation = operation)
        assertEquals(
            listOf(present(1, 3_000_000_000, operation)),
            state.handle(Event.OperationCompleted(operation, true, listOf(body, head, another)), 9_000_000_000),
        )
    }

    @Test
    fun `an overlapping usable probe is inconclusive and suppresses repeated generic errors`() {
        val state = state()
        val id = beginConfirmation(state)
        state.handle(Event.Observation(observation(end = 2_500_000_000, version = 2)), 2_500_000_000)
        assertTrue(state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.Usable)), 3_000_000_000).isEmpty())
        assertEquals(Availability.UNKNOWN, state.availability)
        assertTrue(state.handle(Event.ClassificationRequested, 4_000_000_000).isEmpty())
        assertEquals(listOf(Command.Probe(2)), state.handle(Event.FallbackTick, 120_000_000_000))
    }
}
