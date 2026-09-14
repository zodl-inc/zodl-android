package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.FastestServersResult
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.ServerFallbackPolicy
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Keeps Automatic mode on the OHTTP gateway whenever it is reachable and moves the wallet to the
 * best clearnet lightwalletd while it is not (see [ServerFallbackPolicy]).
 *
 * Triggers: a synchronizer error, a sustained DISCONNECTED status, the app coming to the
 * foreground, and — while a fallback is active — a periodic re-check so the wallet returns to
 * OHTTP as soon as the gateway is back. Every probe goes through the SDK (`validateServerEndpoint`,
 * `getFastestServers`), which honours the Tor preference (`ServiceMode` under `ifTor`), so with Tor
 * on the probes and the fallback traffic never leave over clearnet.
 *
 * The chosen endpoint is persisted the same way Automatic mode always has (the coordinator rebuilds
 * the synchronizer from the persisted wallet); [IsOhttpFallbackActiveProvider] records that the
 * persisted endpoint is a fallback so the default stays OHTTP conceptually and the next trigger
 * tries to go back.
 */
interface OhttpFallbackRepository {
    /** Runs one evaluation now (used by tests and by an explicit "Refresh"). */
    suspend fun evaluate(reason: String)
}

@Suppress("LongParameterList")
class OhttpFallbackRepositoryImpl(
    private val application: Application,
    private val synchronizerProvider: SynchronizerProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val walletRepository: WalletRepository,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val isOhttpFallbackActiveProvider: IsOhttpFallbackActiveProvider,
    private val applicationStateProvider: ApplicationStateProvider,
    private val clock: TimeSource = TimeSource.Monotonic,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : OhttpFallbackRepository {
    private val evaluationMutex = Mutex()
    private var lastEvaluation: TimeSource.Monotonic.ValueTimeMark? = null

    init {
        scope.launch {
            merge(
                synchronizerErrors(),
                sustainedDisconnects(),
                applicationStateProvider.observeOnForeground().map { "foreground" },
                periodicRecoveryChecks(),
            ).collect { reason -> evaluate(reason) }
        }
    }

    private fun synchronizerErrors(): Flow<String> =
        synchronizerProvider.error
            .filterNotNull()
            .map { "synchronizer error: ${it::class.simpleName}" }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun sustainedDisconnects(): Flow<String> =
        synchronizerProvider.synchronizer
            .flatMapLatest { it?.status ?: emptyFlow() }
            .mapLatest { status ->
                // mapLatest cancels the delay when the status changes, so only a status that STAYS
                // disconnected for the grace period fires.
                if (status == Synchronizer.Status.DISCONNECTED) {
                    delay(DISCONNECTED_GRACE)
                    "disconnected for ${DISCONNECTED_GRACE.inWholeSeconds}s"
                } else {
                    null
                }
            }.filterNotNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun periodicRecoveryChecks(): Flow<String> =
        isOhttpFallbackActiveProvider
            .observe()
            .flatMapLatest { active ->
                if (active) {
                    flow {
                        while (true) {
                            delay(RECOVERY_CHECK_INTERVAL)
                            emit("periodic OHTTP recovery check")
                        }
                    }
                } else {
                    emptyFlow()
                }
            }

    override suspend fun evaluate(reason: String) {
        if (isServerSelectionAutomaticProvider.get() != true) return
        val wallet = persistableWalletProvider.getPersistableWallet()
        val synchronizer = synchronizerProvider.getSynchronizerOrNull()
        if (wallet != null && synchronizer != null) {
            evaluationMutex.withLock {
                val last = lastEvaluation
                if (last == null || last.elapsedNow() >= MIN_EVALUATION_INTERVAL) {
                    lastEvaluation = clock.markNow()
                    evaluateLocked(reason, synchronizer, wallet.endpoint)
                }
            }
        }
    }

    private suspend fun evaluateLocked(
        reason: String,
        synchronizer: Synchronizer,
        current: LightWalletEndpoint,
    ) {
        run {
            val gateway = lightWalletEndpointProvider.getDefaultEndpoint()
            Twig.info { "OHTTP fallback: evaluating ($reason); current=${current.host}" }

            val gatewayHealthy = isReachable(synchronizer, gateway)
            val rankedFallbacks =
                if (gatewayHealthy) {
                    emptyList()
                } else {
                    rankFallbacks(synchronizer, lightWalletEndpointProvider.getFallbackEndpoints())
                }

            val target =
                ServerFallbackPolicy.decide(
                    current = current,
                    healthyGateways = if (gatewayHealthy) listOf(gateway) else emptyList(),
                    rankedFallbacks = rankedFallbacks,
                    isOhttpGateway = lightWalletEndpointProvider::isOhttpGateway,
                )

            if (target != null) {
                Twig.info { "OHTTP fallback: ${current.host} -> ${target.host} (gatewayHealthy=$gatewayHealthy)" }
                walletRepository.updateWalletEndpoint(target)
            } else {
                Twig.info { "OHTTP fallback: staying on ${current.host} (gatewayHealthy=$gatewayHealthy)" }
            }
            val active =
                ServerFallbackPolicy.isFallback(
                    endpoint = target ?: current,
                    isOhttpGateway = lightWalletEndpointProvider::isOhttpGateway,
                )
            if (isOhttpFallbackActiveProvider.get() != active) isOhttpFallbackActiveProvider.store(active)
        }
    }

    private suspend fun isReachable(synchronizer: Synchronizer, endpoint: LightWalletEndpoint): Boolean {
        val result =
            withTimeoutOrNull(PROBE_TIMEOUT) {
                runCatching { synchronizer.validateServerEndpoint(application, endpoint) }.getOrNull()
            }
        return result == ServerValidation.Valid
    }

    /** Validated fallback servers, fastest first — the SDK's own fastest-server benchmark. */
    private suspend fun rankFallbacks(
        synchronizer: Synchronizer,
        candidates: List<LightWalletEndpoint>
    ): List<LightWalletEndpoint> =
        withTimeoutOrNull(BENCHMARK_TIMEOUT) {
            runCatching {
                synchronizer
                    .getFastestServers(candidates)
                    .filterIsInstance<FastestServersResult.Done>()
                    .first()
                    .servers
            }.getOrNull()
        }.orEmpty()

    private companion object {
        val DISCONNECTED_GRACE: Duration = 20.seconds
        val RECOVERY_CHECK_INTERVAL: Duration = 5.minutes
        val MIN_EVALUATION_INTERVAL: Duration = 30.seconds
        val PROBE_TIMEOUT: Duration = 10.seconds
        val BENCHMARK_TIMEOUT: Duration = 90.seconds
    }
}
