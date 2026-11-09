package co.electriccoin.zcash.ui.common.privateconnection

import cash.z.ecc.android.sdk.internal.Twig
import co.electriccoin.lightwallet.client.health.OhttpHealthClock
import co.electriccoin.lightwallet.client.health.OhttpHealthOperation
import co.electriccoin.lightwallet.client.health.OhttpHealthOperationCompleted
import co.electriccoin.lightwallet.client.health.OhttpHealthOutcome
import co.electriccoin.lightwallet.client.health.OhttpHealthRoute
import co.electriccoin.lightwallet.client.health.OhttpHealthSnapshot
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Command
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Continuity
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Episode
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Event
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Path
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.ProbeResult
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A confirmed private-connection outage that should be presented, with the user action that revealed it (if any). */
data class PrivateConnectionOutage(
    val episode: Episode,
    val operation: OhttpHealthOperation?,
)

/**
 * Runs [PrivateConnectionHealth] for one owned route: feeds it the evidence, the network path, Tor
 * readiness and the presentation gate, and carries out its commands (the 2 s grace timer, the bounded
 * confirmation probe, the 120 s fallback tick, showing and dismissing the outage).
 *
 * One coroutine handles every event in order, so the policy is never touched concurrently. Everything an
 * ownership starts lives under one [Job] of its own, so retiring it cancels the timers, the probe and the
 * evidence collectors together.
 *
 * [start] begins owning a route. [pause] gives the ownership up for a while (the background) and keeps
 * what the policy knows about a confirmed outage, including the user's "Keep waiting", for the next [start]
 * of the same route; the shown outage is left alone. [stop] ends it for good: the outage is cleared and
 * nothing is carried. The owner decides when a route is owned; this class does not look at the wallet's
 * server selection.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class PrivateConnectionHealthMonitor(
    private val scope: CoroutineScope,
    /** The retained request-health evidence of a route (engine and client sources merged). */
    private val observeEvidence: (OhttpHealthRoute) -> Flow<OhttpHealthSnapshot>,
    /** Completed explicit user operations, with the evidence of the attempt that decided their result. */
    private val completions: Flow<OhttpHealthOperationCompleted>,
    /**
     * One bounded read-only probe of [route], through the same privacy route. A failed probe is
     * [ProbeResult.outcome]; one that throws is treated as inconclusive. [generation] identifies the ownership.
     */
    private val probe: suspend (route: OhttpHealthRoute, generation: Long) -> ProbeResult,
    private val pathUpdates: Flow<Path>,
    /** True when Tor is ready, or not needed; a Tor route is not diagnosed while it is not. */
    private val torReady: Flow<Boolean>,
    /** Foreground and unlocked: when the outage may be shown. */
    private val presentationAllowed: Flow<Boolean>,
    private val clock: () -> Long = OhttpHealthClock::nowNanos,
) {
    private val _outage = MutableStateFlow<PrivateConnectionOutage?>(null)

    /** The outage to show, or null. The sheet follows this: shown when non-null, closed when it clears. */
    val outage: StateFlow<PrivateConnectionOutage?> = _outage.asStateFlow()

    private var generation = 0L

    @Volatile
    private var ownership: Ownership? = null

    /** What a paused ownership knew, and of which route; used only when the same route is owned again. */
    private var carried: Pair<OhttpHealthRoute, Continuity>? = null

    private class Ownership(
        val route: OhttpHealthRoute,
        val generation: Long,
        val state: State,
        val events: Channel<Event>,
        val job: Job,
        val scope: CoroutineScope,
    ) {
        @Volatile
        var grace: Job? = null

        @Volatile
        var probe: Job? = null
    }

    /**
     * Starts owning [route]. An ownership of the same route that was paused (or is being replaced) hands its
     * confirmed outage over, so a background trip or a rebuild does not forget an acknowledged episode. A different
     * route (a Tor toggle changes it) starts clean: the shown outage is cleared, and a still-dead relay is
     * confirmed again.
     */
    @Synchronized
    fun start(route: OhttpHealthRoute) {
        val previous = ownership
        if (previous != null) {
            carried = route.takeIf { previous.route == it }?.let { it to previous.state.continuity(clock()) }
            retire(previous)
        }
        val continuity = carried?.takeIf { it.first == route }?.second
        if (continuity == null) {
            carried = null
            _outage.update { null }
        }
        generation += 1
        val now = clock()
        val state = State(route, generation, continuity, ownershipStartedAt = now)
        val job = SupervisorJob(scope.coroutineContext[Job])
        val own = CoroutineScope(scope.coroutineContext + job)
        val owned = Ownership(route, generation, state, Channel(Channel.UNLIMITED), job, own)
        ownership = owned

        own.launch { for (event in owned.events) apply(owned, event) }
        observeEvidence(route)
            .onEach { snapshot -> snapshot.observations.forEach { owned.events.trySend(Event.Observation(it)) } }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                Twig.error(failure) { "Private connection health: the evidence stream failed" }
            }.launchIn(own)
        completions
            .onEach { completed ->
                owned.events.trySend(
                    Event.OperationCompleted(completed.operation, completed.failed, completed.observations)
                )
            }.launchIn(own)
        pathUpdates.onEach { owned.events.trySend(Event.PathChanged(it)) }.launchIn(own)
        torReady.onEach { owned.events.trySend(Event.TorReadinessChanged(it)) }.launchIn(own)
        presentationAllowed.onEach { owned.events.trySend(Event.PresentationGateChanged(it)) }.launchIn(own)
        own.launch {
            while (true) {
                delay(PrivateConnectionHealth.FALLBACK_NANOS / NANOS_PER_MILLI)
                owned.events.trySend(Event.FallbackTick)
            }
        }
    }

    /**
     * Gives the ownership up without forgetting: the policy's continuity of the route is kept for the next
     * [start] of the same route, and the shown outage stays as it is.
     */
    @Synchronized
    fun pause() {
        val owned = ownership ?: return
        carried = owned.route to owned.state.continuity(clock())
        retire(owned)
        ownership = null
    }

    /** Stops owning the route for good (the public fallback, a manual server, a route change). Clears the outage. */
    @Synchronized
    fun stop() {
        ownership?.let { retire(it) }
        ownership = null
        carried = null
        _outage.update { null }
    }

    /** The user chose "Keep waiting" or dismissed the sheet: the outage is acknowledged. */
    fun acknowledge() {
        ownership?.events?.trySend(Event.Acknowledged)
    }

    /** A generic synchronizer error with no request identity: it asks for one classifying probe. */
    fun onGenericError() {
        ownership?.events?.trySend(Event.ClassificationRequested)
    }

    private fun retire(owned: Ownership) {
        owned.events.close()
        owned.job.cancel()
    }

    /**
     * Handles one event under the same lock as [start], [pause] and [stop]: the ownership check and every write
     * of the outage are atomic with respect to them, so a late `Present` can never show an outage after the
     * route was given up (a switch to Public, for instance).
     */
    private fun apply(
        owned: Ownership,
        event: Event,
    ) = synchronized(this) {
        if (ownership !== owned) return@synchronized
        val commands = owned.state.handle(event, clock())
        // Evidence events are frequent and carry no decision: log what decides something.
        if ((event !is Event.Observation && event !is Event.PresentationGateChanged) || commands.isNotEmpty()) {
            Twig.info { "Private connection health: ${event.describe()} -> ${commands.map { it.toString() }}" }
        }
        commands.forEach { execute(owned, it) }
        /* What the user dismissed is no longer shown, whether or not the policy had marked it as presented (a
        paused ownership that resumed has not). */
        if (event == Event.Acknowledged) _outage.update { null }
        // An outage is only ever shown for a live episode: a recovered or handed-over route clears it.
        if (owned.state.episode == null) _outage.update { null }
    }

    private fun execute(
        owned: Ownership,
        command: Command,
    ) {
        when (command) {
            is Command.ScheduleGrace -> {
                owned.grace?.cancel()
                // Rounded up: a timer that fires before the deadline is ignored by the policy and would leave
                // the investigation stuck.
                val delayMs = ((command.deadline - clock() + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI).coerceAtLeast(0)
                owned.grace =
                    owned.scope.launch {
                        delay(delayMs)
                        owned.events.trySend(Event.GraceExpired(command.id))
                    }
            }

            Command.CancelGrace -> {
                owned.grace?.cancel()
                owned.grace = null
            }

            is Command.Probe -> {
                owned.probe?.cancel()
                owned.probe =
                    owned.scope.launch {
                        owned.events.trySend(Event.ProbeCompleted(command.id, runProbe(owned)))
                    }
            }

            Command.CancelProbe -> {
                owned.probe?.cancel()
                owned.probe = null
            }

            is Command.Present -> {
                _outage.update { PrivateConnectionOutage(command.episode, command.operation) }
            }

            Command.DismissOutage -> {
                _outage.update { null }
            }
        }
    }

    /**
     * A probe that throws must still end its investigation, or nothing could ever be presented again; it is
     * inconclusive, which the policy never counts as an outage.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runProbe(owned: Ownership): ProbeResult {
        val started = clock()
        return try {
            probe(owned.route, owned.generation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Twig.error(failure) { "Private connection health: the probe failed unexpectedly" }
            ProbeResult(OhttpHealthOutcome.Inconclusive, owned.route, owned.generation, started, clock())
        }
    }

    private fun Event.describe(): String =
        when (this) {
            is Event.Observation -> "observation(${observation.source} ${observation.scope} ${observation.outcome})"
            is Event.ProbeCompleted -> "probeCompleted(#$id ${result.outcome})"
            is Event.OperationCompleted -> "operationCompleted(failed=$failed, evidence=${observations.size})"
            else -> toString()
        }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
