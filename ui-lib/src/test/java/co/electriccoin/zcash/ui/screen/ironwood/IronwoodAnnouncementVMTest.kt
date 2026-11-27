package co.electriccoin.zcash.ui.screen.ironwood

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.WalletRepository
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
import kotlin.test.Test

/**
 * Regression coverage for the double-shown Ironwood announcement: the "shown" flag must be
 * persisted when the screen is displayed, before any dismissal, so that Home's observed state
 * flow cannot retain a stale "show it" value and re-trigger the navigation on return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IronwoodAnnouncementVMTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val walletRepository = mockk<WalletRepository>(relaxed = true)

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun announcementIsMarkedShownOnDisplayBeforeAnyDismissal() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            IronwoodAnnouncementVM(navigationRouter, walletRepository)
            advanceUntilIdle()

            coVerify(exactly = 1) { walletRepository.markIronwoodAnnouncementShown() }
            verify(exactly = 0) { navigationRouter.back() }
        }

    @Test
    fun primaryButtonOnlyNavigatesBack() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            val vm = IronwoodAnnouncementVM(navigationRouter, walletRepository)
            advanceUntilIdle()

            vm.state.value.primaryButton
                .onClick()
            advanceUntilIdle()

            verify(exactly = 1) { navigationRouter.back() }
            coVerify(exactly = 1) { walletRepository.markIronwoodAnnouncementShown() }
        }
}
