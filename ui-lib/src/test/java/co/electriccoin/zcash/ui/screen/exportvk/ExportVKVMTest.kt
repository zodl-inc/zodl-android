package co.electriccoin.zcash.ui.screen.exportvk

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.common.usecase.GetVKUseCase
import co.electriccoin.zcash.ui.screen.exportvk.confirm.ExportVKConfirmArgs
import co.electriccoin.zcash.ui.screen.exportvk.detail.VKDetailArgs
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Export Viewing Key chooser (MOB-1883): exactly one of Incoming/Full can be selected, Continue stays
 * disabled until a selection whose key the account actually has, Incoming goes straight to the export screen
 * and Full goes through the consent sheet first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportVKVMTest {
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
    fun continueIsDisabledWithoutSelection() =
        runTest(dispatcher) {
            val vm = startedVm()

            val state = requireNotNull(vm.state.value)
            assertFalse(state.continueButton.isEnabled)
            assertTrue(state.options.none { it.isChecked })
        }

    @Test
    fun optionsAreOrderedIncomingThenFull() =
        runTest(dispatcher) {
            val vm = startedVm()

            assertEquals(
                listOf(VKType.INCOMING, VKType.FULL),
                requireNotNull(vm.state.value).options.map { it.type }
            )
        }

    @Test
    fun tappingAnOptionSelectsOnlyItAndEnablesContinue() =
        runTest(dispatcher) {
            val vm = startedVm()

            optionFor(vm, VKType.FULL).onClick()
            advanceUntilIdle()
            optionFor(vm, VKType.INCOMING).onClick()
            advanceUntilIdle()

            val state = requireNotNull(vm.state.value)
            assertEquals(
                listOf(true, false),
                state.options.map { it.isChecked },
            )
            assertTrue(state.continueButton.isEnabled)
        }

    @Test
    fun continueWithIncomingReplacesTheChooserWithTheExportScreen() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router = router)

            optionFor(vm, VKType.INCOMING).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).continueButton.onClick()

            assertEquals(VKDetailArgs(VKType.INCOMING), router.replacedRoutes.single())
            assertTrue(router.forwardedRoutes.isEmpty())
        }

    @Test
    fun continueWithFullForwardsToTheConsentSheet() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router = router)

            optionFor(vm, VKType.FULL).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).continueButton.onClick()

            assertEquals(ExportVKConfirmArgs, router.forwardedRoutes.single())
        }

    @Test
    fun continueStaysDisabledWhenTheSelectedKeyIsMissing() =
        runTest(dispatcher) {
            val vm =
                startedVm(
                    availability = mapOf(VKType.INCOMING to true, VKType.FULL to false)
                )

            optionFor(vm, VKType.FULL).onClick()
            advanceUntilIdle()

            val state = requireNotNull(vm.state.value)
            assertTrue(optionFor(vm, VKType.FULL).isChecked)
            assertFalse(state.continueButton.isEnabled)
        }

    @Test
    fun backNavigatesBack() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router = router)

            requireNotNull(vm.state.value).onBack()

            assertEquals(1, router.backCount)
        }

    private fun optionFor(
        vm: ExportVKVM,
        type: VKType
    ) = requireNotNull(vm.state.value).options.first { it.type == type }

    private fun TestScope.startedVm(
        availability: Map<VKType, Boolean> =
            mapOf(VKType.INCOMING to true, VKType.FULL to true),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): ExportVKVM {
        val getVK =
            mockk<GetVKUseCase> {
                every { observeAvailability() } returns flowOf(availability)
            }
        val vm = ExportVKVM(getVK = getVK, navigationRouter = router)
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private class FakeNavigationRouter : NavigationRouter {
    var backCount = 0
        private set
    val forwardedRoutes = mutableListOf<Any>()
    val replacedRoutes = mutableListOf<Any>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) {
        replacedRoutes.addAll(routes)
    }

    override fun replaceAll(vararg routes: Any) = Unit

    override fun replaceFrom(route: KClass<*>, vararg routes: Any) = Unit

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
