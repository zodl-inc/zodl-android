package co.electriccoin.zcash.ui.common.usecase

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.privateconnection.RequestHealthDetection
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOhttpUnavailablePromptUseCaseTest {
    private val unavailable = MutableStateFlow(false)
    private val foreground = MutableStateFlow(true)
    private val locked = MutableStateFlow(false)
    private val inTransaction = MutableStateFlow(false)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private var detection = true
    private val useCase =
        ObserveOhttpUnavailablePromptUseCase(
            automaticServerRepository =
                mockk<AutomaticServerRepository> {
                    every { ohttpUnavailable } returns unavailable
                    every { isTransactionInProgress } returns inTransaction
                },
            applicationStateProvider = mockk<ApplicationStateProvider> { every { isInForeground } returns foreground },
            appAccessLockProvider = mockk<AppAccessLockProvider> { every { isLocked } returns locked },
            navigationRouter = navigationRouter,
            requestHealthDetection = RequestHealthDetection { detection },
        )

    /**
     * The unavailable prompt is shown once per outage episode. Backgrounding/foregrounding
     * during the same episode does not repeat it. Recovery ends the episode.
     */
    @Test
    fun opensThePromptOncePerOutageEpisode() =
        runTest {
            backgroundScope.launch { useCase() }
            runCurrent()
            verify(exactly = 0) { navigationRouter.custom(any()) }

            unavailable.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.custom(any()) }

            foreground.value = false
            foreground.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.custom(any()) }

            // Recovery closes the prompt (one more navigation) and ends the episode; the next outage opens it again.
            unavailable.value = false
            runCurrent()
            verify(exactly = 2) { navigationRouter.custom(any()) }
            unavailable.value = true
            runCurrent()
            verify(exactly = 3) { navigationRouter.custom(any()) }
        }

    /** The prompt only pops itself: a send flow pushed above it must not be popped by a recovery. */
    @Test
    fun recoveryClosesThePromptOnlyWhenItIsOnTop() =
        runTest {
            val commands = mutableListOf<(NavBackStackEntry?) -> NavigationCommand?>()
            every { navigationRouter.custom(capture(commands)) } returns Unit
            backgroundScope.launch { useCase() }
            unavailable.value = true
            runCurrent()
            unavailable.value = false
            runCurrent()

            assertEquals(2, commands.size)
            // Nothing on screen (or something else on top): the recovery pops nothing.
            assertNull(commands.last().invoke(null))
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
    fun aSendOrSigningFlowInProgressHoldsThePromptBack() =
        runTest {
            inTransaction.value = true
            backgroundScope.launch { useCase() }
            unavailable.value = true
            runCurrent()
            verify(exactly = 0) { navigationRouter.custom(any()) }

            inTransaction.value = false
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

    /** Without the request-health detection (release builds) the prompt behaves as before: recovery leaves it open. */
    @Test
    fun recoveryDoesNotCloseThePromptWithoutTheRequestHealthDetection() =
        runTest {
            detection = false
            backgroundScope.launch { useCase() }
            unavailable.value = true
            runCurrent()
            verify(exactly = 1) { navigationRouter.custom(any()) }

            unavailable.value = false
            runCurrent()

            verify(exactly = 1) { navigationRouter.custom(any()) }
        }
}
