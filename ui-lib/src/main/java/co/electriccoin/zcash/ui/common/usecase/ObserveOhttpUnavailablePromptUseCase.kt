package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.screen.ohttpunavailable.OhttpUnavailableArgs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/**
 * Opens the "private connection unavailable" prompt once per outage episode, on whatever screen the user is on.
 * An episode ends when [AutomaticServerRepository.ohttpUnavailable] turns false again; "Keep waiting" does not
 * re-arm it. If the outage is confirmed while the app is in the background, the prompt waits for the foreground.
 */
class ObserveOhttpUnavailablePromptUseCase(
    private val automaticServerRepository: AutomaticServerRepository,
    private val applicationStateProvider: ApplicationStateProvider,
    private val navigationRouter: NavigationRouter,
) {
    suspend operator fun invoke() {
        automaticServerRepository.ohttpUnavailable.collectLatest { unavailable ->
            if (unavailable) {
                applicationStateProvider.isInForeground.first { it }
                navigationRouter.forward(OhttpUnavailableArgs)
                // Hold the episode open: collectLatest cancels this once the outage clears.
                awaitCancellation()
            }
        }
    }
}
