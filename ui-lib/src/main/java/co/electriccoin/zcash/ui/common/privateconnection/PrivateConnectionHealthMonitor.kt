package co.electriccoin.zcash.ui.common.privateconnection

import cash.z.ecc.android.sdk.internal.Twig
import co.electriccoin.lightwallet.client.health.OhttpHealthClock
import co.electriccoin.lightwallet.client.health.OhttpHealthOperation
import co.electriccoin.lightwallet.client.health.OhttpHealthOperationCompleted
import co.electriccoin.lightwallet.client.health.OhttpHealthOutcome
import co.electriccoin.lightwallet.client.health.OhttpHealthRoute
import co.electriccoin.lightwallet.client.health.OhttpHealthSnapshot
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Command
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Episode
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Event
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Path
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.ProbeResult
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
 * One coroutine handles every event in order, so the policy is never touched concurrently. [start]
 * begins owning a route; [stop] retires the ownership and fences every late event and callback.
 *
 * The owner decides when a route is owned (the Private gateway, in the foreground, while the user
 * has not chosen the public fallback) and calls [start]/[stop]; this class does not look at the
 * wallet's server selection.
 */
@Suppress("LongParameterList")
class PrivateConnectionHealthMonitor(
    private val scope: CoroutineScope,
    /** The retained request-health evidence of a route (engine and client sources merged). */
    private val observeEvidence: (OhttpHealthRoute) -> Flow<OhttpHealthSnapshot>,
    /** Completed explicit user operations, with the evidence of the attempt that decided their result. */
    private val completions: Flow<OhttpHealthOperationCompleted>,
    /**
     * One bounded read-only probe of [route], through the same privacy route. Must not throw for a
     * failed probe: a failure is [ProbeResult.outcome]. [generation] identifies the ownership.
     */
    private val probe: suspend (route: OhttpHealthRoute, generation: Long) -> ProbeResult,
    private val pathUpdates: Flow<Path>,
    /** True when Tor is ready, or not needed; a Tor route is not diagnosed while it is not. */
    private val torReady: Flow<Boolean>,
    /** Foreground, unlocked, no send in progress: when the outage may be shown. */
    private val presentationAllowed: Flow<Boolean>,
    private val clock: () -> Long = OhttpHealthClock::nowNanos,
) {
    private val _outage = MutableStateFlow<PrivateConnectionOutage?>(null)

    /** The outage to show, or null. The sheet follows this: shown when non-null, closed when it clears. */
    val outage: StateFlow<PrivateConnectionOutage?> = _outage.asStateFlow()

    private var generation = 0L
    @Volatile
    private var ownership: Ownership? = null
    private var carried: PrivateConnectionHealth.Continuity? = null

    private class Ownership(
        val route: OhttpHealthRoute,
        val generation: Long,
        val state: State,
        val events: Channel<Event>,
        val jobs: MutableList<Job>,
    ) {
        var grace: Job? = null
        var probe: Job? = null
    }

    /**
     * Starts owning [route]. A previous ownership of the same route hands its confirmed outage over, so a
     * rebuild (a Tor toggle, a new synchronizer) does not forget an acknowledged episode.
     */
    @Synchronized
    fun start(route: OhttpHealthRoute) {
        val previous = ownership
        if (previous != null) {
            if (previous.route == route) carried = previous.state.continuity(clock())
            retire(previous)
        }
        generation += 1
        val now = clock()
        val state = State(route, generation, carried?.takeIf { previous?.route == route }, ownershipStartedAt = now)
        val owned = Ownership(route, generation, state, Channel(Channel.UNLIMITED), mutableListOf())
        ownership = owned
        if (previous?.route != route) _outage.value = null

        owned.jobs += scope.launch { for (event in owned.events) apply(owned, event) }
        owned.jobs +=
            observeEvidence(route)
                .onEach { snapshot -> snapshot.observations.forEach { owned.events.trySend(Event.Observation(it)) } }
                .catch { if (it is CancellationException) throw it }
                .launchIn(scope)
        owned.jobs +=
            completions
                .onEach { completed ->
                    owned.events.trySend(
                        Event.OperationCompleted(completed.operation, completed.failed, completed.observations),
                    )
                }.launchIn(scope)
        owned.jobs += pathUpdates.onEach { owned.events.trySend(Event.PathChanged(it)) }.launchIn(scope)
        owned.jobs += torReady.onEach { owned.events.trySend(Event.TorReadinessChanged(it)) }.launchIn(scope)
        owned.jobs += presentationAllowed.onEach { owned.events.trySend(Event.PresentationGateChanged(it)) }.launchIn(scope)
        owned.jobs +=
            scope.launch {
                while (true) {
                    delay(PrivateConnectionHealth.FALLBACK_NANOS / NANOS_PER_MILLI)
                    owned.events.trySend(Event.FallbackTick)
                }
            }
    }

    /** Stops owning the route (a background edge, the public fallback, a route change). Clears the outage. */
    @Synchronized
    fun stop() {
        ownership?.let { retire(it) }
        ownership = null
        carried = null
        _outage.value = null
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
        owned.events.trySend(Event.OwnershipRetired)
        owned.events.close()
        owned.jobs.forEach { it.cancel() }
        owned.grace?.cancel()
        owned.probe?.cancel()
    }

    private fun apply(
        owned: Ownership,
        event: Event,
    ) {
        if (ownership !== owned && event != Event.OwnershipRetired) return
        val commands = owned.state.handle(event, clock())
        // Evidence events are frequent and carry no decision; log the ones that do, and every command.
        val failure = event is Event.Observation && event.observation.outcome == OhttpHealthOutcome.TransportUnavailable
        if (failure || event !is Event.Observation && event !is Event.PresentationGateChanged || commands.isNotEmpty()) {
            Twig.info { "Private connection health: ${event.describe()} -> ${commands.map { it.describe() }}" }
        }
        commands.forEach { execute(owned, it) }
        // An outage is only ever shown for a live episode: a recovered or handed-over route clears it.
        if (owned.state.episode == null && _outage.value != null) _outage.value = null
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
                    scope.launch {
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
                    scope.launch {
                        val result = probe(owned.route, owned.generation)
                        owned.events.trySend(Event.ProbeCompleted(command.id, result))
                    }
            }
            Command.CancelProbe -> {
                owned.probe?.cancel()
                owned.probe = null
            }
            is Command.Present -> _outage.value = PrivateConnectionOutage(command.episode, command.operation)
            Command.DismissOutage -> _outage.value = null
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }

    private fun Event.describe(): String =
        when (this) {
            is Event.Observation -> "observation(${observation.source} ${observation.scope} ${observation.outcome})"
            is Event.ProbeCompleted -> "probeCompleted(#$id ${result.outcome})"
            is Event.OperationCompleted -> "operationCompleted(failed=$failed, evidence=${observations.size})"
            else -> toString()
        }

    private fun Command.describe(): String = toString()
}
