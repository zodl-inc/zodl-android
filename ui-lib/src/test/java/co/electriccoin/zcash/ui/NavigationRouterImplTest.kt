package co.electriccoin.zcash.ui

import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.screen.exportvk.ExportVKArgs
import co.electriccoin.zcash.ui.screen.exportvk.detail.VKDetailArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `replaceFrom` command the Full Viewing Key consent sheet relies on (MOB-1883): it reaches the pipeline
 * carrying both the route to pop back to and the routes to push in their original order, and the router's
 * half-second backoff only swallows a command identical to the one still in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationRouterImplTest {
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun replaceFromEmitsTheCommandWithTheRouteAndTheRoutesInOrder() =
        runTest(dispatcher) {
            val router = NavigationRouterImpl()
            val commands = collectedCommands(router)

            router.replaceFrom(ExportVKArgs::class, VKDetailArgs(VKType.FULL))
            advanceUntilIdle()

            assertEquals(
                listOf(NavigationCommand.ReplaceFrom(ExportVKArgs::class, listOf(VKDetailArgs(VKType.FULL)))),
                commands
            )
        }

    @Test
    fun replaceFromWithDifferentRoutesIsNotDebounced() =
        runTest(dispatcher) {
            val router = NavigationRouterImpl()
            val commands = collectedCommands(router)

            router.replaceFrom(ExportVKArgs::class, VKDetailArgs(VKType.FULL))
            runCurrent()
            router.replaceFrom(ExportVKArgs::class, VKDetailArgs(VKType.INCOMING))
            runCurrent()
            router.replaceFrom(ExportVKArgs::class, VKDetailArgs(VKType.INCOMING))
            runCurrent()

            assertEquals(
                listOf(
                    NavigationCommand.ReplaceFrom(ExportVKArgs::class, listOf(VKDetailArgs(VKType.FULL))),
                    NavigationCommand.ReplaceFrom(ExportVKArgs::class, listOf(VKDetailArgs(VKType.INCOMING)))
                ),
                commands
            )
        }

    private fun TestScope.collectedCommands(router: NavigationRouter): List<BaseNavigationCommand> {
        val commands = mutableListOf<BaseNavigationCommand>()
        backgroundScope.launch { router.observePipeline().collect { commands += it } }
        runCurrent()
        return commands
    }
}
