package co.electriccoin.zcash.ui.common.privateconnection

import co.electriccoin.lightwallet.client.health.OhttpHealthCenter
import co.electriccoin.lightwallet.client.health.OhttpHealthOutcome
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.Path
import co.electriccoin.zcash.ui.common.provider.AppAccessLockProvider
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.NetworkConnectivityProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The Private route that should be watched: the gateway the wallet is on, and whether Tor carries it. */
data class PrivateConnectionTarget(
    val endpoint: LightWalletEndpoint,
    val viaTor: Boolean,
)

/**
 * Switch for the request-health outage detection. While it is off, the previous detection (periodic probe
 * with two consecutive confirmations) stays in charge; the two never run at the same time.
 */
fun interface RequestHealthDetection {
    fun isEnabled(): Boolean
}

/** Watches the Private route for an outage and says when one is confirmed. */
interface PrivateConnectionHealthOwner {
    /** The confirmed outage to show, or null. */
    val outage: Flow<PrivateConnectionOutage?>

    /** Starts (or restarts) watching [target]. */
    suspend fun own(target: PrivateConnectionTarget)

    /**
     * Stops watching for a while (the app went to the background) without forgetting a confirmed outage or the
     * user's "Keep waiting"; the shown outage stays.
     */
    fun pause()

    /** Stops watching for good (Public or a manual server, no gateway): the outage is cleared. */
    fun stop()

    /** The user chose "Keep waiting" or dismissed the prompt. */
    fun acknowledge()

    /** A synchronizer error without a request identity. */
    fun onGenericError()
}

/** The owner used where the request-health detection is not provided. */
object NoPrivateConnectionHealth : PrivateConnectionHealthOwner {
    override val outage: Flow<PrivateConnectionOutage?> = emptyFlow()

    override suspend fun own(target: PrivateConnectionTarget) = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun acknowledge() = Unit

    override fun onGenericError() = Unit
}

class PrivateConnectionHealthOwnerImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val probe: PrivateConnectionProbe,
    private val applicationStateProvider: ApplicationStateProvider,
    private val appAccessLockProvider: AppAccessLockProvider,
    private val networkConnectivityProvider: NetworkConnectivityProvider,
    private val center: OhttpHealthCenter = OhttpHealthCenter.shared,
) : PrivateConnectionHealthOwner {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var endpoint: LightWalletEndpoint? = null

    private val monitor =
        PrivateConnectionHealthMonitor(
            scope = scope,
            // The synchronizer is looked up when it is needed, not remembered: a rebuilt one replaces the old.
            observeEvidence = { route ->
                flow {
                    synchronizerProvider.getSynchronizerOrNull()?.let { emitAll(it.observeOhttpHealth(route)) }
                }
            },
            completions = center.completions,
            probe = { route, generation ->
                val synchronizer = synchronizerProvider.getSynchronizerOrNull()
                val watched = endpoint
                if (synchronizer == null || watched == null) {
                    // Nothing to probe with: the policy never counts this as an outage.
                    PrivateConnectionHealth.ProbeResult(OhttpHealthOutcome.Inconclusive, route, generation, 0L, 0L)
                } else {
                    probe.run(synchronizer, watched, route, generation)
                }
            },
            pathUpdates =
                networkConnectivityProvider
                    .observeOnline()
                    .map { online -> if (online) Path.AVAILABLE else Path.UNAVAILABLE },
            // Android has no Tor readiness gate yet: a Tor route is diagnosed as soon as it is owned.
            torReady = flowOf(true),
            presentationAllowed =
                combine(applicationStateProvider.isInForeground, appAccessLockProvider.isLocked) { foreground, locked ->
                    foreground && !locked
                }.distinctUntilChanged(),
        )

    override val outage: Flow<PrivateConnectionOutage?> = monitor.outage

    override suspend fun own(target: PrivateConnectionTarget) {
        val synchronizer = synchronizerProvider.getSynchronizerOrNull()
        val route = synchronizer?.ohttpHealthRoute(target.endpoint.host, target.viaTor)
        if (route == null) {
            stop()
            return
        }
        endpoint = target.endpoint
        monitor.start(route)
    }

    override fun pause() = monitor.pause()

    override fun stop() {
        endpoint = null
        monitor.stop()
    }

    override fun acknowledge() = monitor.acknowledge()

    override fun onGenericError() = monitor.onGenericError()
}
