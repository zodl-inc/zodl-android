package co.electriccoin.zcash.ui.common.usecase

import androidx.navigation.NavDestination.Companion.hasRoute
import cash.z.ecc.android.sdk.internal.Twig
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
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
 * An episode ends when [AutomaticServerRepository.ohttpUnavailable] turns false again; "Keep waiting" does not
 * re-arm it. If the outage is confirmed while the app is in the background, or while it waits for the user to pass
 * app access authentication, the prompt waits until the app is in the foreground and unlocked: it changes the
 * network settings, so it must never be usable in front of the lock screen.
 */
class ObserveOhttpUnavailablePromptUseCase(
    private val automaticServerRepository: AutomaticServerRepository,
    private val applicationStateProvider: ApplicationStateProvider,
    private val appAccessLockProvider: AppAccessLockProvider,
    private val navigationRouter: NavigationRouter,
) {
    suspend operator fun invoke() {
        automaticServerRepository.ohttpUnavailable
            .catch { Twig.error(it) { "Private connection prompt: the outage signal failed" } }
            .collectLatest { unavailable ->
                if (unavailable) {
                    combine(
                        applicationStateProvider.isInForeground,
                        appAccessLockProvider.isLocked,
                    ) { foreground, locked -> foreground && !locked }
                        .first { it }
                    showPrompt()
                    // Hold the episode open: collectLatest cancels this once the outage clears.
                    awaitCancellation()
                }
            }
    }

    // The prompt can be left open while the gateway flaps, which starts a new episode: never stack a second one.
    private fun showPrompt() =
        navigationRouter.custom { current ->
            if (current?.destination?.hasRoute(OhttpUnavailableArgs::class) == true) {
                null
            } else {
                NavigationCommand.Forward(listOf(OhttpUnavailableArgs))
            }
        }
}
