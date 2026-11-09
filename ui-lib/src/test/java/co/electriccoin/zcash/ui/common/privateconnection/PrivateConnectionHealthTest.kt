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
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Path
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.ProbeResult
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.State
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The policy's rules, ported scenario by scenario from the iOS `PrivateConnectionHealthTests` (same
 * nanosecond constants), which are the oracle for the Android behaviour.
 */
class PrivateConnectionHealthTest {
    private val generation = 1L
    private val route = OhttpHealthRoute("gateway.test", "https://relay.test", viaTor = false)

    private fun state(
        route: OhttpHealthRoute = this.route,
        continuity: PrivateConnectionHealth.Continuity? = null,
        ownershipStartedAt: Long = 0,
    ) = State(route, generation, continuity, ownershipStartedAt)

    private fun observation(
        outcome: OhttpHealthOutcome = OhttpHealthOutcome.TransportUnavailable,
        start: Long = 0,
        end: Long = 0,
        version: Long = 1,
        requestId: Long? = null,
        source: OhttpHealthSource = OhttpHealthSource.CLIENT,
        scope: OhttpHealthScope = OhttpHealthScope.ROUTE,
        purpose: OhttpHealthPurpose = OhttpHealthPurpose.BACKGROUND,
        historical: Boolean = false,
        operation: OhttpHealthOperation? = null,
        selectedRoute: OhttpHealthRoute? = null,
    ) = OhttpHealthObservation(
        source = source,
        route = selectedRoute ?: route,
        scope = scope,
        outcome = outcome,
        stage = OhttpHealthStage.COMPLETE,
        version = version,
        requestId = requestId ?: version,
        startedAtNanos = start,
        observedAtNanos = end,
        operation = operation,
        purpose = purpose,
        historical = historical,
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
        val commands = state.handle(Event.Observation(observation(selectedRoute = state.route)), 0)
        val grace = assertNotNull(commands.filterIsInstance<Command.ScheduleGrace>().firstOrNull())
        assertEquals(2_000_000_000, grace.deadline)
        return probeId(state.handle(Event.GraceExpired(grace.id), 2_000_000_000))
    }

