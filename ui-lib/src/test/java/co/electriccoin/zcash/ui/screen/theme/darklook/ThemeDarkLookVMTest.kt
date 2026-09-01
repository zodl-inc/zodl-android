package co.electriccoin.zcash.ui.screen.theme.darklook

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.SetAppearanceModeUseCase
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.screen.more.MoreArgs
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
 * The dark-look bottom sheet (MOB-1740): opened with the [AppearanceMode] the user tapped on the Theme
 * screen (System or Dark), it lets them pick Classic Dark or Pure Black for whenever that mode renders
 * dark, then persists both together and navigates all the way back to the Settings list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeDarkLookVMTest {
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
    fun neverChosenPreselectsClassicDark() =
        runTest(dispatcher) {
            val vm = startedVm(oledProvider = FakeIsOledEnabledStorageProvider(stored = null))

            assertSingleSelection(requireNotNull(vm.state.value), isOledEnabled = false)
        }

    @Test
    fun storedOledChoiceIsReflectedInSelection() =
        runTest(dispatcher) {
            val vm = startedVm(oledProvider = FakeIsOledEnabledStorageProvider(stored = true))

            assertSingleSelection(requireNotNull(vm.state.value), isOledEnabled = true)
        }

    @Test
    fun tappingPureBlackSelectsItLocally() =
        runTest(dispatcher) {
            val vm = startedVm(oledProvider = FakeIsOledEnabledStorageProvider(stored = false))

            optionFor(requireNotNull(vm.state.value), isOledEnabled = true).onClick()
            advanceUntilIdle()

            assertSingleSelection(requireNotNull(vm.state.value), isOledEnabled = true)
        }

    @Test
    fun savingPersistsArgsModeAndSelectedOledChoiceAndNavigatesToSettingsList() =
        runTest(dispatcher) {
            val modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.LIGHT)
            val oledProvider = FakeIsOledEnabledStorageProvider(stored = false)
            val router = FakeNavigationRouter()
            val vm =
                startedVm(
                    args = ThemeDarkLookArgs(AppearanceMode.DARK),
                    modeProvider = modeProvider,
                    oledProvider = oledProvider,
                    router = router
                )

            optionFor(requireNotNull(vm.state.value), isOledEnabled = true).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).saveButton.onClick()
            advanceUntilIdle()

            assertEquals(AppearanceMode.DARK, modeProvider.stored)
            assertEquals(true, oledProvider.stored)
            assertEquals(MoreArgs::class, router.backToRoute)
        }

    @Test
    fun backDismissesWithoutChangingAnything() =
        runTest(dispatcher) {
            val oledProvider = FakeIsOledEnabledStorageProvider(stored = false)
            val router = FakeNavigationRouter()
            val vm = startedVm(oledProvider = oledProvider, router = router)

            requireNotNull(vm.state.value).onBack()

            assertEquals(1, router.backCount)
            assertFalse(oledProvider.stored ?: false)
        }

    private fun assertSingleSelection(
        state: ThemeDarkLookState,
        isOledEnabled: Boolean
    ) {
        state.options.forEach { option ->
            assertEquals(
                option.isOledEnabled == isOledEnabled,
                option.isChecked,
                "unexpected checked state for isOledEnabled=${option.isOledEnabled}"
            )
        }
        assertTrue(state.saveButton.isEnabled)
    }

    private fun optionFor(
        state: ThemeDarkLookState,
        isOledEnabled: Boolean
    ) = state.options.first { it.isOledEnabled == isOledEnabled }

    private fun TestScope.startedVm(
        args: ThemeDarkLookArgs = ThemeDarkLookArgs(AppearanceMode.SYSTEM),
        modeProvider: FakeAppearanceModeStorageProvider = FakeAppearanceModeStorageProvider(stored = null),
        oledProvider: FakeIsOledEnabledStorageProvider = FakeIsOledEnabledStorageProvider(stored = null),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): ThemeDarkLookVM {
        val vm =
            ThemeDarkLookVM(
                args = args,
                navigationRouter = router,
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
    var backToRoute: KClass<*>? = null
        private set

    override fun forward(vararg routes: Any) = Unit

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
