package co.electriccoin.zcash.ui.screen.ohttpunavailable

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.SetTorEnabledUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OhttpUnavailableVMTest {
    private val dispatcher = StandardTestDispatcher()
    private val torEnabled = MutableStateFlow(false)
    private val setTorEnabled = mockk<SetTorEnabledUseCase>(relaxed = true)
    private val persist = mockk<PersistServerSelectionUseCase>(relaxed = true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val transactionInProgress = MutableStateFlow(false)
    private val fastestServers = MutableStateFlow(FastestServersState.None)

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
            automaticServerRepository =
                mockk<AutomaticServerRepository> {
                    every { isTransactionInProgress } returns transactionInProgress
                },
            observeFastestServers = mockk<ObserveFastestServersUseCase> { every { invoke() } returns fastestServers },
            lightWalletEndpointProvider = mockk<LightWalletEndpointProvider>(relaxed = true),
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

    @Test
    fun aTransactionStartingWhileTheSheetIsOpenAsksToCloseIt() =
        runTest(dispatcher) {
            stateOf(createVm())
            verify(exactly = 0) { navigationRouter.custom(any()) }
            transactionInProgress.value = true
            advanceUntilIdle()
            verify(exactly = 1) { navigationRouter.custom(any()) }
        }

    @Test
    fun aTransactionDoesNotPopAScreenThatIsNotTheSheet() =
        runTest(dispatcher) {
            val block = slot<(NavBackStackEntry?) -> NavigationCommand?>()
            every { navigationRouter.custom(capture(block)) } returns Unit
            stateOf(createVm())
            transactionInProgress.value = true
            advanceUntilIdle()
            // Popping with the send flow on top would cancel that flow instead of the sheet.
            assertNull(block.captured(null))
        }
}
