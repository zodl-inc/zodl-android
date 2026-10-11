package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import android.os.SystemClock
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.FastestServersResult
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.resolveIsEndpointCustom
import co.electriccoin.zcash.ui.common.datasource.resolveIsServerSelectionAutomatic
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.model.ServerFallbackPolicy
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.rawWalletBalances
import co.electriccoin.zcash.ui.common.provider.retainWhileWalletExists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

interface AutomaticServerRepository {
    val isServerAutomatic: Flow<Boolean>

    val ohttpUnavailable: Flow<Boolean>

    suspend fun isServerAutomatic(): Boolean

    suspend fun isServerCustom(): Boolean

    fun init()
}

@Suppress("LongParameterList", "TooManyFunctions")
class AutomaticServerRepositoryImpl(
    private val application: Application,
    private val walletRepository: WalletRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val ledgerProposalPipeline: LedgerProposalPipeline,
    private val applicationStateProvider: ApplicationStateProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val isOhttpFallbackActiveProvider: IsOhttpFallbackActiveProvider,
    private val evaluationInterval: EvaluationInterval,
) : AutomaticServerRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _ohttpUnavailable = MutableStateFlow(false)

    /**
     * Consecutive gateway failures count. Reset to 0 when a gateway is healthy or when automatic mode
     * changes. Prevents a single transient failure (e.g., Tor circuits timing out) from immediately
     * triggering a fallback to clearnet.
     */
    @Volatile
    private var consecutiveGatewayFailures = 0

    private val isAppInTransactionState: Boolean
        get() =
            zashiProposalRepository.transactionProposal.value != null ||
                zashiProposalRepository.submitState.value != null ||
                keystoneProposalRepository.transactionProposal.value != null ||
                keystoneProposalRepository.submitState.value != null ||
                ledgerProposalPipeline.transactionProposal.value != null ||
                ledgerProposalPipeline.submitState.value != null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val walletBalances =
        synchronizerProvider
            .rawWalletBalances()
            .retainWhileWalletExists(persistableWalletProvider)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val isServerAutomatic: Flow<Boolean> =
        isServerSelectionAutomaticProvider
            .observe()
            .distinctUntilChanged()
            .flatMapLatest { isAutomatic ->
                if (isAutomatic != null) {
                    flowOf(isAutomatic)
                } else {
                    persistableWalletProvider.persistableWallet
                        .mapNotNull { it?.endpoint }
                        .map { endpoint ->
                            resolveIsServerSelectionAutomatic(
                                isAutomaticPreference = null,
                                currentEndpoint = endpoint,
                                knownEndpoints = lightWalletEndpointProvider.getEndpoints(),
                            )
                        }
                }
            }.distinctUntilChanged()

    /**
     * The "gateway unavailable, pick a server" signal only exists in automatic mode. It is gated here, at
     * the source, so an evaluation still in flight when the user leaves automatic mode cannot re-raise it.
     */
    override val ohttpUnavailable: Flow<Boolean> =
        combine(_ohttpUnavailable, isServerAutomatic) { unavailable, automatic -> unavailable && automatic }
            .distinctUntilChanged()

    override suspend fun isServerAutomatic(): Boolean {
        val preference = isServerSelectionAutomaticProvider.get()
        // Only the wallet read is guarded; resolving a custom endpoint is needed solely when the
        // preference was never written. getEndpoints() is an in-memory list, so it stays unguarded.
        val currentEndpoint =
            if (preference == null) persistableWalletProvider.getPersistableWallet()?.endpoint else null
        return resolveIsServerSelectionAutomatic(
            isAutomaticPreference = preference,
            currentEndpoint = currentEndpoint,
            knownEndpoints = lightWalletEndpointProvider.getEndpoints(),
        )
    }

    // Manual mode alone doesn't mean the endpoint is custom, since manual mode also covers pinning
    // one of our own bundled servers — see resolveIsEndpointCustom.
    override suspend fun isServerCustom(): Boolean {
        if (isServerAutomatic()) return false
        val endpoint = persistableWalletProvider.getPersistableWallet()?.endpoint
        return endpoint != null && resolveIsEndpointCustom(endpoint, lightWalletEndpointProvider.getEndpoints())
    }

    override fun init() {
        switchLane(
            merge(
                applicationStateProvider.observeOnForeground().map { SwitchTrigger.FOREGROUND },
                connectivityEdges(),
                healthTicks(),
            ),
        ).launchIn(scope)

        clearUnavailableOnManualMode().launchIn(scope)
    }

    /**
     * Drops a recorded gateway outage as soon as automatic mode is left. [ohttpUnavailable] already hides
     * it while manual, but without this a stale `true` would bring the banner straight back when the user
     * re-enters automatic mode, before any evaluation has run.
     */
    internal fun clearUnavailableOnManualMode(): Flow<Boolean> =
        isServerAutomatic.onEach { automatic ->
            if (!automatic) {
                _ohttpUnavailable.value = false
            }
        }

    /**
     * The automatic-selection lane, OHTTP first (see [ServerFallbackPolicy]). Every foreground edge
     * re-evaluates (rate limited by [evaluationInterval]); a connectivity edge - a synchronizer error or a
     * sustained DISCONNECTED status - re-evaluates at once so the fallback to a clearnet server is not
     * held back by the rate limit; and while a fallback is active a periodic tick checks whether the OHTTP
     * gateway is back. A newer trigger cancels an in-flight evaluation. A switch candidate is applied only
     * once the first local balance snapshot exists: a different endpoint rebuilds the Synchronizer, and UI
     * consumers retain that snapshot through the replacement.
     *
     * Nothing here may throw out of the flow, the upstream signals included. This lane is launched once
     * for the lifetime of the process on a scope with no exception handler, so a single failure - an
     * unreachable server, a prefs read, a rejected argument - would otherwise take automatic selection
     * down until the next process start, and reach the thread's uncaught handler on its way out.
     */
    internal fun observeSwitchCandidates(
        edges: Flow<Unit>,
        trigger: SwitchTrigger = SwitchTrigger.FOREGROUND,
    ): Flow<LightWalletEndpoint> = switchLane(edges.map { trigger })

    /**
     * Internal test hook: evaluates as a connectivity edge, i.e. an outage the synchronizer already
     * reported, so a single evaluation may leave the gateway (foreground/health need confirmation).
     */
    internal suspend fun evaluateServerSwitch(): LightWalletEndpoint? =
        evaluateServerSwitch(trigger = SwitchTrigger.CONNECTIVITY)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun switchLane(triggers: Flow<SwitchTrigger>): Flow<LightWalletEndpoint> =
        triggers
            .catch { Twig.error(it) { "Automatic server: a trigger signal failed" } }
            .filter { it.bypassesInterval || evaluationInterval.hasElapsed() }
            .mapLatest { trigger ->
                guarded("evaluate a server switch") { resolveSwitchCandidate(trigger) }
            }.filterNotNull()
            .onEach { candidate ->
                guarded("switch to ${candidate.host}:${candidate.port}") { applyServerSwitch(candidate) }
            }

    /** A synchronizer error, or a DISCONNECTED status that lasts [DISCONNECTED_GRACE]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun connectivityEdges(): Flow<SwitchTrigger> =
        merge(
            synchronizerProvider.error.filterNotNull().map { SwitchTrigger.CONNECTIVITY },
            synchronizerProvider.synchronizer
                .flatMapLatest { it?.status ?: emptyFlow() }
                .mapLatest { status ->
                    // mapLatest cancels the delay when the status changes, so only a status that STAYS
                    // disconnected for the grace period fires.
                    if (status == Synchronizer.Status.DISCONNECTED) {
                        delay(DISCONNECTED_GRACE)
                        SwitchTrigger.CONNECTIVITY
                    } else {
                        null
                    }
                }.filterNotNull(),
        )

    /**
     * Periodic health check while Automatic mode is on. On the gateway it runs every [GATEWAY_HEALTH_INTERVAL]:
     * an already-synced wallet whose gateway dies never surfaces an error to Kotlin - the sync engine treats
     * the relay/gateway 502 as transient and retries forever (seen 2026-09-14 with Tor on) - so the only
     * reliable "OHTTP is down" signal is probing the gateway ourselves. While on a clearnet fallback it runs
     * every [FALLBACK_RECOVERY_INTERVAL] to bring the wallet back to OHTTP as soon as the gateway answers.
     *
     * The gateway health ticks run only while the app is in the foreground: they stop in the background
     * and start over (a full [GATEWAY_HEALTH_INTERVAL] after the foreground edge, which itself triggers an
     * evaluation) when the app returns.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun healthTicks(): Flow<SwitchTrigger> =
        isServerAutomatic
            .flatMapLatest { automatic ->
                if (!automatic) {
                    emptyFlow()
                } else {
                    isOhttpFallbackActiveProvider
                        .observe()
                        .distinctUntilChanged()
                        .flatMapLatest { fallbackActive ->
                            if (fallbackActive) {
                                ticks(FALLBACK_RECOVERY_INTERVAL, SwitchTrigger.RECOVERY)
                            } else {
                                applicationStateProvider
                                    .isInForeground
                                    .distinctUntilChanged()
                                    .flatMapLatest { foreground ->
                                        if (foreground) {
                                            ticks(GATEWAY_HEALTH_INTERVAL, SwitchTrigger.HEALTH)
                                        } else {
                                            emptyFlow()
                                        }
                                    }
                            }
                        }
                }
            }

    private fun ticks(
        interval: Duration,
        trigger: SwitchTrigger,
    ): Flow<SwitchTrigger> =
        flow {
            while (true) {
                delay(interval)
                emit(trigger)
            }
        }

    private suspend fun <T> guarded(
        what: String,
        block: suspend () -> T,
    ): T? =
        runCatching { block() }
            .getOrElse {
                if (it is CancellationException) throw it
                Twig.error(it) { "Automatic server: failed to $what" }
                null
            }

    /**
     * Waits for the first local balance snapshot before releasing a candidate, but not indefinitely. In the
     * unhealthy case the wallet's current server has already failed two evaluations and is plausibly the
     * reason no snapshot exists, so the failover that matters most cannot be gated forever on the sync it
     * is meant to repair.
     */
    internal suspend fun resolveSwitchCandidate(trigger: SwitchTrigger): LightWalletEndpoint? {
        val candidate = evaluateServerSwitch(trigger) ?: return null
        val balances = withTimeoutOrNull(BALANCE_SNAPSHOT_TIMEOUT) { walletBalances.filterNotNull().first() }
        if (balances == null) {
            Twig.warn {
                "Automatic server: no local balance snapshot within $BALANCE_SNAPSHOT_TIMEOUT, applying the " +
                    "switch to ${candidate.host}:${candidate.port} anyway"
            }
        } else {
            Twig.info { "Automatic server: local balance snapshot present, applying the switch" }
        }
        return candidate
    }

    /**
     * OHTTP first ([ServerFallbackPolicy]): the OHTTP gateway tier is validated with the SDK's own
     * fastest-server benchmark (Tor-aware through `ServiceMode`); while any gateway answers it is the
     * server, and the clearnet tier is not even probed - so no clearnet host sees the device while OHTTP
     * works, and speed is never a reason to leave OHTTP. Only when no gateway answers is the clearnet tier
     * benchmarked and its best validated server returned; nothing reachable at all means "stay".
     *
     * Consecutive failure confirmation: when the current server is a gateway and tier 0 is empty, a
     * CONNECTIVITY trigger (synchronizer error) falls back immediately, but FOREGROUND/HEALTH triggers
     * wait for a second consecutive failure to avoid leaving OHTTP on transient Tor circuit timeouts.
     *
     * A HEALTH trigger on the gateway skips the benchmark and only probes the current gateway
     * ([probeGateway]); every other trigger ranks as described above.
     */
    @Suppress("ReturnCount", "ComplexMethod", "NestedBlockDepth")
    internal suspend fun evaluateServerSwitch(trigger: SwitchTrigger): LightWalletEndpoint? {
        if (!isServerAutomatic()) {
            _ohttpUnavailable.value = false
            return null
        }
        if (isAppInTransactionState) return null
        val current = persistableWalletProvider.getPersistableWallet()?.endpoint ?: return null
        val gateways = lightWalletEndpointProvider.getEndpoints().filter(lightWalletEndpointProvider::isOhttpGateway)
        if (gateways.isEmpty()) return null
        val synchronizer = synchronizerProvider.getSynchronizerOrNull() ?: return null
        return try {
            val onGateway = lightWalletEndpointProvider.isOhttpGateway(current)
            val healthyGateways =
                if (trigger == SwitchTrigger.HEALTH && onGateway) {
                    // Routine health tick on the gateway: only prove the gateway still answers. The full
                    // benchmark (latency + block fetch per server) is for deciding between servers.
                    listOf(current).filter { probeGateway(synchronizer, it) }
                } else {
                    rankValidated(synchronizer, gateways)
                }

            // Reset consecutive failures when a gateway is healthy, when on a fallback (recovering back
            // to OHTTP), or when switching to automatic mode (implied by being on a non-gateway).
            if (healthyGateways.isNotEmpty() || !onGateway) {
                consecutiveGatewayFailures = 0
            }

            // When on a gateway and it's unreachable, require confirmation before reporting unavailability,
            // unless the trigger is CONNECTIVITY (real outage already surfaced by the synchronizer).
            val needsConfirmation = onGateway && healthyGateways.isEmpty()
            if (needsConfirmation && trigger != SwitchTrigger.CONNECTIVITY) {
                consecutiveGatewayFailures++
                if (consecutiveGatewayFailures < 2) {
                    Twig.info {
                        "Automatic server: gateway unreachable once (trigger=$trigger), waiting for confirmation"
                    }
                    syncFallbackFlag(current)
                    return null
                }
                Twig.info {
                    "Automatic server: gateway unavailability confirmed after 2 consecutive failures"
                }
                consecutiveGatewayFailures = 0
            } else if (needsConfirmation) {
                consecutiveGatewayFailures = 0
            }

            val decision =
                ServerFallbackPolicy.decide(
                    current = current,
                    healthyGateways = healthyGateways,
                    isOhttpGateway = lightWalletEndpointProvider::isOhttpGateway,
                )

            when (decision) {
                is ServerFallbackPolicy.DecisionResult.ReturnToGateway -> {
                    _ohttpUnavailable.value = false
                    Twig.info {
                        "Automatic server: OHTTP-first current=${current.host} " +
                            "gatewayReachable=${healthyGateways.isNotEmpty()} -> ${decision.endpoint.host}"
                    }
                    syncFallbackFlag(decision.endpoint)
                    decision.endpoint
                }

                is ServerFallbackPolicy.DecisionResult.StayAndReportGatewayUnavailable -> {
                    _ohttpUnavailable.value = true
                    Twig.info {
                        "Automatic server: OHTTP-first current=${current.host} " +
                            "gatewayReachable=false -> stay (gateway unavailable, awaiting manual server selection)"
                    }
                    syncFallbackFlag(current)
                    null
                }

                is ServerFallbackPolicy.DecisionResult.Stay -> {
                    // Gateway reachable and already on it, or on manual clearnet fallback with gateway still down
                    if (healthyGateways.isNotEmpty()) {
                        _ohttpUnavailable.value = false
                    }
                    Twig.info {
                        "Automatic server: OHTTP-first current=${current.host} " +
                            "gatewayReachable=${healthyGateways.isNotEmpty()} -> stay"
                    }
                    syncFallbackFlag(current)
                    null
                }
            }
        } finally {
            evaluationInterval.markAttempted()
        }
    }

    /**
     * Validated servers, fastest first, or empty when none of [candidates] answers. The SDK's fastest-server
     * benchmark (latency + a timed block fetch, Tor-aware) ranks them; when it validates nothing - its
     * fetch thresholds are tight and a slow Tor circuit can miss them even though the server is fine -
     * fall back to plain validation (`GetLightdInfo`, also Tor-aware) in the configured order, so an
     * outage never leaves the wallet on a dead gateway just because the ranking was too strict.
     */
    private suspend fun rankValidated(
        synchronizer: Synchronizer,
        candidates: List<LightWalletEndpoint>,
    ): List<LightWalletEndpoint> {
        val ranked =
            if (candidates.isEmpty()) {
                emptyList()
            } else {
                withTimeoutOrNull(TIER_BENCHMARK_TIMEOUT) {
                    synchronizer
                        .getFastestServers(candidates)
                        .filterIsInstance<FastestServersResult.Done>()
                        .first()
                        .servers
                }.orEmpty()
            }
        return ranked.ifEmpty {
            candidates.filter { candidate ->
                withTimeoutOrNull(VALIDATION_TIMEOUT) {
                    runCatching { synchronizer.validateServerEndpoint(application, candidate) }.getOrNull()
                } == ServerValidation.Valid
            }
        }
    }

    /**
     * Lightweight liveness probe of a single gateway: plain validation (`GetLightdInfo`, Tor-aware), the
     * cheapest SDK call that proves the gateway answers, capped by [VALIDATION_TIMEOUT].
     */
    private suspend fun probeGateway(
        synchronizer: Synchronizer,
        gateway: LightWalletEndpoint,
    ): Boolean =
        withTimeoutOrNull(VALIDATION_TIMEOUT) {
            runCatching { synchronizer.validateServerEndpoint(application, gateway) }.getOrNull()
        } == ServerValidation.Valid

    private suspend fun syncFallbackFlag(endpoint: LightWalletEndpoint) {
        val active = ServerFallbackPolicy.isFallback(endpoint, lightWalletEndpointProvider::isOhttpGateway)
        if (isOhttpFallbackActiveProvider.get() != active) isOhttpFallbackActiveProvider.store(active)
    }

    /**
     * The SDK is told about the switch only once it has actually been applied: a declined or failed switch
     * that confirmed anyway would clear the SDK's consecutive-failure count and start its cooldown, leaving
     * a wallet on a dead server to earn its way out from scratch.
     *
     * The Synchronizer is taken before the endpoint is written, because writing it tears the current
     * Synchronizer down and rebuilds it. Which instance receives the confirmation does not matter - the
     * hysteresis state it updates is process-wide and outlives any single Synchronizer.
     */
    private suspend fun applyServerSwitch(candidate: LightWalletEndpoint) {
        if (!isServerAutomatic() || isAppInTransactionState) return
        if (candidate !in lightWalletEndpointProvider.getEndpoints()) return
        val synchronizer: Synchronizer? = synchronizerProvider.getSynchronizerOrNull()
        Twig.info { "Automatic server: switching to ${candidate.host}:${candidate.port}" }
        walletRepository.updateWalletEndpoint(candidate)
        syncFallbackFlag(candidate)
        synchronizer?.confirmServerSwitch(candidate)
    }
}

