package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.AppAccessLockProvider
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.screen.ohttpunavailable.OhttpUnavailableArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOhttpUnavailablePromptUseCaseTest {
    private val unavailable = MutableStateFlow(false)
    private val foreground = MutableStateFlow(true)
    private val locked = MutableStateFlow(false)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val useCase =
        ObserveOhttpUnavailablePromptUseCase(
            automaticServerRepository =
                mockk<AutomaticServerRepository> { every { ohttpUnavailable } returns unavailable },
            applicationStateProvider = mockk<ApplicationStateProvider> { every { isInForeground } returns foreground },
            appAccessLockProvider = mockk<AppAccessLockProvider> { every { isLocked } returns locked },
            navigationRouter = navigationRouter,
        )

    @Test
    fun opensThePromptOncePerOutageEpisode() =
        runTest {
            backgroundScope.launch { useCase() }
            runCurrent()
            verify(exactly = 0) { navigationRouter.custom(any()) }

            unavailable.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.custom(any()) }

            // Still the same episode: no second prompt.
            foreground.value = false
            foreground.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.custom(any()) }

            // Recovery ends the episode; the next outage prompts again.
            unavailable.value = false
            runCurrent()
            unavailable.value = true
            runCurrent()
            verify(exactly = 2) { navigationRouter.custom(any()) }
        }

    @Test
    fun anOutageConfirmedInTheBackgroundPromptsOnceTheAppIsBack() =
        runTest {
            foreground.value = false
            backgroundScope.launch { useCase() }
            unavailable.value = true
            runCurrent()
            verify(exactly = 0) { navigationRouter.custom(any()) }

            foreground.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.custom(any()) }
        }

    @Test
    fun theLockScreenHoldsThePromptBack() =
        runTest {
            locked.value = true
            backgroundScope.launch { useCase() }
            unavailable.value = true
            runCurrent()
            verify(exactly = 0) { navigationRouter.custom(any()) }

            locked.value = false
            runCurrent()
            verify(exactly = 1) { navigationRouter.custom(any()) }
        }

    @Test
    fun thePromptIsOpenedAsAForwardNavigationFromAnyScreen() =
        runTest {
            val block = slot<(androidx.navigation.NavBackStackEntry?) -> NavigationCommand?>()
            every { navigationRouter.custom(capture(block)) } returns Unit
            backgroundScope.launch { useCase() }
            unavailable.value = true
            runCurrent()

            assertEquals(NavigationCommand.Forward(listOf(OhttpUnavailableArgs)), block.captured(null))
        }
}
