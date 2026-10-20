package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.ServerConnectionMode
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsOhttpOptInDismissedProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The opt-in is offered to everyone not on Private (Manual, Public) until they opt out with "Not now". It is not
 * offered while Public is only the fallback of a private connection outage: that user never chose Public, and the
 * wallet returns to Private by itself.
 */
class ObserveOhttpOptInVisibleUseCase(
    private val observeServerConnectionMode: ObserveServerConnectionModeUseCase,
    private val isOhttpOptInDismissedProvider: IsOhttpOptInDismissedProvider,
    private val isOhttpFallbackActiveProvider: IsOhttpFallbackActiveProvider,
) {
    operator fun invoke(): Flow<Boolean> =
        combine(
            observeServerConnectionMode(),
            isOhttpOptInDismissedProvider.observe(),
            isOhttpFallbackActiveProvider.observe(),
        ) { mode, dismissed, isOutageFallback ->
            mode != ServerConnectionMode.PRIVATE &&
                !dismissed &&
                !(mode == ServerConnectionMode.PUBLIC && isOutageFallback)
        }.distinctUntilChanged()
}
