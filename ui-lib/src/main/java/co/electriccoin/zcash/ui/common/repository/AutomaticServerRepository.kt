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
import co.electriccoin.zcash.ui.common.privateconnection.NoPrivateConnectionHealth
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealthOwner
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionTarget
import co.electriccoin.zcash.ui.common.privateconnection.RequestHealthDetection
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsPublicConnectionProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.NetworkConnectivityProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionMutex
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
import kotlinx.coroutines.flow.emitAll
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

    /** True while a send, shield or signing flow is in progress; the wallet's server must not change then. */
    val isTransactionInProgress: Flow<Boolean>

    suspend fun isServerAutomatic(): Boolean

    suspend fun isServerCustom(): Boolean

    /** The user chose "Keep waiting" on, or dismissed, the private-connection-unavailable prompt. */
    fun acknowledgeOhttpUnavailable()

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
    private val isPublicConnectionProvider: IsPublicConnectionProvider,
    private val networkConnectivityProvider: NetworkConnectivityProvider,
    private val serverSelectionMutex: ServerSelectionMutex,
    private val evaluationInterval: EvaluationInterval,
    private val privateConnectionHealth: PrivateConnectionHealthOwner = NoPrivateConnectionHealth,
    private val requestHealthDetection: RequestHealthDetection = RequestHealthDetection { false },
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider? = null,
) : AutomaticServerRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _ohttpUnavailable = MutableStateFlow(false)

    /**
     * Bumped each time automatic mode is left. An evaluation reports the gateway unavailable only if no
     * manual-mode clear happened since it started, so a slow evaluation cannot re-raise the banner.
     */
    private val manualModeClears = AtomicLong(0)

    /**
     * Consecutive gateway failures count. Reset to 0 when a gateway is healthy or when automatic mode
     * changes. Prevents a single transient failure (e.g., Tor circuits timing out) from immediately
     * reporting the gateway unavailable.
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

    override val isTransactionInProgress: Flow<Boolean> =
        combine(
            listOf<Flow<Any?>>(
                zashiProposalRepository.transactionProposal,
                zashiProposalRepository.submitState,
                keystoneProposalRepository.transactionProposal,
                keystoneProposalRepository.submitState,
                ledgerProposalPipeline.transactionProposal,
                ledgerProposalPipeline.submitState,
            )
        ) { states -> states.any { it != null } }.distinctUntilChanged()

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
        combine(
            _ohttpUnavailable,
            privateConnectionHealth.outage.map { it != null }.onStart { emit(false) },
            isServerAutomatic,
        ) { legacy, confirmed, automatic -> (legacy || confirmed) && automatic }
            .distinctUntilChanged()

    override fun acknowledgeOhttpUnavailable() = privateConnectionHealth.acknowledge()

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

        if (requestHealthDetection.isEnabled()) {
            watchPrivateConnection().launchIn(scope)
            genericErrors().onEach { privateConnectionHealth.onGenericError() }.launchIn(scope)
        }
    }

    /** What the request-health detection should do about the Private route. */
    private sealed interface PrivateWatch {
        data class Own(
            val target: PrivateConnectionTarget,
        ) : PrivateWatch

        /** The route still qualifies but the app is in the background: stop for now, remember what is known. */
        data object Pause : PrivateWatch

        /** The route does not qualify (Public, a manual server, no gateway): stop for good. */
        data object Stop : PrivateWatch
    }

    /**
     * The Private route the request-health detection watches: the gateway the wallet is on, while Automatic is on
     * in Private mode. In the foreground it is watched; in the background the detection pauses and keeps its
     * state; anything else (Public, a manual server, no gateway) stops it and clears a shown outage.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("MagicNumber")
    internal fun watchPrivateConnection(): Flow<Unit> =
        combine(
            isServerAutomatic,
            isPublicConnectionProvider.observe(),
            applicationStateProvider.isInForeground,
            persistableWalletProvider.persistableWallet.map { it?.endpoint },
            (isTorEnabledStorageProvider?.observe() ?: flowOf(null)).map { it == true },
            synchronizerProvider.synchronizer,
        ) { values ->
            val automatic = values[0] as Boolean
            val isPublic = values[1] as Boolean
            val foreground = values[2] as Boolean
            val endpoint = values[3] as LightWalletEndpoint?
            val viaTor = values[4] as Boolean
            val hasSynchronizer = values[5] != null
            when {
                !automatic || isPublic || !hasSynchronizer || endpoint == null ||
                    !lightWalletEndpointProvider.isOhttpGateway(endpoint) -> PrivateWatch.Stop

                !foreground -> PrivateWatch.Pause

                else -> PrivateWatch.Own(PrivateConnectionTarget(endpoint, viaTor))
            }
        }.distinctUntilChanged()
            .mapLatest { watch ->
                when (watch) {
                    is PrivateWatch.Own -> privateConnectionHealth.own(watch.target)
                    PrivateWatch.Pause -> privateConnectionHealth.pause()
                    PrivateWatch.Stop -> privateConnectionHealth.stop()
                }
            }.catch { Twig.error(it) { "Automatic server: the private connection watch failed" } }

    /** A synchronizer error, or a DISCONNECTED status that lasts [DISCONNECTED_GRACE]: no request identity. */
    private fun genericErrors(): Flow<Unit> = connectivityEdges().map { }

    /**
     * Drops a recorded gateway outage as soon as automatic mode is left. [ohttpUnavailable] already hides
     * it while manual, but without this a stale `true` would bring the banner straight back when the user
     * re-enters automatic mode, before any evaluation has run.
     */
    internal fun clearUnavailableOnManualMode(): Flow<Boolean> =
        isServerAutomatic
            .onEach { automatic ->
                if (!automatic) {
                    manualModeClears.incrementAndGet()
                    _ohttpUnavailable.value = false
                }
            }.catch { Twig.error(it) { "Automatic server: the server selection mode signal failed" } }

    /**
     * The automatic-selection lane, OHTTP first (see [ServerFallbackPolicy]). Every foreground edge
     * re-evaluates (rate limited by [evaluationInterval]); a connectivity edge - a synchronizer error or a
     * sustained DISCONNECTED status - re-evaluates at once so reporting a gateway outage is not held back
     * by the rate limit; and while the wallet is on a clearnet server a periodic tick checks whether the
     * OHTTP gateway is back. A newer trigger cancels an in-flight evaluation. A switch candidate is applied only
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

    /** Internal test hook: evaluates as a connectivity edge, i.e. an outage the synchronizer already reported. */
    internal suspend fun evaluateServerSwitch(): LightWalletEndpoint? =
        evaluateServerSwitch(trigger = SwitchTrigger.CONNECTIVITY)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun switchLane(triggers: Flow<SwitchTrigger>): Flow<LightWalletEndpoint> =
        flow {
            // Evaluations accepted and not yet finished. A RECOVERY tick is the lightest check and runs every
            // 30 s in the foreground, so letting it replace a running evaluation (mapLatest) would cancel every
            // CONNECTIVITY or FOREGROUND re-rank before it completes. It is skipped instead; the next tick
            // comes after the evaluation ends. Counted when accepted, before the hop into mapLatest, so a tick
            // right behind a trigger already sees it.
            val inFlight = AtomicInteger(0)
            emitAll(
                triggers
                    .catch { Twig.error(it) { "Automatic server: a trigger signal failed" } }
                    .filter { it.bypassesInterval || evaluationInterval.hasElapsed() }
                    .filter { it != SwitchTrigger.RECOVERY || inFlight.get() == 0 }
                    .onEach { inFlight.incrementAndGet() }
                    .mapLatest { trigger ->
                        try {
                            guarded("evaluate a server switch") { resolveSwitchCandidate(trigger) }
                        } finally {
                            inFlight.decrementAndGet()
                        }
                    }.filterNotNull()
                    .onEach { candidate ->
                        guarded("switch to ${candidate.host}:${candidate.port}") { applyServerSwitch(candidate) }
                    }
            )
        }

    /**
     * A synchronizer error, or a DISCONNECTED status that lasts [DISCONNECTED_GRACE] ([isDisconnectedFor]).
     * The grace detector is built inside the `flatMapLatest` lambda, so each synchronizer gets its own: a
     * new synchronizer whose first status is DISCONNECTED (after the previous one ended DISCONNECTED) starts
     * a fresh 20 s timer instead of inheriting the old edge state.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun connectivityEdges(): Flow<SwitchTrigger> =
        merge(
            synchronizerProvider.error.filterNotNull().map { SwitchTrigger.CONNECTIVITY },
            synchronizerProvider.synchronizer
                .flatMapLatest { it?.status?.isDisconnectedFor() ?: emptyFlow() }
                .filter { it }
                .map { SwitchTrigger.CONNECTIVITY },
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
     * evaluation) when the app returns. The fallback recovery ticks run every [FALLBACK_RECOVERY_INTERVAL]
     * in the foreground and every [FALLBACK_RECOVERY_BACKGROUND_INTERVAL] in the background, so a wallet
     * syncing in the background still returns to OHTTP.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun healthTicks(): Flow<SwitchTrigger> =
        isServerAutomatic
            .flatMapLatest { automatic ->
                if (!automatic) {
                    emptyFlow()
                } else {
                    combine(
                        isOhttpFallbackActiveProvider.observe(),
                        isPublicConnectionProvider.observe(),
                    ) { fallbackActive, isPublic -> fallbackActive to isPublic }
                        .distinctUntilChanged()
                        .flatMapLatest { (fallbackActive, isPublic) ->
                            if (fallbackActive) {
                                applicationStateProvider
                                    .isInForeground
                                    .distinctUntilChanged()
                                    .flatMapLatest { foreground ->
                                        ticks(
                                            if (foreground) {
                                                FALLBACK_RECOVERY_INTERVAL
                                            } else {
                                                FALLBACK_RECOVERY_BACKGROUND_INTERVAL
                                            },
                                            SwitchTrigger.RECOVERY
                                        )
                                    }
                            } else if (isPublic || requestHealthDetection.isEnabled()) {
                                // The request-health detection runs its own fallback probe in Private mode.
                                emptyFlow()
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
     * server, and the clearnet tier is never probed - so no clearnet host sees the device, and speed is
     * never a reason to leave OHTTP. When no gateway answers, the wallet stays on the gateway and the
     * unavailability is reported ([ohttpUnavailable]); Automatic mode never switches to clearnet on its
     * own, only an explicit manual server choice does.
     *
     * Consecutive failure confirmation: when the current server is a gateway and tier 0 is empty, every
     * trigger waits for a second consecutive failure before reporting, so a single failed check (a
     * transient Tor circuit timeout, one synchronizer error) does not raise it.
     *
     * A HEALTH trigger on the gateway skips the benchmark and only probes the current gateway
     * ([probeGateway]); every other trigger ranks as described above.
     */
    @Suppress("ReturnCount", "ComplexMethod", "NestedBlockDepth")
    internal suspend fun evaluateServerSwitch(trigger: SwitchTrigger): LightWalletEndpoint? {
        val clearsAtStart = manualModeClears.get()
        if (!isServerAutomatic()) {
            _ohttpUnavailable.value = false
            return null
        }
        // A build without an OHTTP gateway has no Private connection: it uses the clearnet selection.
        if (isPublicConnectionProvider.get() || !lightWalletEndpointProvider.hasOhttpGateway()) {
            _ohttpUnavailable.value = false
            return evaluatePublicServerSwitch(trigger)
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
                    // Routine tick: only prove the gateway answers; the full benchmark is for choosing servers.
                    listOf(current).filter { probeGateway(synchronizer, it) }
                } else {
                    rankValidated(synchronizer, gateways)
                }

            if (healthyGateways.isNotEmpty() || !onGateway) {
                consecutiveGatewayFailures = 0
            }

            // Every trigger, a synchronizer error included, needs CONFIRMATIONS_TO_REPORT consecutive failed
            // checks before the outage is reported (ZIP #10), so one transient failure never raises it.
            val needsConfirmation = onGateway && healthyGateways.isEmpty() && !requestHealthDetection.isEnabled()
            if (needsConfirmation) {
                // Offline failures should not count as gateway outages (matches iOS behavior).
                if (!networkConnectivityProvider.isOnline()) {
                    Twig.info {
                        "Automatic server: device is offline, not counting gateway failure (trigger=$trigger)"
                    }
                    syncFallbackFlag(current)
                    return null
                }
                consecutiveGatewayFailures++
                if (consecutiveGatewayFailures < CONFIRMATIONS_TO_REPORT) {
                    Twig.info {
                        "Automatic server: gateway unreachable once (trigger=$trigger), waiting for confirmation"
                    }
                    syncFallbackFlag(current)
                    return null
                }
                Twig.info {
                    "Automatic server: gateway unavailability confirmed after $CONFIRMATIONS_TO_REPORT consecutive " +
                        "failures"
                }
                consecutiveGatewayFailures = 0
            }

            val decision =
                ServerFallbackPolicy.decide(
                    current = current,
                    healthyGateways = healthyGateways,
                    isOhttpGateway = lightWalletEndpointProvider::isOhttpGateway,
                    configuredGateways = gateways,
                )

            when (decision) {
                is ServerFallbackPolicy.DecisionResult.ReturnToGateway -> {
                    if (decision.isReachable) {
                        _ohttpUnavailable.value = false
                    } else {
                        // Counts as the first failed check: the next check, on the gateway, confirms it.
                        consecutiveGatewayFailures = 1
                    }
                    Twig.info {
                        "Automatic server: OHTTP-first current=${current.host} " +
                            "gatewayReachable=${healthyGateways.isNotEmpty()} -> ${decision.endpoint.host}"
                    }
                    syncFallbackFlag(decision.endpoint)
                    decision.endpoint
                }

                is ServerFallbackPolicy.DecisionResult.StayAndReportGatewayUnavailable -> {
                    // With the request-health detection on, the outage is confirmed there, not here.
                    if (!requestHealthDetection.isEnabled()) reportGatewayUnavailable(clearsAtStart)
                    Twig.info {
                        "Automatic server: OHTTP-first current=${current.host} " +
                            "gatewayReachable=false -> stay (gateway unavailable, awaiting manual server selection)"
                    }
                    syncFallbackFlag(current)
                    null
                }

                is ServerFallbackPolicy.DecisionResult.Stay -> {
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
     * Public mode: the original automatic server switch, restricted to the public (clearnet) servers. On a
     * foreground edge the SDK benchmarks them through its hysteresis policy and names a better server, if
     * any; the gateway is never a candidate. Health, recovery and connectivity triggers belong to Private.
     */
    @Suppress("ReturnCount")
    private suspend fun evaluatePublicServerSwitch(trigger: SwitchTrigger): LightWalletEndpoint? {
        if (isAppInTransactionState) return null
        if (isOhttpFallbackActiveProvider.get() && trigger.probesGatewayRecovery) {
            evaluateReturnToPrivate(trigger)?.let { return it }
        }
        if (trigger != SwitchTrigger.FOREGROUND && trigger != SwitchTrigger.CONNECTIVITY) return null
        val current = persistableWalletProvider.getPersistableWallet()?.endpoint ?: return null
        val candidates = lightWalletEndpointProvider.getFallbackEndpoints()
        if (candidates.isEmpty()) return null
        val synchronizer = synchronizerProvider.getSynchronizerOrNull() ?: return null
        return try {
            synchronizer.evaluateServerSwitch(
                current = current,
                candidates = candidates,
                fetchThreshold = PUBLIC_FETCH_THRESHOLD,
                blocksToFetch = 1
            )
        } finally {
            evaluationInterval.markAttempted()
        }
    }

    /**
     * Returns the first healthy OHTTP gateway, or null. Called for the triggers with
     * [SwitchTrigger.probesGatewayRecovery] (RECOVERY, FOREGROUND, CONNECTIVITY): one lightweight [probeGateway]
     * per gateway (one GetLightdInfo, 20 s cap), the first that answers wins. There is one gateway per network, so
     * ranking buys nothing. RECOVERY ticks run every 30 s (foreground) or 5 min (background): the full benchmark
     * takes 90 s + 20 s, so a tick never runs it, and [switchLane] skips a tick while an evaluation is running.
     */
    @Suppress("ReturnCount")
    private suspend fun evaluateReturnToPrivate(trigger: SwitchTrigger): LightWalletEndpoint? {
        val gateways = lightWalletEndpointProvider.getEndpoints().filter(lightWalletEndpointProvider::isOhttpGateway)
        if (gateways.isEmpty()) return null
        val synchronizer = synchronizerProvider.getSynchronizerOrNull() ?: return null
        return try {
            gateways.firstOrNull { probeGateway(synchronizer, it) }
        } finally {
            // A RECOVERY tick is the periodic probe itself. Marking it would restart the evaluation interval
            // every 30 s and filter out every FOREGROUND trigger for as long as the fallback lasts.
            if (trigger != SwitchTrigger.RECOVERY) evaluationInterval.markAttempted()
        }
    }

    private suspend fun reportGatewayUnavailable(clearsAtStart: Long) {
        if (manualModeClears.get() == clearsAtStart && isServerAutomatic()) {
            _ohttpUnavailable.value = true
            // The clear bumps the counter before it writes false: re-check after our write.
            if (manualModeClears.get() != clearsAtStart) _ohttpUnavailable.value = false
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
     * Applies a server switch if the candidate still fits the current connection mode.
     *
     * The candidate was chosen for the mode the wallet was in when evaluation started. The user can change
     * the mode while it runs (a benchmark plus snapshot wait takes time), so the candidate is only applied if
     * it still fits: Private never moves to clearnet, and a deliberate Public never returns to the gateway.
     * The user's save takes the same lock, so the mode cannot change between the validation and writes.
     *
     * The endpoint is written first; if this fails, the wallet must not already read as Private while on clearnet.
     * For Private mode, the fallback flag is updated to reflect whether the endpoint is off the gateway.
     *
     * The SDK is told about the switch only after it has been applied: a declined or failed switch that confirmed
     * anyway would clear the SDK's consecutive-failure count and start its cooldown, leaving a wallet on a dead
     * server to earn its way out from scratch. The Synchronizer is taken before the endpoint write, because writing
     * it tears the current Synchronizer down and rebuilds it. Which instance receives the confirmation does not
     * matter - the hysteresis state it updates is process-wide and outlives any single Synchronizer.
     */
    private suspend fun applyServerSwitch(candidate: LightWalletEndpoint) =
        serverSelectionMutex.withLock { applyServerSwitchLocked(candidate) }

    @Suppress("ReturnCount")
    private suspend fun applyServerSwitchLocked(candidate: LightWalletEndpoint) {
        if (!isServerAutomatic() || isAppInTransactionState) return
        if (candidate !in lightWalletEndpointProvider.getEndpoints()) return
        val returningToPrivate = lightWalletEndpointProvider.isOhttpGateway(candidate)
        val isPublic = isPublicConnectionProvider.get() || !lightWalletEndpointProvider.hasOhttpGateway()
        if (!returningToPrivate && !isPublic) {
            Twig.warn { "Automatic server: dropped a clearnet candidate, the wallet is no longer Public" }
            return
        }
        if (returningToPrivate && isPublic && !isOhttpFallbackActiveProvider.get()) {
            Twig.warn { "Automatic server: dropped a gateway candidate, Public was chosen on purpose" }
            return
        }
        val synchronizer: Synchronizer? = synchronizerProvider.getSynchronizerOrNull()
        Twig.info { "Automatic server: switching to ${candidate.host}:${candidate.port}" }
        walletRepository.updateWalletEndpoint(candidate)
        if (returningToPrivate) isPublicConnectionProvider.store(false)
        val isOnPrivate = !isPublicConnectionProvider.get() && lightWalletEndpointProvider.hasOhttpGateway()
        if (returningToPrivate || isOnPrivate) syncFallbackFlag(candidate)
        synchronizer?.confirmServerSwitch(candidate)
    }
}

/** What woke the automatic-selection lane; connectivity edges skip the foreground rate limit. */
enum class SwitchTrigger(
    val bypassesInterval: Boolean,
    val probesGatewayRecovery: Boolean = false,
) {
    FOREGROUND(bypassesInterval = false, probesGatewayRecovery = true),
    CONNECTIVITY(bypassesInterval = true, probesGatewayRecovery = true),
    HEALTH(bypassesInterval = true),
    RECOVERY(bypassesInterval = true, probesGatewayRecovery = true),
}

/** Consecutive failed gateway checks before the outage is reported. */
private const val CONFIRMATIONS_TO_REPORT = 2

/** How often, while on the OHTTP gateway, to confirm it still answers. */
private val GATEWAY_HEALTH_INTERVAL = 2.minutes

/** Cap on one plain validation of a fallback candidate. */
private val VALIDATION_TIMEOUT = 20.seconds

/** Public mode: how long a test block fetch may take before the SDK counts the server as slow. */
private val PUBLIC_FETCH_THRESHOLD = 5.seconds

/**
 * How often, while on a clearnet fallback and in the foreground, to check whether the OHTTP gateway is back.
 * The probe checks the gateway only via lightweight [probeGateway] (one GetLightdInfo per gateway, 20 s cap),
 * never re-benchmarking clearnet servers. When the gateway does not answer, a CONNECTIVITY or FOREGROUND
 * trigger goes on to re-rank the public tier. [switchLane] skips the RECOVERY ticks that fall into such a
 * re-rank instead of letting them cancel it, and a tick does not restart the evaluation interval.
 */
private val FALLBACK_RECOVERY_INTERVAL = 30.seconds

/** The same check while the app is in the background, sparing the battery. */
private val FALLBACK_RECOVERY_BACKGROUND_INTERVAL = 5.minutes

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
