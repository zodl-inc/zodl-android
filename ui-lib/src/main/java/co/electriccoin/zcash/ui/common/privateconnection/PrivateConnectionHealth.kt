@file:Suppress("ReturnCount", "ComplexCondition", "CyclomaticComplexMethod", "TooManyFunctions")

package co.electriccoin.zcash.ui.common.privateconnection

import co.electriccoin.lightwallet.client.health.OhttpHealthObservation
import co.electriccoin.lightwallet.client.health.OhttpHealthOperation
import co.electriccoin.lightwallet.client.health.OhttpHealthOutcome
import co.electriccoin.lightwallet.client.health.OhttpHealthPurpose
import co.electriccoin.lightwallet.client.health.OhttpHealthRoute
import co.electriccoin.lightwallet.client.health.OhttpHealthScope
import co.electriccoin.lightwallet.client.health.OhttpHealthSource

/**
 * Pure policy for one owned private route (a Kotlin port of the iOS `PrivateConnectionHealth`, MOB-2300).
 * All time and external work enter as events; the policy answers with commands. It owns no clock, no
 * coroutine and no Android type, so every rule is exercised by a plain unit test.
 *
 * Times are [co.electriccoin.lightwallet.client.health.OhttpHealthClock] nanoseconds. Observations
 * carry no subscription generation: the owner runs one subscription per [State] and drops late
 * deliveries of an older one, so only the route is checked here.
 */
object PrivateConnectionHealth {
    enum class Path { UNKNOWN, REQUIRES_CONNECTION, AVAILABLE, UNAVAILABLE }

    enum class Availability { UNKNOWN, HEALTHY, SUSPECTED, UNAVAILABLE }

    data class Episode(
        val id: Long,
        val confirmedAt: Long,
    )

    /** Only confirmed outage decisions and an unexpired route pause survive a change of request ownership. */
    data class Continuity(
        val episode: Episode?,
        val acknowledgedAt: Long?,
        val nextEpisodeId: Long,
        val pauseUntil: Long?,
        val unacknowledgedPresentation: Boolean,
    )

    /** The result of one bounded read-only probe of the route. */
    data class ProbeResult(
        val outcome: OhttpHealthOutcome,
        val route: OhttpHealthRoute,
        val generation: Long,
        val startedAtNanos: Long,
        val observedAtNanos: Long,
    )

    sealed interface Event {
        data class Observation(
            val observation: OhttpHealthObservation,
        ) : Event

        data class OperationCompleted(
            val operation: OhttpHealthOperation,
            val failed: Boolean,
            val observations: List<OhttpHealthObservation>,
        ) : Event

        /** A generic error (no request identity) asks for a classifying probe. */
        data object ClassificationRequested : Event

        data class GraceExpired(
            val id: Long,
        ) : Event

        data class ProbeCompleted(
            val id: Long,
            val result: ProbeResult,
        ) : Event

        data object FallbackTick : Event

        data class PathChanged(
            val path: Path,
        ) : Event

        data class TorReadinessChanged(
            val ready: Boolean,
        ) : Event

        data class PresentationGateChanged(
            val allowed: Boolean,
        ) : Event

        /** The user chose "Keep waiting" or dismissed the sheet. */
        data object Acknowledged : Event

        data object OwnershipRetired : Event
    }

    sealed interface Command {
        data class ScheduleGrace(
            val id: Long,
            val deadline: Long,
        ) : Command

        data object CancelGrace : Command

        data class Probe(
            val id: Long,
        ) : Command

        data object CancelProbe : Command

        data class Present(
            val episode: Episode,
            val operation: OhttpHealthOperation?,
        ) : Command

        data object DismissOutage : Command
    }

    const val GRACE_NANOS = 2_000_000_000L
    const val FALLBACK_NANOS = 120_000_000_000L

    private enum class Investigation { GRACE, CLASSIFICATION, CONFIRMATION, RECHECK }

    private data class RequestIdentity(
        val source: OhttpHealthSource,
        val id: Long,
    )

