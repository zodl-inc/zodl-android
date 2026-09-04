package co.electriccoin.zcash.ui.screen.theme

import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The app-wide theme state (MOB-1740): the stored appearance mode and dark look are read as one emission, so
 * the splash gate ([ThemeVM.isThemeResolved]) never reports resolved while either half is still missing, and
 * the two exposed values always belong to the same read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeVMTest {
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
    fun storedValuesAreExposedOnceBothAreRead() =
        runTest(dispatcher) {
            val vm =
                startedVm(
                    modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.DARK),
                    oledProvider = FakeIsOledEnabledStorageProvider(stored = true)
                )

            assertTrue(vm.isThemeResolved.value)
            assertEquals(AppearanceMode.DARK, vm.appearanceMode.value)
            assertTrue(vm.isOledEnabled.value)
        }

    @Test
    fun neverChosenPreferencesResolveToSystemAndClassicDark() =
        runTest(dispatcher) {
            val vm =
                startedVm(
                    modeProvider = FakeAppearanceModeStorageProvider(stored = null),
                    oledProvider = FakeIsOledEnabledStorageProvider(stored = null)
                )

            assertTrue(vm.isThemeResolved.value)
            assertEquals(AppearanceMode.SYSTEM, vm.appearanceMode.value)
            assertFalse(vm.isOledEnabled.value)
        }

    @Test
    fun themeStaysUnresolvedWhileEitherStoredValueIsMissing() =
        runTest(dispatcher) {
            val oledProvider = FakeIsOledEnabledStorageProvider(isSeeded = false)
            val vm =
                startedVm(
                    modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.LIGHT),
                    oledProvider = oledProvider
                )

            assertFalse(vm.isThemeResolved.value)
            assertEquals(AppearanceMode.SYSTEM, vm.appearanceMode.value)
            assertFalse(vm.isOledEnabled.value)

            oledProvider.emit(false)
            advanceUntilIdle()

            assertTrue(vm.isThemeResolved.value)
            assertEquals(AppearanceMode.LIGHT, vm.appearanceMode.value)
        }

    @Test
    fun themeResolutionIsObservableWithoutSubscribingToIt() =
        runTest(dispatcher) {
            val vm =
                ThemeVM(
                    appearanceModeStorageProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.DARK),
                    isOledEnabledStorageProvider = FakeIsOledEnabledStorageProvider(stored = true)
                )

            advanceUntilIdle()

            assertTrue(vm.isThemeResolved.value)
        }

    @Test
    fun laterPreferenceChangesAreReflected() =
        runTest(dispatcher) {
            val modeProvider = FakeAppearanceModeStorageProvider(stored = AppearanceMode.SYSTEM)
            val oledProvider = FakeIsOledEnabledStorageProvider(stored = false)
            val vm = startedVm(modeProvider = modeProvider, oledProvider = oledProvider)

            modeProvider.emit(AppearanceMode.DARK)
            oledProvider.emit(true)
            advanceUntilIdle()

            assertEquals(AppearanceMode.DARK, vm.appearanceMode.value)
            assertTrue(vm.isOledEnabled.value)
        }

    private fun TestScope.startedVm(
        modeProvider: FakeAppearanceModeStorageProvider = FakeAppearanceModeStorageProvider(stored = null),
        oledProvider: FakeIsOledEnabledStorageProvider = FakeIsOledEnabledStorageProvider(stored = null),
    ): ThemeVM {
        val vm =
            ThemeVM(
                appearanceModeStorageProvider = modeProvider,
                isOledEnabledStorageProvider = oledProvider
            )
        backgroundScope.launch { vm.appearanceMode.collect { } }
        backgroundScope.launch { vm.isOledEnabled.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private class FakeAppearanceModeStorageProvider(
    stored: AppearanceMode? = null,
    isSeeded: Boolean = true,
) : AppearanceModeStorageProvider {
    private val state = MutableSharedFlow<AppearanceMode?>(replay = 1)

    private var current: AppearanceMode? = stored

    init {
        if (isSeeded) {
            state.tryEmit(stored)
        }
    }

    suspend fun emit(value: AppearanceMode?) {
        current = value
        state.emit(value)
    }

    override suspend fun get(): AppearanceMode? = current

    override suspend fun store(amount: AppearanceMode) = emit(amount)

    override fun observe(): Flow<AppearanceMode?> = state

    override suspend fun clear() = emit(null)
}

private class FakeIsOledEnabledStorageProvider(
    stored: Boolean? = null,
    isSeeded: Boolean = true,
) : IsOledEnabledStorageProvider {
    private val state = MutableSharedFlow<Boolean?>(replay = 1)

    private var current: Boolean? = stored

    init {
        if (isSeeded) {
            state.tryEmit(stored)
        }
    }

    suspend fun emit(value: Boolean?) {
        current = value
        state.emit(value)
    }

    override suspend fun get(): Boolean? = current

    override suspend fun store(amount: Boolean) = emit(amount)

    override fun observe(): Flow<Boolean?> = state

    override suspend fun clear() = emit(null)
}