/** What woke the automatic-selection lane; connectivity edges skip the foreground rate limit. */
enum class SwitchTrigger(
    val bypassesInterval: Boolean,
) {
    FOREGROUND(bypassesInterval = false),
    CONNECTIVITY(bypassesInterval = true),
    HEALTH(bypassesInterval = true),
    RECOVERY(bypassesInterval = true),
}

/** How often, while on the OHTTP gateway, to confirm it still answers. */
private val GATEWAY_HEALTH_INTERVAL = 2.minutes

/** Cap on one plain validation of a fallback candidate. */
private val VALIDATION_TIMEOUT = 20.seconds

/** How long a DISCONNECTED status must last before it counts as a connectivity edge. */
private val DISCONNECTED_GRACE = 20.seconds

/** How often, while on a clearnet fallback, to check whether the OHTTP gateway is back. */
private val FALLBACK_RECOVERY_INTERVAL = 5.minutes

/** Cap on validating one tier (the SDK benchmark itself times out per server well before this). */
private val TIER_BENCHMARK_TIMEOUT = 90.seconds

/**
 * Rate limit for the automatic-selection lane. `observeOnForeground()` fires on every foreground edge and a
 * full evaluation opens a connection to every bundled host, so an app the user keeps switching in and out
 * of would otherwise re-benchmark the whole list each time, for a decision that is almost always "stay".
 *
 * What is marked is the attempt, not the success. An evaluation that throws or is cancelled by the next
 * foreground edge did the network work all the same, and a user toggling foreground faster than an
 * evaluation completes would otherwise never let one finish and never be rate limited at all.
 */
