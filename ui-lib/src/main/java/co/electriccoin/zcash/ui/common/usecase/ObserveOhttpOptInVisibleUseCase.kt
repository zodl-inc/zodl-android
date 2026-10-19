package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.ServerConnectionMode
import co.electriccoin.zcash.ui.common.provider.IsOhttpOptInDismissedProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** The opt-in is offered to everyone not on Private (Manual, Public) until they opt out with "Not now". */
class ObserveOhttpOptInVisibleUseCase(
    private val observeServerConnectionMode: ObserveServerConnectionModeUseCase,
    private val isOhttpOptInDismissedProvider: IsOhttpOptInDismissedProvider,
) {
    operator fun invoke(): Flow<Boolean> =
        combine(
            observeServerConnectionMode(),
            isOhttpOptInDismissedProvider.observe()
        ) { mode, dismissed ->
            mode != ServerConnectionMode.PRIVATE && !dismissed
        }.distinctUntilChanged()
}
