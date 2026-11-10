package co.electriccoin.zcash.ui.screen.zns.settings

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsZnsNamesEnabledStorageProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ZnsSettingsVMTest {
    private val stored = MutableStateFlow(false)
    private val storage =
        mockk<IsZnsNamesEnabledStorageProvider>(relaxed = true) {
            every { observe() } returns stored
        }
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun namesStartOffAndSavingTurnsThemOn() =
        runTest {
            val vm = ZnsSettingsVM(storage, navigationRouter)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()

            assertEquals(false, vm.state.value?.isEnabled)

            vm.state.value
                ?.onSaveClick
                ?.invoke(true)
            runCurrent()

            coVerify(exactly = 1) { storage.store(true) }
            verify(exactly = 1) { navigationRouter.back() }
        }
}
