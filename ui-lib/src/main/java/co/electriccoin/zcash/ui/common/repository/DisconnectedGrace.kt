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
