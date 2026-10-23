package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long a [Synchronizer.Status.DISCONNECTED] status must last before the app acts on it: the
 * connectivity edge of the automatic server selection ([AutomaticServerRepository]) and the
 * "Disconnected" home message (`GetHomeMessageUseCase`) both wait this long, so one failed call or a
 * status blip around a lifecycle change neither probes the gateway nor flashes the banner.
 *
 * The timer is uninterrupted on purpose, which relies on the SDK reporting a sustained outage as a
 * steady DISCONNECTED: the slipstream engine retrying a failed initial pass alternates state 1 (an
 * attempt) and idle (a backoff of 3, 6, 12, 24, 30 s) and the SDK keeps the status DISCONNECTED across
 * those attempts until a snapshot shows the server was reached (`nextStatus`/`serverReached` in
 * `slipstream-lib`), so the flapping never resets this grace; a failed engine restart reads as
 * DISCONNECTED too (`hostWantsRunning`). Were the SDK to flap, an outage shorter than the next idle gap
 * would never be shown - keep the two in step.
 */
internal val DISCONNECTED_GRACE = 20.seconds

/**
 * Whether the status has been [Synchronizer.Status.DISCONNECTED] for at least [grace] without
 * interruption. Emits `false` at once for any status (so a consumer combining it never waits), `true`
 * [grace] after the status became DISCONNECTED and stayed so, and `false` again as soon as it is
 * anything else; equal values are not repeated, so a sustained outage reads as one `false -> true` edge.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<Synchronizer.Status>.isDisconnectedFor(grace: Duration = DISCONNECTED_GRACE): Flow<Boolean> =
    map { it == Synchronizer.Status.DISCONNECTED }
        .distinctUntilChanged()
        .transformLatest { disconnected ->
            emit(false)
            if (disconnected) {
                delay(grace)
                emit(true)
            }
        }.distinctUntilChanged()
