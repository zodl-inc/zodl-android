package co.electriccoin.zcash.ui.common.privateconnection

import android.app.Application
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.CompactBlockProcessorException
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.health.OhttpHealthCenter
import co.electriccoin.lightwallet.client.health.OhttpHealthClock
import co.electriccoin.lightwallet.client.health.OhttpHealthOutcome
import co.electriccoin.lightwallet.client.health.OhttpHealthPurpose
import co.electriccoin.lightwallet.client.health.OhttpHealthRoute
import co.electriccoin.lightwallet.client.health.withOhttpDiagnostic
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth.ProbeResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One bounded read-only probe of the Private route: the SDK's own server validation (GetLightdInfo and
 * the chain checks) of the gateway, through whatever route the wallet uses (Tor included).
 *
 * The outcome is classified from the diagnostic evidence the Kotlin client recorded while the probe ran;
 * where the engine carried the call (Tor) there is none, and a failed validation then means the route did
 * not deliver. A mismatch of network or consensus branch is a configuration problem, never an outage.
 */
class PrivateConnectionProbe(
    private val application: Application,
    private val center: OhttpHealthCenter = OhttpHealthCenter.shared,
    private val clock: () -> Long = OhttpHealthClock::nowNanos,
) {
    suspend fun run(
        synchronizer: Synchronizer,
        endpoint: LightWalletEndpoint,
        route: OhttpHealthRoute,
        generation: Long,
    ): ProbeResult {
        val started = clock()
        val validation =
            withTimeoutOrNull(budget(route)) {
                withOhttpDiagnostic { synchronizer.validateServerEndpoint(application, endpoint) }
            }
        return ProbeResult(
            outcome = classify(validation, diagnosticOutcomes(route, started)),
            route = route,
            generation = generation,
            startedAtNanos = started,
            observedAtNanos = clock(),
        )
    }

    private fun diagnosticOutcomes(
        route: OhttpHealthRoute,
        since: Long,
    ): List<OhttpHealthOutcome> =
        center
            .snapshot(route)
            .observations
            .filter { it.purpose == OhttpHealthPurpose.DIAGNOSTIC && it.startedAtNanos >= since }
            .map { it.outcome }

    internal companion object {
        private val CLEARNET_BUDGET = 5.seconds
        private val TOR_BUDGET = 20.seconds

        fun budget(route: OhttpHealthRoute): Duration = if (route.viaTor) TOR_BUDGET else CLEARNET_BUDGET

        /** A pause, then an unreachable route, then a protocol problem; otherwise what the validation itself says. */
        fun classify(
            validation: ServerValidation?,
            diagnostics: List<OhttpHealthOutcome>,
        ): OhttpHealthOutcome {
            diagnostics.filterIsInstance<OhttpHealthOutcome.RateLimited>().maxByOrNull { it.retryNotBeforeNanos }?.let {
                return it
            }
            return when (validation) {
                // The probe's budget ran out: the route did not deliver in time.
                null -> {
                    OhttpHealthOutcome.TransportUnavailable
                }

                ServerValidation.Valid -> {
                    OhttpHealthOutcome.Usable
                }

                ServerValidation.Running -> {
                    OhttpHealthOutcome.Inconclusive
                }

                is ServerValidation.InValid -> {
                    when {
                        OhttpHealthOutcome.TransportUnavailable in diagnostics -> {
                            OhttpHealthOutcome.TransportUnavailable
                        }

                        validation.reason is CompactBlockProcessorException -> {
                            OhttpHealthOutcome.ConfigurationFailure
                        }

                        else -> {
                            diagnostics.firstOrNull {
                                it == OhttpHealthOutcome.ConfigurationFailure ||
                                    it == OhttpHealthOutcome.PolicyRejected ||
                                    it == OhttpHealthOutcome.ResponseRejected
                            } ?: OhttpHealthOutcome.TransportUnavailable
                        }
                    }
                }
            }
        }
    }
}