    class State(
        val route: OhttpHealthRoute,
        val generation: Long,
        continuity: Continuity? = null,
        val ownershipStartedAt: Long = 0L,
    ) {
        var path = Path.UNKNOWN
            private set
        var torReady = false
            private set
        var availability = Availability.UNKNOWN
            private set
        var graceDeadline: Long? = null
            private set
        var investigationId: Long? = null
            private set
        var pauseUntil: Long? = null
            private set
        var retainedEvidence: List<OhttpHealthObservation> = emptyList()
            private set
        var acknowledgedAt: Long? = null
            private set
        var completedOperations: Set<String> = emptySet()
            private set
        var pendingPresentation: OhttpHealthOperation? = null
            private set
        var episode: Episode? = null
            private set
        var presentationAllowed = true
            private set
        var presentationPending = false
            private set
        var isPresented = false
            private set
        var retired = false
            private set

        private var investigation: Investigation? = null
        private var investigationFailureAt: Long? = null
        private var nextId = 0L
        private var nextEpisodeId = 0L
        private var armed = true
        private var latestFailureAt: Long? = null
        private var latestSuccessAt: Long? = null
        private var latestSuccessStart: Long? = null

        init {
            if (continuity != null) {
                episode = continuity.episode
                acknowledgedAt = continuity.acknowledgedAt
                nextEpisodeId = continuity.nextEpisodeId
                pauseUntil = continuity.pauseUntil
                presentationPending = continuity.unacknowledgedPresentation
            }
        }

        fun continuity(now: Long): Continuity =
            Continuity(
                episode = episode,
                acknowledgedAt = acknowledgedAt,
                nextEpisodeId = nextEpisodeId,
                pauseUntil = pauseUntil?.takeIf { it > now },
                unacknowledgedPresentation = acknowledgedAt == null && (isPresented || presentationPending),
            )

        fun handle(
            event: Event,
            now: Long,
        ): List<Command> {
            if (retired) return emptyList()
            return when (event) {
                is Event.Observation -> {
                    receive(event.observation, now)
                }

                is Event.OperationCompleted -> {
                    complete(event.operation, event.failed, event.observations, now)
                }

                Event.ClassificationRequested -> {
                    startProbe(now)
                }

                is Event.GraceExpired -> {
                    expireGrace(event.id, now)
                }

                is Event.ProbeCompleted -> {
                    completeProbe(event.id, event.result, now)
                }

                Event.FallbackTick -> {
                    armed = true
                    val success = latestSuccessAt
                    if (success != null && availability == Availability.HEALTHY &&
                        now >= success && now - success < FALLBACK_NANOS
                    ) {
                        emptyList()
                    } else {
                        startProbe(now)
                    }
                }

                is Event.PathChanged -> {
                    changePath(event.path, now)
                }

                is Event.TorReadinessChanged -> {
                    changeTorReadiness(event.ready, now)
                }

                is Event.PresentationGateChanged -> {
                    presentationAllowed = event.allowed
                    presentIfAllowed(now)
                }

                Event.Acknowledged -> {
                    acknowledge(now)
                }

                Event.OwnershipRetired -> {
                    retire()
                }
            }
        }

        private fun acknowledge(now: Long): List<Command> {
            if (episode == null) return emptyList()
            acknowledgedAt = now
            pendingPresentation = null
            presentationPending = false
            val commands = if (isPresented) listOf(Command.DismissOutage) else emptyList()
            isPresented = false
            return commands
        }

        private fun retire(): List<Command> {
            val dismiss = if (isPresented) listOf(Command.DismissOutage) else emptyList()
            val commands = stopInvestigation(disarm = true) + dismiss
            retired = true
            isPresented = false
            pendingPresentation = null
            presentationPending = false
            return commands
        }

        private fun complete(
            operation: OhttpHealthOperation,
            failed: Boolean,
            observations: List<OhttpHealthObservation>,
            now: Long,
        ): List<Command> {
            if (operation.startedAtNanos < ownershipStartedAt) return emptyList()
            val seen = operation.id in completedOperations
            completedOperations = completedOperations + operation.id
            if (seen) return emptyList()
            val failures = attributedFailures(observations, operation)
            if (!failed || failures.isEmpty()) return emptyList()
            val acknowledged = acknowledgedAt
            if (acknowledged != null && operation.startedAtNanos <= acknowledged) return emptyList()
            armed = true
            pendingPresentation = operation
            val commands = mutableListOf<Command>()
            for (failure in failures) commands += receive(failure, now)
            if (episode != null) {
                presentationPending = true
                commands += if (availability == Availability.UNKNOWN) startProbe(now) else presentIfAllowed(now)
            } else if (investigation == null) {
                val first = failures.minBy { it.observedAtNanos }
                commands += suspect(first.observedAtNanos, now)
            }
            return commands
        }

        private fun expireGrace(
            id: Long,
            now: Long,
        ): List<Command> {
            val deadline = graceDeadline
            if (investigationId != id || investigation != Investigation.GRACE || deadline == null || now < deadline) {
                return emptyList()
            }
            if (!mayDiagnose(now)) return stopInvestigation(disarm = true)
            graceDeadline = null
            investigation = Investigation.CONFIRMATION
            return listOf(Command.Probe(id))
        }

        private fun completeProbe(
            id: Long,
            result: ProbeResult,
            now: Long,
        ): List<Command> {
            if (investigationId != id || investigation == Investigation.GRACE ||
                result.generation != generation || result.route != route
            ) {
                return emptyList()
            }
            val confirming = investigation == Investigation.CONFIRMATION
            val failureAnchor = investigationFailureAt
            investigation = null
            investigationId = null
            investigationFailureAt = null
            if (result.startedAtNanos < ownershipStartedAt) return becomeUnknown()
            return when (val outcome = result.outcome) {
                OhttpHealthOutcome.Usable -> {
                    val failure = latestFailureAt
                    if (failure != null && result.startedAtNanos <= failure) {
                        becomeUnknown()
                    } else {
                        recover(result.startedAtNanos, result.observedAtNanos)
                    }
                }

                OhttpHealthOutcome.TransportUnavailable -> {
                    probeFailure(result, confirming, failureAnchor, now)
                }

                is OhttpHealthOutcome.RateLimited -> {
                    pauseUntil = maxOf(pauseUntil ?: 0L, outcome.retryNotBeforeNanos)
                    becomeUnknown()
                }

                else -> {
                    becomeUnknown()
                }
            }
        }

        private fun probeFailure(
            result: ProbeResult,
            confirming: Boolean,
            failureAnchor: Long?,
            now: Long,
        ): List<Command> {
            if (!isApplicableFailure(result.observedAtNanos)) return becomeUnknown()
            if (confirming || episode != null) {
                // Concurrent failures cannot slide this investigation's anchor or defer outage confirmation.
                if (failureAnchor == null || result.startedAtNanos <= failureAnchor) return becomeUnknown()
                latestFailureAt = maxOf(latestFailureAt ?: 0L, result.observedAtNanos)
                availability = Availability.UNAVAILABLE
                if (episode == null) {
                    nextEpisodeId += 1
                    episode = Episode(nextEpisodeId, result.observedAtNanos)
                    presentationPending = true
                }
                return presentIfAllowed(now)
            }
            return suspect(result.observedAtNanos, now)
        }

        private fun changePath(
            path: Path,
            now: Long,
        ): List<Command> {
            val reconnect = this.path == Path.UNAVAILABLE && path != Path.UNAVAILABLE
            this.path = path
            if (path == Path.UNAVAILABLE) {
                val unknown = becomeUnknown()
                return unknown + stopInvestigation(disarm = true)
            }
            if (reconnect) {
                armed = true
                return startProbe(now)
            }
            return emptyList()
        }

        private fun changeTorReadiness(
            ready: Boolean,
            now: Long,
        ): List<Command> {
            val reconnect = route.viaTor && !torReady && ready
            torReady = ready
            if (route.viaTor && !ready) {
                val unknown = becomeUnknown()
                return unknown + stopInvestigation(disarm = true)
            }
            if (reconnect) {
                armed = true
                return startProbe(now)
            }
            return emptyList()
        }

        /** The selected attempt supplies causality. Never guess which of several requests decided its result. */
        private fun attributedFailures(
            observations: List<OhttpHealthObservation>,
            operation: OhttpHealthOperation,
        ): List<OhttpHealthObservation> {
            val owned = observations.filter { isCurrent(it) && !it.historical && it.operation == operation }
            val terminal =
                owned
                    .groupBy { RequestIdentity(it.source, it.requestId) }
                    .values
                    .map { entries -> entries.maxBy { it.version } }
            if (terminal.isEmpty() || !terminal.all { it.outcome == OhttpHealthOutcome.TransportUnavailable }) {
                return emptyList()
            }
            return terminal
                .filter { isApplicableFailure(it.observedAtNanos) }
                .sortedWith(
                    Comparator { lhs, rhs ->
                        when {
                            lhs.observedAtNanos != rhs.observedAtNanos -> {
                                lhs.observedAtNanos.compareTo(rhs.observedAtNanos)
                            }

                            lhs.source != rhs.source -> {
                                if (lhs.source == OhttpHealthSource.NATIVE) -1 else 1
                            }

                            else -> {
                                lhs.requestId.compareTo(rhs.requestId)
                            }
                        }
                    }
                )
        }

        private fun isCurrent(observation: OhttpHealthObservation) = observation.route == route

        private fun mayDiagnose(now: Long): Boolean {
            val pause = pauseUntil
            return path != Path.UNAVAILABLE && (!route.viaTor || torReady) && (pause == null || now >= pause)
        }

        /** Versions order only matching retained categories, never another source or outcome. */
        private fun retain(observation: OhttpHealthObservation): Boolean {
            val index =
                retainedEvidence.indexOfFirst {
                    it.source == observation.source && it.scope == observation.scope &&
                        it.outcome.category == observation.outcome.category
                }
            if (index < 0) {
                retainedEvidence = retainedEvidence + observation
                return true
            }
            val previous = retainedEvidence[index]
            val newer =
                if (observation.purpose != OhttpHealthPurpose.DIAGNOSTIC &&
                    observation.scope == OhttpHealthScope.ROUTE && observation.outcome == OhttpHealthOutcome.Usable
                ) {
                    compareValuesBy(
                        observation,
                        previous,
                        { it.startedAtNanos },
                        { it.observedAtNanos },
                        { it.version },
                    ) > 0
                } else {
                    previous.version < observation.version
                }
            if (!newer) return false
            retainedEvidence = retainedEvidence.toMutableList().also { it[index] = observation }
            return true
        }

        private fun receive(
            observation: OhttpHealthObservation,
            now: Long,
        ): List<Command> {
            if (observation.route != route || !(observation.historical || isCurrent(observation))) return emptyList()
            // A retained pause remains binding after subscription replacement, even without an original owner.
            val outcome = observation.outcome
            if (outcome is OhttpHealthOutcome.RateLimited && outcome.retryNotBeforeNanos > now) {
                pauseUntil = maxOf(pauseUntil ?: 0L, outcome.retryNotBeforeNanos)
                retain(observation)
                val stopped = stopInvestigation(disarm = true)
                return stopped + becomeUnknown()
            }
            if (observation.historical || observation.startedAtNanos < ownershipStartedAt ||
                observation.purpose == OhttpHealthPurpose.DIAGNOSTIC || !retain(observation)
            ) {
                return emptyList()
            }
            return when {
                outcome == OhttpHealthOutcome.Usable && observation.scope == OhttpHealthScope.ROUTE -> {
                    recover(observation.startedAtNanos, observation.observedAtNanos)
                }

                outcome == OhttpHealthOutcome.TransportUnavailable -> {
                    suspect(observation.observedAtNanos, now)
                }

                else -> {
                    emptyList()
                }
            }
        }

        private fun isApplicableFailure(time: Long): Boolean = latestSuccessStart.let { it == null || time >= it }

        private fun suspect(
            time: Long,
            now: Long,
        ): List<Command> {
            if (!isApplicableFailure(time)) return emptyList()
            latestFailureAt = maxOf(latestFailureAt ?: 0L, time)
            if (episode != null) return emptyList()
            var commands: List<Command> = emptyList()
            // The first real failure owns the grace even if a generic-error classification is still running.
            if (investigation == Investigation.CLASSIFICATION) commands = stopInvestigation(disarm = false)
            if (!armed || investigation != null || !mayDiagnose(now)) return commands
            availability = Availability.SUSPECTED
            nextId += 1
            investigationId = nextId
            investigation = Investigation.GRACE
            investigationFailureAt = time
            val deadline = if (time > Long.MAX_VALUE - GRACE_NANOS) Long.MAX_VALUE else time + GRACE_NANOS
            graceDeadline = deadline
            return commands + Command.ScheduleGrace(nextId, deadline)
        }

        private fun recover(
            start: Long,
            end: Long,
        ): List<Command> {
            latestFailureAt?.let { if (start <= it) return emptyList() }
            latestSuccessStart?.let { if (start < it) return emptyList() }
            val dismiss = if (isPresented) listOf(Command.DismissOutage) else emptyList()
            val commands = stopInvestigation(disarm = false) + dismiss
            latestSuccessAt = end
            latestSuccessStart = start
            availability = Availability.HEALTHY
            armed = true
            episode = null
            acknowledgedAt = null
            pendingPresentation = null
            presentationPending = false
            isPresented = false
            return commands
        }

        private fun startProbe(now: Long): List<Command> {
            if (!armed || investigation != null || !mayDiagnose(now)) return emptyList()
            nextId += 1
            investigationId = nextId
            investigation = if (episode == null) Investigation.CLASSIFICATION else Investigation.RECHECK
            investigationFailureAt = latestFailureAt ?: if (episode == null) null else ownershipStartedAt
            return listOf(Command.Probe(nextId))
        }

        private fun stopInvestigation(disarm: Boolean): List<Command> {
            val commands =
                when (investigation) {
                    Investigation.GRACE -> listOf(Command.CancelGrace)
                    null -> emptyList()
                    else -> listOf(Command.CancelProbe)
                }
            investigation = null
            investigationId = null
            investigationFailureAt = null
            graceDeadline = null
            if (disarm) armed = false
            return commands
        }

        private fun presentIfAllowed(now: Long): List<Command> {
            val current = episode
            if (availability != Availability.UNAVAILABLE || !presentationPending || isPresented ||
                !presentationAllowed || !mayDiagnose(now) || current == null
            ) {
                return emptyList()
            }
            isPresented = true
            presentationPending = false
            return listOf(Command.Present(current, pendingPresentation))
        }

        private fun becomeUnknown(): List<Command> {
            availability = Availability.UNKNOWN
            armed = false
            return suspendPresentation()
        }

        private fun suspendPresentation(): List<Command> {
            if (!isPresented) return emptyList()
            isPresented = false
            presentationPending = true
            return listOf(Command.DismissOutage)
        }
    }
}
