package co.electriccoin.zcash.ui.screen.ohttpunavailable

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.SetTorEnabledUseCase
import io.mockk.coEvery
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
            coEvery { persist.persistPublic(any(), any(), any()) } returns true
            stateOf(createVm()).onSwitchToPublic()
            advanceUntilIdle()
            coVerify(exactly = 1) {
                persist.persistPublic(afterPrivateUnavailable = true, fastest = null, abortIf = any())
            }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun aSwitchRefusedUnderTheLockKeepsTheSheetOpen() =
        runTest(dispatcher) {
            coEvery { persist.persistPublic(any(), any(), any()) } returns false
            stateOf(createVm()).onSwitchToPublic()
            advanceUntilIdle()
            verify(exactly = 0) { navigationRouter.back() }
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

    /**
     * A transaction in progress prevents the sheet from being popped. Popping with the send
     * flow still active would cancel the send instead of the sheet.
     */
    @Test
    fun aTransactionDoesNotPopAScreenThatIsNotTheSheet() =
        runTest(dispatcher) {
            val block = slot<(NavBackStackEntry?) -> NavigationCommand?>()
            every { navigationRouter.custom(capture(block)) } returns Unit
            stateOf(createVm())
            transactionInProgress.value = true
            advanceUntilIdle()
            assertNull(block.captured(null))
        }

    @Test
    fun switchToPublicDoesNotSwitchWhenATransactionIsInProgress() =
        runTest(dispatcher) {
            transactionInProgress.value = true
            stateOf(createVm()).onSwitchToPublic()
            advanceUntilIdle()
            coVerify(exactly = 0) { persist.persistPublic(any(), any(), any()) }
            verify(exactly = 0) { navigationRouter.back() }
        }

    /** The check handed to the use case, which re-runs it under the lock, must follow the live transaction state. */
    @Test
    fun theCheckHandedToTheUseCaseFollowsTheTransactionState() =
        runTest(dispatcher) {
            val abortIf = slot<suspend () -> Boolean>()
            coEvery { persist.persistPublic(any(), any(), capture(abortIf)) } returns true
            stateOf(createVm()).onSwitchToPublic()
            advanceUntilIdle()
            assertEquals(false, abortIf.captured())
            transactionInProgress.value = true
            assertEquals(true, abortIf.captured())
        }
}
