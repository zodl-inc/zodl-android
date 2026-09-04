package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.SetAppearanceModeUseCase
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.screen.more.MoreArgs
import co.electriccoin.zcash.ui.screen.theme.darklook.ThemeDarkLookArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The Theme settings screen (MOB-1740): the stored appearance mode (System/Light/Dark) decides which radio
 * option is checked. Tapping Light selects it locally, enabling Save. Tapping System or Dark doesn't touch
 * the local selection - it opens the dark-look bottom sheet instead, which owns persisting the mode plus the
 * chosen dark look (Classic Dark/Pure Black) and navigates all the way back to the Settings list.
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
            val vm = startedVm(modeProvider = FakeAppearanceModeStorageProvider(stored = null))

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.SYSTEM)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun storedModeIsReflectedInSelection() =
        runTest(dispatcher) {
            val vm = startedVm(modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.DARK))

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.DARK)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun tappingLightSelectsItLocallyAndEnablesSave() =
        runTest(dispatcher) {
            val vm = startedVm(modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.SYSTEM))

            optionFor(requireNotNull(vm.state.value), AppearanceMode.LIGHT).onClick()
            advanceUntilIdle()

            val state = requireNotNull(vm.state.value)
            assertSingleSelection(state, AppearanceMode.LIGHT)
            assertTrue(state.saveButton.isEnabled)
        }

    @Test
    fun tappingSystemForwardsToDarkLookSheetWithoutChangingSelection() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm =
                startedVm(
                    modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.LIGHT),
                    router = router
                )

            optionFor(requireNotNull(vm.state.value), AppearanceMode.SYSTEM).onClick()
            advanceUntilIdle()

            assertEquals(ThemeDarkLookArgs(AppearanceMode.SYSTEM), router.forwardedRoutes.single())
            assertSingleSelection(requireNotNull(vm.state.value), AppearanceMode.LIGHT)
        }

    @Test
    fun tappingDarkForwardsToDarkLookSheetWithoutChangingSelection() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm =
                startedVm(
                    modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.LIGHT),
                    router = router
                )

            optionFor(requireNotNull(vm.state.value), AppearanceMode.DARK).onClick()
            advanceUntilIdle()

            assertEquals(ThemeDarkLookArgs(AppearanceMode.DARK), router.forwardedRoutes.single())
            assertSingleSelection(requireNotNull(vm.state.value), AppearanceMode.LIGHT)
        }

    @Test
    fun savingPersistsLightModeAndNavigatesToSettingsList() =
        runTest(dispatcher) {
            val modeProvider = FakeAppearanceModeStorageProvider(stored = null)
            val oledProvider = FakeIsOledEnabledStorageProvider(stored = true)
            val router = FakeNavigationRouter()
            val vm = startedVm(modeProvider = modeProvider, oledProvider = oledProvider, router = router)

            optionFor(requireNotNull(vm.state.value), AppearanceMode.LIGHT).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).saveButton.onClick()
            advanceUntilIdle()

            assertEquals(AppearanceMode.LIGHT, modeProvider.stored)
            assertEquals(false, oledProvider.stored)
            assertEquals(MoreArgs::class, router.backToRoute)
        }

    @Test
    fun backDismissesWithoutChangingAnything() =
        runTest(dispatcher) {
            val modeProvider = FakeAppearanceModeStorageProvider(stored = null)
            val router = FakeNavigationRouter()
            val vm = startedVm(modeProvider = modeProvider, router = router)

            requireNotNull(vm.state.value).onBack()

            assertEquals(1, router.backCount)
            assertNull(modeProvider.stored)
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
    private val state = MutableStateFlow(stored)

    val stored: AppearanceMode?
        get() = state.value

    override suspend fun get(): AppearanceMode? = state.value

    override suspend fun store(amount: AppearanceMode) {
        state.value = amount
    }

    override fun observe(): Flow<AppearanceMode?> = state

    override suspend fun clear() {
        state.value = null
    }
}

private class FakeIsOledEnabledStorageProvider(
    stored: Boolean?
) : IsOledEnabledStorageProvider {
    private val state = MutableStateFlow(stored)

    val stored: Boolean?
        get() = state.value

    override suspend fun get(): Boolean? = state.value

    override suspend fun store(amount: Boolean) {
        state.value = amount
    }

    override fun observe(): Flow<Boolean?> = state

    override suspend fun clear() {
        state.value = null
    }
}

private class FakeNavigationRouter : NavigationRouter {
    var backCount = 0
        private set
    var backToRoute: KClass<*>? = null
        private set
    val forwardedRoutes = mutableListOf<Any>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) {
        backToRoute = route
    }

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
