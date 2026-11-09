package co.electriccoin.zcash.ui.screen.ohttpoptin

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.OptOutOfOhttpUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OhttpOptInVMTest {
    private val dispatcher = StandardTestDispatcher()
    private val persist = mockk<PersistServerSelectionUseCase>(relaxed = true)
    private val optOut = mockk<OptOutOfOhttpUseCase>(relaxed = true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm() = OhttpOptInVM(persist, optOut, navigationRouter)

    @Test
    fun turnOnSwitchesToPrivateAndCloses() =
        runTest(dispatcher) {
            createVm().state.value.onTurnOn()
            advanceUntilIdle()
            coVerify(exactly = 1) { persist.persistPrivate() }
            coVerify(exactly = 0) { optOut() }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun notNowOptsOutForGoodAndCloses() =
        runTest(dispatcher) {
            createVm().state.value.onNotNow()
            advanceUntilIdle()
            coVerify(exactly = 1) { optOut() }
            coVerify(exactly = 0) { persist.persistPrivate() }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun closingTheScreenKeepsTheWidget() =
        runTest(dispatcher) {
            createVm().state.value.onBack()
            advanceUntilIdle()
            coVerify(exactly = 0) { optOut() }
            coVerify(exactly = 0) { persist.persistPrivate() }
            verify(exactly = 1) { navigationRouter.back() }
        }
}
