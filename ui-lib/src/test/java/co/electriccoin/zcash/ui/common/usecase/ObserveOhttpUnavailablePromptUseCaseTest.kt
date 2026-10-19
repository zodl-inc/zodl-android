package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.screen.ohttpunavailable.OhttpUnavailableArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOhttpUnavailablePromptUseCaseTest {
    private val unavailable = MutableStateFlow(false)
    private val foreground = MutableStateFlow(true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val useCase =
        ObserveOhttpUnavailablePromptUseCase(
            automaticServerRepository =
                mockk<AutomaticServerRepository> { every { ohttpUnavailable } returns unavailable },
            applicationStateProvider = mockk<ApplicationStateProvider> { every { isInForeground } returns foreground },
            navigationRouter = navigationRouter,
        )

    @Test
    fun opensThePromptOncePerOutageEpisode() =
        runTest {
            backgroundScope.launch { useCase() }
            runCurrent()
            verify(exactly = 0) { navigationRouter.forward(OhttpUnavailableArgs) }

            unavailable.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.forward(OhttpUnavailableArgs) }

            // Still the same episode: no second prompt.
            foreground.value = false
            foreground.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.forward(OhttpUnavailableArgs) }

            // Recovery ends the episode; the next outage prompts again.
            unavailable.value = false
            runCurrent()
            unavailable.value = true
            runCurrent()
            verify(exactly = 2) { navigationRouter.forward(OhttpUnavailableArgs) }
        }

    @Test
    fun anOutageConfirmedInTheBackgroundPromptsOnceTheAppIsBack() =
        runTest {
            foreground.value = false
            backgroundScope.launch { useCase() }
            unavailable.value = true
            runCurrent()
            verify(exactly = 0) { navigationRouter.forward(OhttpUnavailableArgs) }

            foreground.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.forward(OhttpUnavailableArgs) }
        }
}
