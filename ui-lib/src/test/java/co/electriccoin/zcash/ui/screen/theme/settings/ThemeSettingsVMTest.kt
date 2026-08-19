package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.usecase.SetAppearanceModeUseCase
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Theme settings screen (MOB-1724): the stored preference decides which of the four options
 * (System/Light/Dark/OLED) is checked, Save only becomes available once the selection differs from the
 * stored one, and saving persists the choice and pops the screen. A never-chosen (null) preference
 * resolves to System.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeSettingsVMTest {
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
    fun neverChosenSelectsSystemWithSaveDisabled() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeAppearanceModeStorageProvider(stored = null))

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.SYSTEM)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun storedLightSelectsLightWithSaveDisabled() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.LIGHT))

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.LIGHT)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun storedDarkSelectsDarkWithSaveDisabled() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.DARK))

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.DARK)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun storedOledSelectsOledWithSaveDisabled() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.OLED))

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.OLED)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun togglingBackAndForthDisablesSaveAgain() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeAppearanceModeStorageProvider(stored = null))

            optionFor(requireNotNull(vm.state.value), AppearanceMode.OLED).onClick()
            advanceUntilIdle()

            val oledSelected = requireNotNull(vm.state.value)
            assertSingleSelection(oledSelected, AppearanceMode.OLED)
            assertTrue(oledSelected.saveButton.isEnabled)

            optionFor(oledSelected, AppearanceMode.SYSTEM).onClick()
            advanceUntilIdle()

            val systemSelected = requireNotNull(vm.state.value)
            assertSingleSelection(systemSelected, AppearanceMode.SYSTEM)
            assertFalse(systemSelected.saveButton.isEnabled)
        }

    @Test
    fun savingOledPersistsTheChoiceAndNavigatesBack() =
        runTest(dispatcher) {
            val provider = FakeAppearanceModeStorageProvider(stored = null)
            val router = FakeNavigationRouter()
            val vm = startedVm(provider = provider, router = router)

            optionFor(requireNotNull(vm.state.value), AppearanceMode.OLED).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).saveButton.onClick()
            advanceUntilIdle()

            assertEquals(AppearanceMode.OLED, provider.stored)
            assertEquals(1, router.backCount)
        }

    @Test
    fun backDismissesWithoutChangingAnything() =
        runTest(dispatcher) {
            val provider = FakeAppearanceModeStorageProvider(stored = null)
            val router = FakeNavigationRouter()
            val vm = startedVm(provider = provider, router = router)

            requireNotNull(vm.state.value).onBack()

            assertEquals(1, router.backCount)
            assertNull(provider.stored)
        }

    private fun assertSingleSelection(
        state: ThemeSettingsState,
        expected: AppearanceMode
    ) {
        state.options.forEach { option ->
            assertEquals(
                option.mode == expected,
                option.isChecked,
                "unexpected checked state for ${option.mode}"
            )
        }
    }

    private fun optionFor(
        state: ThemeSettingsState,
        mode: AppearanceMode
    ) = state.options.first { it.mode == mode }

    private fun TestScope.startedVm(
        provider: FakeAppearanceModeStorageProvider = FakeAppearanceModeStorageProvider(stored = null),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): ThemeSettingsVM {
        val vm =
            ThemeSettingsVM(
                navigationRouter = router,
                appearanceModeStorageProvider = provider,
                setAppearanceMode = SetAppearanceModeUseCase(router, provider)
            )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private class FakeAppearanceModeStorageProvider(
    stored: AppearanceMode?
) : AppearanceModeStorageProvider {
    var stored: AppearanceMode? = stored
        private set

    override suspend fun get(): AppearanceMode? = stored

    override suspend fun store(amount: AppearanceMode) {
        stored = amount
    }

    override fun observe(): Flow<AppearanceMode?> = emptyFlow()

    override suspend fun clear() {
        stored = null
    }
}

private class FakeNavigationRouter : NavigationRouter {
    var backCount = 0
        private set

    override fun forward(vararg routes: Any) = Unit

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
