package co.electriccoin.zcash.ui.common.usecase

import androidx.navigation.NavDestination.Companion.hasRoute
import cash.z.ecc.android.sdk.internal.Twig
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.privateconnection.RequestHealthDetection
import co.electriccoin.zcash.ui.common.provider.AppAccessLockProvider
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.screen.ohttpunavailable.OhttpUnavailableArgs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Opens the "private connection unavailable" prompt once per outage episode, on whatever screen the user is on.
 * An episode ends when [AutomaticServerRepository.ohttpUnavailable] turns false again, which also closes the
 * prompt if it is still on top; "Keep waiting" does not re-arm it. If the outage is confirmed while the app is in
 * the background, or while it waits for the user to pass app access authentication, the prompt waits until the
 * app is in the foreground and unlocked: it changes the network settings, so it must never be usable in front of
 * the lock screen. It also waits while a send or signing flow is in progress: the switch rebuilds the
 * Synchronizer, which the automatic lane never does mid-transaction.
 */
class ObserveOhttpUnavailablePromptUseCase(
    private val automaticServerRepository: AutomaticServerRepository,
    private val applicationStateProvider: ApplicationStateProvider,
    private val appAccessLockProvider: AppAccessLockProvider,
    private val navigationRouter: NavigationRouter,
    private val requestHealthDetection: RequestHealthDetection,
) {
    /**
     * Observes OHTTP unavailability and shows the prompt when appropriate.
     *
     * Uses `awaitCancellation()` to hold the episode open; `collectLatest` cancels this suspension
     * once the outage clears, allowing the next outage to trigger a new prompt.
     */
    suspend operator fun invoke() {
        automaticServerRepository.ohttpUnavailable
            .catch { Twig.error(it) { "Private connection prompt: the outage signal failed" } }
            .collectLatest { unavailable ->
                if (unavailable) {
                    combine(
                        applicationStateProvider.isInForeground,
                        appAccessLockProvider.isLocked,
                        automaticServerRepository.isTransactionInProgress,
                    ) { foreground, locked, inTransaction -> foreground && !locked && !inTransaction }
                        .first { it }
                    showPrompt()
                    try {
                        awaitCancellation()
                    } finally {
                        // The outage is over (or this episode was replaced): the prompt has nothing left to say.
                        if (requestHealthDetection.isEnabled()) closePrompt()
                    }
                }
            }
    }

    /** Closes the prompt only when it is the screen on top: a send flow pushed above it must not be popped. */
    private fun closePrompt() =
        navigationRouter.custom { current ->
            if (current?.destination?.hasRoute(OhttpUnavailableArgs::class) == true) {
                NavigationCommand.Back
            } else {
                null
            }
        }

    /** The prompt can be left open while the gateway flaps and starts a new episode: never stack a second one. */
    private fun showPrompt() =
        navigationRouter.custom { current ->
            if (current?.destination?.hasRoute(OhttpUnavailableArgs::class) == true) {
                null
            } else {
                NavigationCommand.Forward(listOf(OhttpUnavailableArgs))
            }
        }
}
