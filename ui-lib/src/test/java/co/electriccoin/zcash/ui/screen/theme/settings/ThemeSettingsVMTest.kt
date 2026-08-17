package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsOledThemeEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.SetOledThemeUseCase
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
 * The Theme settings screen (MOB-1724): the stored preference decides which option is checked, Save only
 * becomes available once the selection differs from the stored one, and saving persists the choice and pops
 * the screen.
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
    fun neverChosenSelectsClassicWithSaveDisabled() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeIsOledThemeEnabledStorageProvider(stored = null))

            val state = requireNotNull(vm.state.value)
            assertTrue(state.isClassicThemeSelected.isChecked)
            assertFalse(state.isOledThemeSelected.isChecked)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun storedFalseSelectsClassicWithSaveDisabled() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeIsOledThemeEnabledStorageProvider(stored = false))

            val state = requireNotNull(vm.state.value)
            assertTrue(state.isClassicThemeSelected.isChecked)
            assertFalse(state.isOledThemeSelected.isChecked)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun storedTrueSelectsOledWithSaveDisabled() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeIsOledThemeEnabledStorageProvider(stored = true))

            val state = requireNotNull(vm.state.value)
            assertFalse(state.isClassicThemeSelected.isChecked)
            assertTrue(state.isOledThemeSelected.isChecked)
            assertFalse(state.saveButton.isEnabled)
        }

    @Test
    fun togglingBackAndForthDisablesSaveAgain() =
        runTest(dispatcher) {
            val vm = startedVm(provider = FakeIsOledThemeEnabledStorageProvider(stored = null))

            requireNotNull(vm.state.value).isOledThemeSelected.onClick()
            advanceUntilIdle()

            val oledSelected = requireNotNull(vm.state.value)
            assertTrue(oledSelected.isOledThemeSelected.isChecked)
            assertTrue(oledSelected.saveButton.isEnabled)

            oledSelected.isClassicThemeSelected.onClick()
            advanceUntilIdle()

            val classicSelected = requireNotNull(vm.state.value)
            assertTrue(classicSelected.isClassicThemeSelected.isChecked)
            assertFalse(classicSelected.saveButton.isEnabled)
        }

    @Test
    fun savingOledPersistsTheChoiceAndNavigatesBack() =
        runTest(dispatcher) {
            val provider = FakeIsOledThemeEnabledStorageProvider(stored = null)
            val router = FakeNavigationRouter()
            val vm = startedVm(provider = provider, router = router)

            requireNotNull(vm.state.value).isOledThemeSelected.onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).saveButton.onClick()
            advanceUntilIdle()

            assertEquals(true, provider.stored)
            assertEquals(1, router.backCount)
        }

    @Test
    fun backDismissesWithoutChangingAnything() =
        runTest(dispatcher) {
            val provider = FakeIsOledThemeEnabledStorageProvider(stored = null)
            val router = FakeNavigationRouter()
            val vm = startedVm(provider = provider, router = router)

            requireNotNull(vm.state.value).onBack()

            assertEquals(1, router.backCount)
            assertNull(provider.stored)
        }

    private fun TestScope.startedVm(
        provider: FakeIsOledThemeEnabledStorageProvider = FakeIsOledThemeEnabledStorageProvider(stored = null),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): ThemeSettingsVM {
        val vm =
            ThemeSettingsVM(
                navigationRouter = router,
                isOledThemeEnabledStorageProvider = provider,
                setOledTheme = SetOledThemeUseCase(router, provider)
            )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private class FakeIsOledThemeEnabledStorageProvider(
    stored: Boolean?
) : IsOledThemeEnabledStorageProvider {
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