class EvaluationInterval(
    private val minimumInterval: Duration,
    private val timeSource: TimeSource = ElapsedRealtimeTimeSource,
) {
    @Volatile
    private var lastAttemptAt: TimeMark? = null

    fun hasElapsed(): Boolean = lastAttemptAt?.let { it.elapsedNow() >= minimumInterval } ?: true

    fun markAttempted() {
        lastAttemptAt = timeSource.markNow()
    }
}

/**
 * `TimeSource.Monotonic` is `System.nanoTime()`, which does not advance while the device is in deep sleep:
 * a phone asleep overnight accrues almost no elapsed time, so the morning foreground edge would still be
 * inside the evaluation interval and automatic selection would sit out the whole morning.
 * `SystemClock.elapsedRealtime()` counts sleep, which is what this wall-clock rate limit needs.
 */
object ElapsedRealtimeTimeSource : TimeSource {
    override fun markNow(): TimeMark = ElapsedRealtimeMark(SystemClock.elapsedRealtime())
}

private class ElapsedRealtimeMark(
    private val startedAt: Long,
) : TimeMark {
    override fun elapsedNow(): Duration = (SystemClock.elapsedRealtime() - startedAt).milliseconds
}

internal val MINIMUM_EVALUATION_INTERVAL = 10.minutes

/**
 * Cap on the wait for the first local balance snapshot before a switch is applied.
 */
private val BALANCE_SNAPSHOT_TIMEOUT = 30.seconds
