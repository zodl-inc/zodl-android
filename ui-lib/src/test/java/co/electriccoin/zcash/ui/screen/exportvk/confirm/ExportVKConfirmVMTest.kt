package co.electriccoin.zcash.ui.screen.exportvk.confirm

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.exportvk.ExportVKArgs
import co.electriccoin.zcash.ui.screen.exportvk.detail.VKDetailArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
 * The Full Viewing Key consent sheet (MOB-1883): the three acknowledgements are shown unchecked in their
 * agreed order, Export Key unlocks only once all of them are checked, unchecking any of them locks it again,
 * Export replaces the sheet and the chooser beneath it with the export screen and Cancel simply dismisses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportVKConfirmVMTest {
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
    fun consentCheckboxesCarryTheThreeDisclaimersInOrder() =
        runTest(dispatcher) {
            val vm = startedVm()

            val state = requireNotNull(vm.state.value)
            assertEquals(
                listOf(
                    stringRes(R.string.exportViewingKey_confirm_check_reveals),
                    stringRes(R.string.exportViewingKey_confirm_check_trust),
                    stringRes(R.string.exportViewingKey_confirm_check_undone)
                ),
                state.checkboxes.map { it.title }
            )
            assertTrue(state.checkboxes.none { it.isChecked })
        }

    @Test
    fun exportIsDisabledUntilAllThreeAreChecked() =
        runTest(dispatcher) {
            val vm = startedVm()

            assertEquals(3, requireNotNull(vm.state.value).checkboxes.size)
            assertFalse(requireNotNull(vm.state.value).exportButton.isEnabled)

            check(vm, 0)
            check(vm, 1)
            assertFalse(requireNotNull(vm.state.value).exportButton.isEnabled)

            check(vm, 2)
            val state = requireNotNull(vm.state.value)
            assertTrue(state.checkboxes.all { it.isChecked })
            assertTrue(state.exportButton.isEnabled)
        }

    @Test
    fun uncheckingDisablesExportAgain() =
        runTest(dispatcher) {
            val vm = startedVm()
            check(vm, 0)
            check(vm, 1)
            check(vm, 2)

            check(vm, 1)

            val state = requireNotNull(vm.state.value)
            assertEquals(listOf(true, false, true), state.checkboxes.map { it.isChecked })
            assertFalse(state.exportButton.isEnabled)
        }

    @Test
    fun exportReplacesTheSheetWithTheFullKeyExportScreen() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router)
            check(vm, 0)
            check(vm, 1)
            check(vm, 2)

            requireNotNull(vm.state.value).exportButton.onClick()

            assertEquals(ExportVKArgs::class to listOf<Any>(VKDetailArgs(VKType.FULL)), router.replacedFrom.single())
            assertTrue(router.replacedRoutes.isEmpty())
            assertTrue(router.forwardedRoutes.isEmpty())
        }

    @Test
    fun cancelAndBackDismiss() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router)

            requireNotNull(vm.state.value).cancelButton.onClick()
            requireNotNull(vm.state.value).onBack()

            assertEquals(2, router.backCount)
            assertTrue(router.replacedFrom.isEmpty())
        }

    private fun TestScope.check(
        vm: ExportVKConfirmVM,
        index: Int
    ) {
        requireNotNull(vm.state.value).checkboxes[index].onClick()
        advanceUntilIdle()
    }

    private fun TestScope.startedVm(router: FakeNavigationRouter = FakeNavigationRouter()): ExportVKConfirmVM {
        val vm = ExportVKConfirmVM(navigationRouter = router)
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
    val replacedFrom = mutableListOf<Pair<KClass<*>, List<Any>>>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) {
        replacedRoutes.addAll(routes)
    }

    override fun replaceAll(vararg routes: Any) = Unit

    override fun replaceFrom(route: KClass<*>, vararg routes: Any) {
        replacedFrom += route to routes.toList()
    }

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
