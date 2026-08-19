package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
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
 * The Theme settings screen (MOB-1724): the stored appearance mode (System/Light/Dark) decides which
 * radio option is checked. The OLED checkbox is a separate on/off modifier - enabled whenever the
 * selected mode isn't Light (Light never renders dark, so pure black would never apply), unrelated to
 * whether the mode itself changed. Save enables when either the mode or the OLED choice differs from
 * what's stored, and persists both together.
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
    fun neverChosenSelectsSystemWithOledUncheckedAndSaveDisabled() =
        runTest(dispatcher) {
            val vm = startedVm(modeProvider = FakeAppearanceModeStorageProvider(stored = null))

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.SYSTEM)
            assertFalse(state.oledCheckbox.isChecked)
            assertTrue(state.oledCheckbox.isEnabled)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun storedOledEnabledIsReflectedInCheckbox() =
        runTest(dispatcher) {
            val vm =
                startedVm(
                    modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.DARK),
                    oledProvider = FakeIsOledEnabledStorageProvider(stored = true)
                )

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.DARK)
            assertTrue(state.oledCheckbox.isChecked)
            assertTrue(state.oledCheckbox.isEnabled)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun oledCheckboxIsDisabledWhenLightIsSelected() =
        runTest(dispatcher) {
            val vm = startedVm(modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.LIGHT))

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.LIGHT)
            assertFalse(state.oledCheckbox.isEnabled)
        }

    @Test
    fun oledCheckboxBecomesDisabledAfterSwitchingToLight() =
        runTest(dispatcher) {
            val vm =
                startedVm(
                    modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.DARK),
                    oledProvider = FakeIsOledEnabledStorageProvider(stored = true)
                )

            optionFor(requireNotNull(vm.state.value), AppearanceMode.LIGHT).onClick()
            advanceUntilIdle()

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.LIGHT)
            assertFalse(state.oledCheckbox.isEnabled)
        }

    @Test
    fun togglingOledAloneEnablesSave() =
        runTest(dispatcher) {
            val vm = startedVm(modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.DARK))

            requireNotNull(vm.state.value).oledCheckbox.onClick()
            advanceUntilIdle()

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.DARK)
            assertTrue(state.oledCheckbox.isChecked)
            assertTrue(state.saveButton.isEnabled)
        }

    @Test
    fun savingPersistsBothModeAndOledChoiceAndNavigatesBack() =
        runTest(dispatcher) {
            val modeProvider = FakeAppearanceModeStorageProvider(stored = null)
            val oledProvider = FakeIsOledEnabledStorageProvider(stored = null)
            val router = FakeNavigationRouter()
            val vm = startedVm(modeProvider = modeProvider, oledProvider = oledProvider, router = router)

            optionFor(requireNotNull(vm.state.value), AppearanceMode.DARK).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).oledCheckbox.onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).saveButton.onClick()
            advanceUntilIdle()

            assertEquals(AppearanceMode.DARK, modeProvider.stored)
            assertEquals(true, oledProvider.stored)
            assertEquals(1, router.backCount)
        }

    @Test
    fun backDismissesWithoutChangingAnything() =
        runTest(dispatcher) {
            val modeProvider = FakeAppearanceModeStorageProvider(stored = null)
            val oledProvider = FakeIsOledEnabledStorageProvider(stored = null)
            val router = FakeNavigationRouter()
            val vm = startedVm(modeProvider = modeProvider, oledProvider = oledProvider, router = router)

            requireNotNull(vm.state.value).onBack()

            assertEquals(1, router.backCount)
            assertNull(modeProvider.stored)
            assertNull(oledProvider.stored)
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
        modeProvider: FakeAppearanceModeStorageProvider = FakeAppearanceModeStorageProvider(stored = null),
        oledProvider: FakeIsOledEnabledStorageProvider = FakeIsOledEnabledStorageProvider(stored = null),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): ThemeSettingsVM {
        val vm =
            ThemeSettingsVM(
                navigationRouter = router,
                appearanceModeStorageProvider = modeProvider,
                isOledEnabledStorageProvider = oledProvider,
                setAppearanceMode = SetAppearanceModeUseCase(router, modeProvider, oledProvider)
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

private class FakeIsOledEnabledStorageProvider(
    stored: Boolean?
) : IsOledEnabledStorageProvider {
    var stored: Boolean? = stored
        private set

    override suspend fun get(): Boolean? = stored

    override suspend fun store(amount: Boolean) {
        stored = amount
    }

    override fun observe(): Flow<Boolean?> = emptyFlow()

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
