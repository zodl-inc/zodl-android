package co.electriccoin.zcash.ui.screen.connectledger.openapp

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerPairingTimedOutException
import co.electriccoin.zcash.ui.common.usecase.OpenLedgerZcashAppResult
import co.electriccoin.zcash.ui.common.usecase.OpenLedgerZcashAppUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The open-the-app screen asks the selected Ledger to open the Zcash app as soon as it opens, moves
 * on to the handshake once it runs, and maps failures to the same sheets as the other enrollment
 * screens. It never asks on its own again, so returning from the handshake leaves it idle.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerOpenAppVMTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theRequestStartsOnItsOwnWithTheButtonLoadingAndDisabled() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<OpenLedgerZcashAppResult>()
            val openLedgerZcashApp =
                mockk<OpenLedgerZcashAppUseCase> {
                    coEvery { this@mockk.invoke() } coAnswers { pending.await() }
                }
            val vm = vm(openLedgerZcashApp = openLedgerZcashApp)
            collect(vm)
            runCurrent()

            coVerify(exactly = 1) { openLedgerZcashApp.invoke() }
            val button = vm.state.value.primaryButton
            assertTrue(button.isLoading)
            assertFalse(button.isEnabled)
            assertEquals(R.string.ledger_connect_continue, button.text.resourceId())
            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun anOpenedAppGoesOnToTheHandshakeAndLeavesThePageIdle() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val openLedgerZcashApp = openingWith(OpenLedgerZcashAppResult.Opened)
            val vm = vm(openLedgerZcashApp = openLedgerZcashApp, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            verify(exactly = 1) { navigationRouter.forward(LedgerHandshakeArgs) }
            val button = vm.state.value.primaryButton
            assertFalse(button.isLoading)
            assertTrue(button.isEnabled)
            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun returningFromTheHandshakeDoesNotAskAgainUntilTheButtonIsTapped() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val openLedgerZcashApp = openingWith(OpenLedgerZcashAppResult.Opened)
            val vm = vm(openLedgerZcashApp = openLedgerZcashApp, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            vm.onPermissionsGranted()
            runCurrent()
            coVerify(exactly = 1) { openLedgerZcashApp.invoke() }

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 2) { openLedgerZcashApp.invoke() }
            verify(exactly = 2) { navigationRouter.forward(LedgerHandshakeArgs) }
        }

    @Test
    fun withoutAutoOpenThePageStartsIdleAndAsksOnlyWhenTheButtonIsTapped() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val openLedgerZcashApp = openingWith(OpenLedgerZcashAppResult.Opened)
            val vm =
                vm(
                    openLedgerZcashApp = openLedgerZcashApp,
                    navigationRouter = navigationRouter,
                    args = LedgerOpenAppArgs(autoOpen = false),
                )
            collect(vm)
            runCurrent()

            coVerify(exactly = 0) { openLedgerZcashApp.invoke() }
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_connect_continue, button.text.resourceId())
            assertTrue(button.isEnabled)
            assertFalse(button.isLoading)
            assertNull(vm.state.value.errorSheet)

            vm.onPermissionsGranted()
            runCurrent()
            coVerify(exactly = 0) { openLedgerZcashApp.invoke() }

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 1) { openLedgerZcashApp.invoke() }
            verify(exactly = 1) { navigationRouter.forward(LedgerHandshakeArgs) }
        }

    @Test
    fun aDeclinedRequestShowsItsSheetAndEnablesTheButton() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.AppOpenRejected>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_openAppRejected_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_tryAgain, assertNotNull(sheet.primary).text.resourceId())
            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_connect_continue, button.text.resourceId())
            assertTrue(button.isEnabled)
            assertFalse(button.isLoading)
        }

    @Test
    fun aMissingAppShowsTheInstallSheet() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.AppNotInstalled>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_appNotInstalled_title, sheet.title.resourceId())
        }

    @Test
    fun aLostConnectionReadsAsADisconnectDuringSetupRatherThanAFailedPairing() =
        runTest(dispatcher) {
            listOf(
                mockk<LedgerException.ConnectionFailed>(relaxed = true),
                mockk<LedgerException.DeviceNotFound>(relaxed = true),
                mockk<LedgerException.Timeout>(relaxed = true),
                mockk<LedgerException.Disconnected>(relaxed = true),
            ).forEach { exception ->
                val sheet = assertNotNull(failingWith(exception).state.value.errorSheet)
                assertEquals(R.string.ledger_error_disconnected_title, sheet.title.resourceId())
                assertEquals(R.string.ledger_error_disconnected_message, sheet.message.resourceId())
            }
        }

    @Test
    fun aRequestThatRunsOutOfTimeReadsAsADisconnectAndTryAgainAsksAgain() =
        runTest(dispatcher) {
            var attempts = 0
            val openLedgerZcashApp =
                mockk<OpenLedgerZcashAppUseCase> {
                    coEvery { this@mockk.invoke() } coAnswers {
                        attempts++
                        throw LedgerPairingTimedOutException()
                    }
                }
            val vm = vm(openLedgerZcashApp = openLedgerZcashApp)
            collect(vm)
            runCurrent()

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_disconnected_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_disconnected_message, sheet.message.resourceId())
            val primary = assertNotNull(sheet.primary)
            assertEquals(R.string.ledger_error_tryAgain, primary.text.resourceId())

            primary.onClick()
            runCurrent()

            assertEquals(2, attempts)
        }

    @Test
    fun anIssueThatCannotBeRetriedOffersNoTryAgainAnywhere() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.TransactionNotSignable>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertNull(sheet.primary)
            assertNull(sheet.secondary)
            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_connect_continue, button.text.resourceId())
            assertFalse(button.isEnabled)
            assertFalse(button.isLoading)
        }

    @Test
    fun thePageButtonAsksAgainAfterADecline() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val openLedgerZcashApp = declinedOnceThen(OpenLedgerZcashAppResult.Opened)
            val vm = vm(openLedgerZcashApp = openLedgerZcashApp, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            assertNotNull(vm.state.value.errorSheet).onBack()
            runCurrent()
            assertNull(vm.state.value.errorSheet)

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 2) { openLedgerZcashApp.invoke() }
            verify(exactly = 1) { navigationRouter.forward(LedgerHandshakeArgs) }
            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun theSheetPrimaryAsksAgainWithTheButtonLoading() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<OpenLedgerZcashAppResult>()
            var attempts = 0
            val openLedgerZcashApp =
                mockk<OpenLedgerZcashAppUseCase> {
                    coEvery { this@mockk.invoke() } coAnswers {
                        attempts++
                        if (attempts == 1) {
                            throw mockk<LedgerException.AppOpenRejected>(relaxed = true)
                        }
                        pending.await()
                    }
                }
            val vm = vm(openLedgerZcashApp = openLedgerZcashApp)
            collect(vm)
            runCurrent()

            assertNotNull(
                vm.state.value.errorSheet
                    ?.primary
            ).onClick()
            runCurrent()

            coVerify(exactly = 2) { openLedgerZcashApp.invoke() }
            assertNull(vm.state.value.errorSheet)
            val button = vm.state.value.primaryButton
            assertTrue(button.isLoading)
            assertFalse(button.isEnabled)
        }

    @Test
    fun aMissingDeviceFallsBackToTheWalletRoot() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm =
                vm(
                    openLedgerZcashApp = openingWith(OpenLedgerZcashAppResult.NoDevice),
                    navigationRouter = navigationRouter,
                )
            collect(vm)
            runCurrent()

            verify(exactly = 1) { navigationRouter.backToRoot() }
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
        }

    @Test
    fun aNonLedgerFailureGoesToTheGeneralErrorScreenAndLeavesARetryBehind() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val failure = IllegalStateException("boom")
            val openLedgerZcashApp =
                mockk<OpenLedgerZcashAppUseCase> {
                    coEvery { this@mockk.invoke() } throws failure
                }
            val vm = vm(openLedgerZcashApp = openLedgerZcashApp, navigateToError = navigateToError)
            collect(vm)
            runCurrent()

            verify(exactly = 1) { navigateToError.invoke(ErrorArgs.General(failure), any()) }
            assertNull(vm.state.value.errorSheet)
            assertEquals(
                R.string.ledger_connect_continue,
                vm.state.value.primaryButton.text
                    .resourceId()
            )
            assertTrue(vm.state.value.primaryButton.isEnabled)
        }

    @Test
    fun backCancelsARunningRequestAndReturnsToTheDevicePicker() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            var isCancelled = false
            val vm =
                vm(
                    openLedgerZcashApp = cancellable { isCancelled = true },
                    navigationRouter = navigationRouter,
                )
            collect(vm)
            runCurrent()

            vm.state.value.onBack()
            runCurrent()

            assertTrue(isCancelled)
            verify(exactly = 1) { navigationRouter.back() }
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
        }

    @Test
    fun leavingTheScreenCancelsARunningRequest() =
        runTest(dispatcher) {
            var isCancelled = false
            val vm = vm(openLedgerZcashApp = cancellable { isCancelled = true })
            collect(vm)
            runCurrent()
            assertFalse(isCancelled)

            vm.triggerOnCleared()
            runCurrent()

            assertTrue(isCancelled)
        }

    @Test
    fun deniedPermissionsShowThePermissionsSheetAndGrantingThemAsksAgain() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<OpenLedgerZcashAppResult>()
            val openLedgerZcashApp =
                mockk<OpenLedgerZcashAppUseCase> {
                    coEvery { this@mockk.invoke() } coAnswers { pending.await() }
                }
            val vm = vm(openLedgerZcashApp = openLedgerZcashApp)
            collect(vm)
            runCurrent()

            vm.onPermissionsDenied(false)
            runCurrent()
            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_permissions_title, sheet.title.resourceId())
            assertFalse(vm.state.value.primaryButton.isLoading)

            vm.onPermissionsGranted()
            runCurrent()

            assertTrue(vm.state.value.primaryButton.isLoading)
            assertNull(vm.state.value.errorSheet)
            coVerify(exactly = 2) { openLedgerZcashApp.invoke() }
        }

    @Test
    fun thePageButtonKeepsContinueUnlessOnlySettingsCanFixTheIssue() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<OpenLedgerZcashAppResult>()
            val openLedgerZcashApp =
                mockk<OpenLedgerZcashAppUseCase> {
                    coEvery { this@mockk.invoke() } coAnswers { pending.await() }
                }
            val denied = vm(openLedgerZcashApp = openLedgerZcashApp)
            collect(denied)
            runCurrent()
            denied.onPermissionsDenied(false)
            runCurrent()
            assertEquals(
                R.string.ledger_error_permissions_cta,
                denied.state.value.primaryButton.text
                    .resourceId()
            )

            val declined = failingWith(mockk<LedgerException.AppOpenRejected>(relaxed = true))
            assertEquals(
                R.string.ledger_connect_continue,
                declined.state.value.primaryButton.text
                    .resourceId()
            )
            assertEquals(
                R.string.ledger_error_tryAgain,
                assertNotNull(declined.state.value.errorSheet?.primary).text.resourceId()
            )
        }

    private fun openingWith(result: OpenLedgerZcashAppResult) =
        mockk<OpenLedgerZcashAppUseCase> {
            coEvery { this@mockk.invoke() } returns result
        }

    private fun declinedOnceThen(result: OpenLedgerZcashAppResult): OpenLedgerZcashAppUseCase {
        var attempts = 0
        return mockk {
            coEvery { this@mockk.invoke() } coAnswers {
                attempts++
                if (attempts == 1) {
                    throw mockk<LedgerException.AppOpenRejected>(relaxed = true)
                }
                result
            }
        }
    }

    private fun cancellable(onCancelled: () -> Unit) =
        mockk<OpenLedgerZcashAppUseCase> {
            coEvery { this@mockk.invoke() } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    onCancelled()
                }
            }
        }

    private fun TestScope.failingWith(exception: LedgerException): LedgerOpenAppVM {
        val openLedgerZcashApp =
            mockk<OpenLedgerZcashAppUseCase> {
                coEvery { this@mockk.invoke() } throws exception
            }
        val vm = vm(openLedgerZcashApp = openLedgerZcashApp)
        collect(vm)
        runCurrent()
        return vm
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

    /**
     * `onCleared` is protected on `ViewModel`, and the test needs the real disposal path rather
     * than a stand-in for it.
     */
    private fun LedgerOpenAppVM.triggerOnCleared() {
        LedgerOpenAppVM::class.java
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(this)
    }

    private fun TestScope.collect(vm: LedgerOpenAppVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun vm(
        openLedgerZcashApp: OpenLedgerZcashAppUseCase = mockk(relaxed = true),
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        args: LedgerOpenAppArgs = LedgerOpenAppArgs(),
    ) = LedgerOpenAppVM(
        args = args,
        application = mockk<Application>(relaxed = true),
        openLedgerZcashApp = openLedgerZcashApp,
        navigateToError = navigateToError,
        navigationRouter = navigationRouter,
    )
}
