package co.electriccoin.zcash.ui.screen.viewingkey

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.usecase.GetViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RemoveViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewOnlyAccountFixtures
import co.electriccoin.zcash.ui.screen.accountlist.AccountListArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.viewingkey.remove.RemoveVKAccountArgs
import co.electriccoin.zcash.ui.screen.viewingkey.remove.RemoveVKAccountVM
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The removal sheet removes the account once however often Remove Account is tapped, stays up and
 * ignores back while removing, hides before it returns to the account list, survives a dismissed or
 * failed authentication unchanged, shows the error screen for any other failure, and goes straight
 * back for an id that names no view-only account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoveVKAccountVMTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

    private val removeViewOnlyAccount =
        mockk<RemoveViewOnlyAccountUseCase> {
            coEvery { this@mockk.invoke(any()) } returns true
        }

    private val accounts =
        MutableStateFlow<List<WalletAccount>?>(
            listOf(ViewOnlyAccountFixtures.zodl(), ViewOnlyAccountFixtures.viewOnly(isSelected = true))
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theSheetOpensReadyToRemove() =
        runTest {
            val vm = vm()
            collect(vm)

            val state = assertNotNull(vm.state.value)
            assertTrue(state.removeButton.isEnabled)
            assertFalse(state.removeButton.isLoading)
            assertTrue(state.cancelButton.isEnabled)
            assertTrue(state.isDismissable)
        }

    @Test
    fun removingHidesTheSheetBeforeItReturnsToTheAccountList() =
        runTest {
            val vm = vm()
            collect(vm)

            assertNotNull(vm.state.value).removeButton.onClick()
            runCurrent()

            coVerify(exactly = 1) { removeViewOnlyAccount(ViewOnlyAccountFixtures.viewOnlyKeyId) }
            assertNull(vm.state.value)
            verify(exactly = 0) { navigationRouter.backTo(any()) }

            advanceTimeBy(SHEET_HIDE_DELAY_MS + 1)

            verify(exactly = 1) { navigationRouter.backTo(AccountListArgs::class) }
        }

    @Test
    fun aDoubleTapRemovesOnceAndTheSheetStaysUpMeanwhile() =
        runTest {
            val removed = CompletableDeferred<Boolean>()
            coEvery { removeViewOnlyAccount(any()) } coAnswers { removed.await() }
            val vm = vm()
            collect(vm)

            val remove = assertNotNull(vm.state.value).removeButton
            remove.onClick()
            runCurrent()
            remove.onClick()
            runCurrent()

            val removing = assertNotNull(vm.state.value)
            assertTrue(removing.removeButton.isLoading)
            assertFalse(removing.removeButton.isEnabled)
            assertFalse(removing.cancelButton.isEnabled)
            assertFalse(removing.isDismissable)

            removing.onBack()
            removing.cancelButton.onClick()
            verify(exactly = 0) { navigationRouter.back() }

            removed.complete(true)
            advanceTimeBy(SHEET_HIDE_DELAY_MS + 1)

            coVerify(exactly = 1) { removeViewOnlyAccount(any()) }
            verify(exactly = 1) { navigationRouter.backTo(AccountListArgs::class) }
            verify(exactly = 0) { navigationRouter.back() }
        }

    @Test
    fun aDismissedAuthenticationLeavesTheSheetAsItWas() =
        runTest {
            coEvery { removeViewOnlyAccount(any()) } throws BiometricsCancelledException()
            val vm = vm()
            collect(vm)

            assertNotNull(vm.state.value).removeButton.onClick()
            advanceTimeBy(SHEET_HIDE_DELAY_MS + 1)

            val state = assertNotNull(vm.state.value)
            assertTrue(state.removeButton.isEnabled)
            assertTrue(state.isDismissable)
            verify(exactly = 0) { navigationRouter.backTo(any()) }
            verify(exactly = 0) { navigateToError(any(), any()) }
        }

    @Test
    fun aFailedAuthenticationLeavesTheSheetAsItWas() =
        runTest {
            coEvery { removeViewOnlyAccount(any()) } throws BiometricsFailureException()
            val vm = vm()
            collect(vm)

            assertNotNull(vm.state.value).removeButton.onClick()
            advanceTimeBy(SHEET_HIDE_DELAY_MS + 1)

            assertTrue(assertNotNull(vm.state.value).removeButton.isEnabled)
            verify(exactly = 0) { navigationRouter.backTo(any()) }
            verify(exactly = 0) { navigateToError(any(), any()) }
        }

    @Test
    fun anyOtherFailureShowsTheErrorScreen() =
        runTest {
            val failure = IllegalStateException("deletion failed")
            coEvery { removeViewOnlyAccount(any()) } throws failure
            val vm = vm()
            collect(vm)

            assertNotNull(vm.state.value).removeButton.onClick()
            advanceTimeBy(SHEET_HIDE_DELAY_MS + 1)

            verify(exactly = 1) { navigateToError(ErrorArgs.General(failure), any()) }
            assertTrue(assertNotNull(vm.state.value).removeButton.isEnabled)
            verify(exactly = 0) { navigationRouter.backTo(any()) }
        }

    @Test
    fun cancelClosesTheSheet() =
        runTest {
            val vm = vm()
            collect(vm)

            assertNotNull(vm.state.value).cancelButton.onClick()

            verify(exactly = 1) { navigationRouter.back() }
            coVerify(exactly = 0) { removeViewOnlyAccount(any()) }
        }

    @Test
    fun aMissingAccountGoesBack() =
        runTest {
            accounts.value = listOf(ViewOnlyAccountFixtures.zodl())

            collect(vm())

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun theZodlAccountIsNeverOfferedForRemoval() =
        runTest {
            collect(vm(accountKeyId = ViewOnlyAccountFixtures.zodlKeyId))

            verify(exactly = 1) { navigationRouter.back() }
        }

    private fun vm(accountKeyId: String = ViewOnlyAccountFixtures.viewOnlyKeyId) =
        RemoveVKAccountVM(
            args = RemoveVKAccountArgs(accountKeyId),
            getViewOnlyAccount = GetViewOnlyAccountUseCase(ViewOnlyAccountFixtures.getWalletAccounts(accounts)),
            removeViewOnlyAccount = removeViewOnlyAccount,
            navigateToError = navigateToError,
            navigationRouter = navigationRouter,
        )

    private fun TestScope.collect(vm: RemoveVKAccountVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
    }

    private companion object {
        const val SHEET_HIDE_DELAY_MS = 350L
    }
}