    private fun confirm(state: State): List<Command> {
        val id = beginConfirmation(state)
        return state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 3_000_000_000)
    }

    private fun present(id: Long, confirmedAt: Long, operation: OhttpHealthOperation? = null) =
        Command.Present(Episode(id, confirmedAt), operation)

    @Test
    fun `ownership transfer keeps the acknowledged episode but clears evidence`() {
        val original = state()
        confirm(original)
        original.handle(Event.Acknowledged, 4_000_000_000)
        original.handle(Event.FallbackTick, 120_000_000_000)
        val oldOperation = operation(121_000_000_000)
        val oldFailure = observation(start = 121_000_000_000, end = 122_000_000_000, version = 2, operation = oldOperation)
        original.handle(Event.OperationCompleted(oldOperation, true, listOf(oldFailure)), 122_000_000_000)
        val episode = assertNotNull(original.episode)
        var transferred =
            state(continuity = original.continuity(123_000_000_000), ownershipStartedAt = 123_000_000_000)
        for (floor in listOf(124_000_000_000, 125_000_000_000)) {
            assertEquals(episode, transferred.episode)
            assertEquals(4_000_000_000, transferred.acknowledgedAt)
            assertEquals(Availability.UNKNOWN, transferred.availability)
            assertTrue(transferred.retainedEvidence.isEmpty())
            assertTrue(transferred.completedOperations.isEmpty())
            assertNull(transferred.pendingPresentation)
            assertNull(transferred.investigationId)
            assertNull(transferred.graceDeadline)
            assertTrue(!transferred.isPresented)
            assertTrue(transferred.handle(Event.PresentationGateChanged(true), floor).isEmpty())
            assertTrue(transferred.handle(Event.OperationCompleted(oldOperation, true, listOf(oldFailure)), floor).isEmpty())
            transferred = state(continuity = transferred.continuity(floor), ownershipStartedAt = floor)
        }
        transferred.handle(
            Event.Observation(observation(OhttpHealthOutcome.Usable, start = 126_000_000_000, end = 127_000_000_000)),
            127_000_000_000,
        )
        assertNull(transferred.episode)
        transferred.handle(
            Event.Observation(observation(start = 128_000_000_000, end = 129_000_000_000, version = 2)),
            129_000_000_000,
        )
        val id = assertNotNull(transferred.investigationId)
        transferred.handle(Event.GraceExpired(id), 131_000_000_000)
        transferred.handle(
            Event.ProbeCompleted(
                id,
                result(OhttpHealthOutcome.TransportUnavailable, start = 131_000_000_001, end = 132_000_000_000),
            ),
            132_000_000_000,
        )
        assertEquals(2L, transferred.episode?.id)
    }

    @Test
    fun `a carried episode can revalidate after the ownership floor`() {
        for (acknowledged in listOf(false, true)) {
            val original = state()
            confirm(original)
            if (acknowledged) original.handle(Event.Acknowledged, 4_000_000_000)
            val transferred = state(continuity = original.continuity(10_000_000_000), ownershipStartedAt = 10_000_000_000)
            assertTrue(transferred.handle(Event.PresentationGateChanged(true), 11_000_000_000).isEmpty())
            assertTrue(
                transferred
                    .handle(
                        Event.Observation(observation(OhttpHealthOutcome.Usable, start = 9_000_000_000, end = 11_000_000_000)),
                        11_000_000_000,
                    ).isEmpty(),
            )
            assertEquals(1L, transferred.episode?.id)
            val first = probeId(transferred.handle(Event.FallbackTick, 120_000_000_000))
            assertTrue(
                transferred
                    .handle(
                        Event.ProbeCompleted(
                            first,
                            result(OhttpHealthOutcome.TransportUnavailable, start = 9_000_000_000, end = 121_000_000_000),
                        ),
                        121_000_000_000,
                    ).isEmpty(),
            )
            assertEquals(Availability.UNKNOWN, transferred.availability)
            val second = probeId(transferred.handle(Event.FallbackTick, 240_000_000_000))
            transferred.handle(
                Event.Observation(observation(start = 240_000_000_001, end = 240_500_000_000, version = 2)),
                240_500_000_000,
            )
            val commands =
                transferred.handle(
                    Event.ProbeCompleted(
                        second,
                        result(OhttpHealthOutcome.TransportUnavailable, start = 240_000_000_000, end = 241_000_000_000),
                    ),
                    241_000_000_000,
                )
            assertEquals(Availability.UNAVAILABLE, transferred.availability)
            assertEquals(1L, transferred.episode?.id)
            assertEquals(if (acknowledged) 4_000_000_000 else null, transferred.acknowledgedAt)
            assertEquals(if (acknowledged) emptyList() else listOf(present(1, 3_000_000_000)), commands)
        }
    }

    @Test
    fun `a carry keeps an unexpired pause and a fresh success recovers`() {
        val original = state()
        confirm(original)
        original.handle(Event.Acknowledged, 4_000_000_000)
        original.handle(
            Event.Observation(observation(OhttpHealthOutcome.RateLimited(200_000_000_000), version = 2)),
            5_000_000_000,
        )
        val transferred = state(continuity = original.continuity(10_000_000_000), ownershipStartedAt = 10_000_000_000)
        assertEquals(200_000_000_000, transferred.pauseUntil)
        assertTrue(transferred.handle(Event.FallbackTick, 120_000_000_000).isEmpty())
        assertTrue(transferred.handle(Event.PresentationGateChanged(true), 200_000_000_000).isEmpty())
        assertNull(transferred.investigationId)
        val id = probeId(transferred.handle(Event.FallbackTick, 240_000_000_000))
        assertTrue(
            transferred
                .handle(
                    Event.ProbeCompleted(
                        id,
                        result(OhttpHealthOutcome.Usable, start = 240_000_000_000, end = 241_000_000_000),
                    ),
                    241_000_000_000,
                ).isEmpty(),
        )
        assertEquals(Availability.HEALTHY, transferred.availability)
        assertNull(transferred.episode)
        assertNull(transferred.acknowledgedAt)
        assertNull(transferred.continuity(241_000_000_000).pauseUntil)
    }

    @Test
    fun `the first failure uses exactly two seconds and bursts do not extend it`() {
        val state = state()
        assertEquals(listOf(Command.ScheduleGrace(1, 2_000_000_000)), state.handle(Event.Observation(observation()), 0))
        assertTrue(state.handle(Event.Observation(observation(end = 1_000_000_000, version = 2)), 1_000_000_000).isEmpty())
        assertEquals(2_000_000_000, state.graceDeadline)
        assertTrue(state.handle(Event.GraceExpired(1), 1_999_999_999).isEmpty())
        assertEquals(listOf(Command.Probe(1)), state.handle(Event.GraceExpired(1), 2_000_000_000))
        assertTrue(state.handle(Event.GraceExpired(1), 2_000_000_001).isEmpty())
    }

    @Test
    fun `a fresh success cancels the grace without a probe`() {
        val state = state()
        state.handle(Event.Observation(observation()), 0)
        assertEquals(
            listOf(Command.CancelGrace),
            state.handle(
                Event.Observation(observation(OhttpHealthOutcome.Usable, start = 1_000_000_000, end = 1_500_000_000, version = 2)),
                1_500_000_000,
            ),
        )
        assertTrue(state.handle(Event.GraceExpired(1), 2_000_000_000).isEmpty())
        assertEquals(Availability.HEALTHY, state.availability)
    }

    @Test
    fun `a confirmation failure presents exactly once`() {
        val state = state()
        assertEquals(listOf(present(1, 3_000_000_000)), confirm(state))
        assertEquals(listOf(Command.Probe(2)), state.handle(Event.FallbackTick, 123_000_000_000))
        assertTrue(
            state
                .handle(
                    Event.ProbeCompleted(
                        2,
                        result(OhttpHealthOutcome.TransportUnavailable, start = 123_000_000_000, end = 124_000_000_000),
                    ),
                    124_000_000_000,
                ).isEmpty(),
        )
        assertEquals(1L, state.episode?.id)
    }

    @Test
    fun `a usable confirmation never presents`() {
        val state = state()
        val id = beginConfirmation(state)
        assertTrue(state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.Usable)), 3_000_000_000).isEmpty())
        assertEquals(Availability.HEALTHY, state.availability)
        assertNull(state.episode)
    }

    @Test
    fun `source versions never compare across sources and an overlapping success cannot heal`() {
        val state = state()
        state.handle(
            Event.Observation(observation(end = 1_000_000_000, version = 999, source = OhttpHealthSource.NATIVE)),
            1_000_000_000,
        )
        assertTrue(
            state
                .handle(
                    Event.Observation(observation(OhttpHealthOutcome.Usable, start = 500_000_000, end = 2_000_000_000, version = 1)),
                    2_000_000_000,
                ).isEmpty(),
        )
        assertEquals(Availability.SUSPECTED, state.availability)
        assertEquals(
            listOf(Command.CancelGrace),
            state.handle(
                Event.Observation(
                    observation(OhttpHealthOutcome.Usable, start = 1_000_000_001, end = 2_000_000_001, version = 2),
                ),
                2_000_000_001,
            ),
        )
        assertEquals(Availability.HEALTHY, state.availability)
    }

    @Test
    fun `an old failure cannot reopen a healthy route`() {
        val state = state()
        state.handle(Event.Observation(observation(OhttpHealthOutcome.Usable, start = 10, end = 20)), 20)
        assertTrue(state.handle(Event.Observation(observation(start = 0, end = 9, version = 2)), 21).isEmpty())
        assertEquals(Availability.HEALTHY, state.availability)
    }

    @Test
    fun `outcomes that are not unavailability never start suspicion`() {
        listOf(
            OhttpHealthOutcome.BackendUnavailable,
            OhttpHealthOutcome.PolicyRejected,
            OhttpHealthOutcome.ConfigurationFailure,
            OhttpHealthOutcome.ResponseRejected,
            OhttpHealthOutcome.Cancelled,
            OhttpHealthOutcome.Inconclusive,
        ).forEach { outcome ->
            val state = state()
            assertTrue(
                state.handle(Event.Observation(observation(outcome, scope = OhttpHealthScope.SWAPS)), 0).isEmpty(),
                outcome.toString(),
            )
            assertEquals(Availability.UNKNOWN, state.availability)
        }
    }

    @Test
    fun `a valid route response heals despite a backend failure`() {
        val state = state()
        state.handle(Event.Observation(observation()), 0)
        assertTrue(
            state
                .handle(
                    Event.Observation(
                        observation(OhttpHealthOutcome.BackendUnavailable, start = 1, end = 2, version = 2, scope = OhttpHealthScope.SWAPS),
                    ),
                    2,
                ).isEmpty(),
        )
        assertEquals(
            listOf(Command.CancelGrace),
            state.handle(Event.Observation(observation(OhttpHealthOutcome.Usable, start = 1, end = 2, version = 3)), 2),
        )
        assertEquals(Availability.HEALTHY, state.availability)
    }

    @Test
    fun `an inconclusive or rejected probe suppresses repeats until the tick`() {
        listOf(
            OhttpHealthOutcome.Inconclusive,
            OhttpHealthOutcome.Cancelled,
            OhttpHealthOutcome.PolicyRejected,
            OhttpHealthOutcome.ConfigurationFailure,
            OhttpHealthOutcome.ResponseRejected,
            OhttpHealthOutcome.BackendUnavailable,
        ).forEach { outcome ->
            val state = state()
            val id = probeId(state.handle(Event.ClassificationRequested, 0))
            assertTrue(state.handle(Event.ClassificationRequested, 1).isEmpty())
            assertTrue(state.handle(Event.ProbeCompleted(id, result(outcome)), 3_000_000_000).isEmpty())
            assertTrue(state.handle(Event.ClassificationRequested, 4_000_000_000).isEmpty())
            assertTrue(
                state.handle(Event.Observation(observation(end = 5_000_000_000, version = 2)), 5_000_000_000).isEmpty(),
            )
            assertEquals(listOf(Command.Probe(2)), state.handle(Event.FallbackTick, 120_000_000_000), outcome.toString())
        }
    }

    @Test
    fun `a fallback tick or a generic error failure uses grace then a fresh confirmation`() {
        for (event in listOf<Event>(Event.FallbackTick, Event.ClassificationRequested)) {
            val state = state()
            val id = probeId(state.handle(event, 0))
            assertEquals(
                listOf(Command.ScheduleGrace(2, 3_000_000_000)),
                state.handle(
                    Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable, start = 0, end = 1_000_000_000)),
                    1_000_000_000,
                ),
            )
            assertNull(state.episode)
            assertEquals(listOf(Command.Probe(2)), state.handle(Event.GraceExpired(2), 3_000_000_000))
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
        }
    }

    @Test
    fun `the fallback freshness boundary requires a new applicable success`() {
        val state = state()
        state.handle(Event.Observation(observation(OhttpHealthOutcome.Usable, start = 1, end = 10)), 10)
        assertTrue(state.handle(Event.FallbackTick, 120_000_000_009).isEmpty())
        assertEquals(listOf(Command.Probe(1)), state.handle(Event.FallbackTick, 120_000_000_010))
    }

    @Test
    fun `historical success never skips the fallback but a historical pause still blocks it`() {
        val state = state()
        assertTrue(
            state.handle(Event.Observation(observation(OhttpHealthOutcome.Usable, end = 10, historical = true)), 10).isEmpty(),
        )
        assertTrue(
            state
                .handle(
                    Event.Observation(observation(OhttpHealthOutcome.RateLimited(30), end = 11, version = 2, historical = true)),
                    11,
                ).isEmpty(),
        )
        assertTrue(state.handle(Event.FallbackTick, 29).isEmpty())
        assertEquals(listOf(Command.Probe(1)), state.handle(Event.FallbackTick, 30))
    }

    @Test
    fun `a historical failure never starts or confirms an outage`() {
        val state = state()
        assertTrue(state.handle(Event.Observation(observation(historical = true)), 0).isEmpty())
        assertEquals(Availability.UNKNOWN, state.availability)
    }

    @Test
    fun `an explicit pause cancels the investigation and requires a rearm`() {
        val state = state()
        val id = beginConfirmation(state)
        assertEquals(
            listOf(Command.CancelProbe),
            state.handle(
                Event.Observation(observation(OhttpHealthOutcome.RateLimited(9_000_000_000), end = 3_000_000_000, version = 2)),
                3_000_000_000,
            ),
        )
        assertTrue(
            state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 3_000_000_000).isEmpty(),
        )
        assertTrue(state.handle(Event.ClassificationRequested, 10_000_000_000).isEmpty())
        assertEquals(listOf(Command.Probe(2)), state.handle(Event.FallbackTick, 10_000_000_000))
    }

    @Test
    fun `permitted paths allow a diagnostic`() {
        listOf(Path.UNKNOWN, Path.REQUIRES_CONNECTION, Path.AVAILABLE).forEach { path ->
            val state = state()
            state.handle(Event.PathChanged(path), 0)
            assertEquals(listOf(Command.Probe(1)), state.handle(Event.ClassificationRequested, 1), path.toString())
        }
    }

    @Test
    fun `an unavailable path cancels and a reconnect rearms without an internet ping`() {
        val state = state()
        beginConfirmation(state)
        assertEquals(listOf(Command.CancelProbe), state.handle(Event.PathChanged(Path.UNAVAILABLE), 3_000_000_000))
        assertTrue(state.handle(Event.FallbackTick, 120_000_000_000).isEmpty())
        assertEquals(listOf(Command.Probe(2)), state.handle(Event.PathChanged(Path.REQUIRES_CONNECTION), 121_000_000_000))
    }

    @Test
    fun `Tor readiness takes precedence and ready rearms the same route`() {
        val state = state(route = route.copy(viaTor = true))
        assertTrue(state.handle(Event.ClassificationRequested, 0).isEmpty())
        assertEquals(listOf(Command.Probe(1)), state.handle(Event.TorReadinessChanged(true), 1))
        assertEquals(listOf(Command.CancelProbe), state.handle(Event.TorReadinessChanged(false), 2))
    }

    @Test
    fun `acknowledgement allows only a new failed operation and deduplicates it`() {
        val state = state()
        confirm(state)
        assertEquals(listOf(Command.DismissOutage), state.handle(Event.Acknowledged, 4_000_000_000))
        assertTrue(state.handle(Event.Observation(observation(end = 5_000_000_000, version = 2)), 5_000_000_000).isEmpty())
        assertEquals(listOf(Command.Probe(2)), state.handle(Event.FallbackTick, 120_000_000_000))
        assertTrue(
            state
                .handle(
                    Event.ProbeCompleted(
                        2,
                        result(OhttpHealthOutcome.TransportUnavailable, start = 120_000_000_000, end = 121_000_000_000),
                    ),
                    121_000_000_000,
                ).isEmpty(),
        )
        val oldOperation = operation(3_000_000_000)
        val oldEvidence = observation(end = 122_000_000_000, version = 3, operation = oldOperation)
        assertTrue(state.handle(Event.OperationCompleted(oldOperation, true, listOf(oldEvidence)), 122_000_000_000).isEmpty())
        val operation = operation(123_000_000_000)
        val evidence = observation(start = 123_000_000_000, end = 124_000_000_000, version = 4, operation = operation)
        assertEquals(
            listOf(present(1, 3_000_000_000, operation)),
            state.handle(Event.OperationCompleted(operation, true, listOf(evidence)), 124_000_000_000),
        )
        assertTrue(
            state.handle(Event.OperationCompleted(operation, true, listOf(evidence, evidence)), 125_000_000_000).isEmpty(),
        )
    }

    @Test
    fun `a local failure or a final policy rejection never uses an earlier RPC failure`() {
        val state = state()
        confirm(state)
        state.handle(Event.Acknowledged, 4_000_000_000)
        val operation = operation(5_000_000_000)
        state.handle(Event.Observation(observation(end = 6_000_000_000, version = 2, operation = operation)), 6_000_000_000)
        assertTrue(state.handle(Event.OperationCompleted(operation, true, emptyList()), 7_000_000_000).isEmpty())
        val rejected = operation(8_000_000_000)
        assertTrue(
            state
                .handle(
                    Event.OperationCompleted(
                        rejected,
                        true,
                        listOf(observation(OhttpHealthOutcome.PolicyRejected, end = 9_000_000_000, version = 3, operation = rejected)),
                    ),
                    9_000_000_000,
                ).isEmpty(),
        )
        val recovered = operation(10_000_000_000)
        assertTrue(
            state
                .handle(
                    Event.OperationCompleted(
                        recovered,
                        false,
                        listOf(observation(end = 11_000_000_000, version = 4, operation = recovered)),
                    ),
                    11_000_000_000,
                ).isEmpty(),
        )
    }

    @Test
    fun `a pending presentation waits for the gate and recovery clears it`() {
        val state = state()
        state.handle(Event.PresentationGateChanged(false), 0)
        assertTrue(confirm(state).isEmpty())
        assertTrue(state.presentationPending)
        assertEquals(1, state.handle(Event.PresentationGateChanged(true), 4_000_000_000).size)
        assertEquals(
            listOf(Command.DismissOutage),
            state.handle(
                Event.Observation(observation(OhttpHealthOutcome.Usable, start = 4_000_000_001, end = 5_000_000_000, version = 2)),
                5_000_000_000,
            ),
        )
        assertEquals(
            listOf(Command.ScheduleGrace(2, 9_000_000_000)),
            state.handle(Event.Observation(observation(start = 6_000_000_000, end = 7_000_000_000, version = 3)), 7_000_000_000),
        )
    }

    @Test
    fun `retirement cancels and fences every late event`() {
        val state = state()
        val id = beginConfirmation(state)
        assertEquals(listOf(Command.CancelProbe), state.handle(Event.OwnershipRetired, 3_000_000_000))
        assertTrue(
            state.handle(Event.ProbeCompleted(id, result(OhttpHealthOutcome.TransportUnavailable)), 4_000_000_000).isEmpty(),
        )
        assertTrue(state.handle(Event.Observation(observation(end = 5_000_000_000, version = 2)), 5_000_000_000).isEmpty())
        assertTrue(state.handle(Event.FallbackTick, 120_000_000_000).isEmpty())
    }
}
