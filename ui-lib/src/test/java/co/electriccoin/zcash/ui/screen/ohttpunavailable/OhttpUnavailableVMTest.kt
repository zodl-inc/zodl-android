package co.electriccoin.zcash.ui.screen.ohttpunavailable

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.SetTorEnabledUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class OhttpUnavailableVMTest {
    private val dispatcher = StandardTestDispatcher()
    private val torEnabled = MutableStateFlow(false)
    private val setTorEnabled = mockk<SetTorEnabledUseCase>(relaxed = true)
    private val persist = mockk<PersistServerSelectionUseCase>(relaxed = true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm() =
        OhttpUnavailableVM(
            isTorEnabled = mockk<IsTorEnabledUseCase> { every { observe() } returns torEnabled },
            setTorEnabled = setTorEnabled,
            persistServerSelection = persist,
            navigationRouter = navigationRouter,
        )

    private fun kotlinx.coroutines.test.TestScope.stateOf(vm: OhttpUnavailableVM): OhttpUnavailableState {
        backgroundScope.launch(dispatcher) { vm.state.collect { } }
        advanceUntilIdle()
        return vm.state.value!!
    }

    @Test
    fun torToggleReflectsTheGlobalSetting() =
        runTest(dispatcher) {
            val vm = createVm()
            assertEquals(false, stateOf(vm).isTorEnabled)
            torEnabled.value = true
            advanceUntilIdle()
            assertEquals(true, vm.state.value!!.isTorEnabled)
        }

    @Test
    fun togglingTorStoresTheGlobalSetting() =
        runTest(dispatcher) {
            stateOf(createVm()).onTorToggle(true)
            advanceUntilIdle()
            coVerify(exactly = 1) { setTorEnabled(true) }
        }

    @Test
    fun keepWaitingOnlyClosesTheSheet() =
        runTest(dispatcher) {
            stateOf(createVm()).onKeepWaiting()
            advanceUntilIdle()
            verify(exactly = 1) { navigationRouter.back() }
            coVerify(exactly = 0) { persist.persistPublic(any()) }
            coVerify(exactly = 0) { persist.persistPrivate() }
        }

    @Test
    fun switchToPublicIsMarkedAsOutageInducedAndCloses() =
        runTest(dispatcher) {
            stateOf(createVm()).onSwitchToPublic()
            advanceUntilIdle()
            coVerify(exactly = 1) { persist.persistPublic(afterPrivateUnavailable = true) }
            verify(exactly = 1) { navigationRouter.back() }
        }
}
