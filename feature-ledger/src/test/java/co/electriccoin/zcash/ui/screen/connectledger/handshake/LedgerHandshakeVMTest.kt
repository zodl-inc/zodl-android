package co.electriccoin.zcash.ui.screen.connectledger.handshake

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerPairingTimedOutException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceResult
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthw.neworactive.HWNewOrActiveArgs
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The handshake screen asks the device the scan screen bonded with for its account as soon as it
 * opens, hands a fresh pairing on to the New/Active question and a re-bound account on to
 * Connected, and maps failures to sheets in which a lost link reads as a disconnect during setup:
 * the phone bonded with the device on the scan screen already.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerHandshakeVMTest {
    private val dispatcher = StandardTestDispatcher()

    private val device =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA",
            rssi = -40,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theHandshakeStartsOnItsOwnAndAFreshPairingReplacesTheScreenWithNewOrActive() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns PairLedgerDeviceResult.Paired
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            coVerify(exactly = 1) { pairLedgerDevice.invoke(device) }
            verify(exactly = 1) { navigationRouter.replace(HWNewOrActiveArgs(HWWalletEnrollment.Ledger)) }
            verify(exactly = 0) { navigationRouter.forward(HWNewOrActiveArgs(HWWalletEnrollment.Ledger)) }
        }

    @Test
    fun whileTheHandshakeRunsThePageWaitsBehindADisabledConnect() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<PairLedgerDeviceResult>()
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers { pending.await() }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()

            val state = vm.state.value
            assertTrue(state.isConnecting)
            assertEquals(R.string.ledger_handshake_title, state.title.resourceId())
            assertEquals(R.string.ledger_handshake_message, state.message.resourceId())
            assertEquals(R.string.ledger_scan_select_cta, state.primaryButton.text.resourceId())
            assertFalse(state.primaryButton.isEnabled)
            assertNull(state.errorSheet)
        }

    @Test
    fun aMissingDeviceFallsBackToTheWalletRootWithoutTalkingToAnyDevice() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice = mockk<PairLedgerDeviceUseCase>(relaxed = true)
            val vm =
                vm(
                    pairLedgerDevice = pairLedgerDevice,
                    navigationRouter = navigationRouter,
                    selectedDevice = null,
                )
            collect(vm)
            runCurrent()

            verify(exactly = 1) { navigationRouter.backToRoot() }
            coVerify(exactly = 0) { pairLedgerDevice.invoke(any()) }
        }

    /**
     * Every failure also leaves an enabled page button behind the sheet, Retry or the sheet's own
     * action, so dismissing the sheet never strands the user on a dead page.
     */
    @Test
    fun handshakeFailuresMapToTheirSheetsAndLeaveAnEnabledPageButton() =
        runTest(dispatcher) {
            mapOf(
                mockk<LedgerException.WrongApp>(relaxed = true) {
                    every { statusWord } returns WRONG_APP_STATUS
                } to R.string.ledger_error_locked_title,
                mockk<LedgerException.WrongApp>(relaxed = true) {
                    every { statusWord } returns null
                } to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.DeviceRefused>(relaxed = true) {
                    every { isTransient } returns true
                } to R.string.ledger_error_locked_title,
                mockk<LedgerException.DeviceRefused>(relaxed = true) {
                    every { isTransient } returns false
                } to R.string.ledger_error_unknown_title,
                mockk<LedgerException.UserRejected>(relaxed = true) to R.string.ledger_error_importRejected_title,
                mockk<LedgerException.AppTooOld>(relaxed = true) to R.string.ledger_error_appTooOld_title,
                mockk<LedgerException.DerivationBudgetExhausted>(relaxed = true) to
                    R.string.ledger_error_restartApp_title,
                mockk<LedgerException.Disconnected>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.ConnectionFailed>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.DeviceNotFound>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.Timeout>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.PairingRefused>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                LedgerPairingTimedOutException() to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.BluetoothDisabled>(relaxed = true) to R.string.ledger_error_bluetoothOff_title,
                mockk<LedgerException.BluetoothUnauthorized>(relaxed = true) to
                    R.string.ledger_error_permissions_title,
                mockk<LedgerException.CapsMismatch>(relaxed = true) to R.string.ledger_error_unknown_title,
                mockk<LedgerException.AppNotInstalled>(relaxed = true) to R.string.ledger_error_appNotInstalled_title,
                mockk<LedgerException.AppOpenRejected>(relaxed = true) to R.string.ledger_error_openAppRejected_title,
                mockk<LedgerException.DeviceMismatch>(relaxed = true) to R.string.ledger_error_unknown_title,
                mockk<LedgerException.MalformedReply>(relaxed = true) to R.string.ledger_error_unknown_title,
                mockk<LedgerException.Internal>(relaxed = true) to R.string.ledger_error_unknown_title,
                mockk<LedgerException.InvalidInput>(relaxed = true) to R.string.ledger_error_unknown_title,
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) {
                    every { scanErrorCode } returns 1
                } to R.string.ledger_error_noDevices_title,
            ).forEach { (exception, expectedTitle) ->
                val vm = failingWith(exception)
                val label = exception.javaClass.simpleName

                val sheet = assertNotNull(vm.state.value.errorSheet, label)
                assertEquals(expectedTitle, sheet.title.resourceId(), label)
                assertFalse(vm.state.value.isConnecting, label)
                val button = vm.state.value.primaryButton
                assertTrue(button.isEnabled, label)
                val sheetAction = assertNotNull(sheet.primary, label).text.resourceId()
                assertTrue(
                    button.text.resourceId() in setOf(R.string.ledger_handshake_retry, sheetAction),
                    label
                )
            }
        }

    @Test
    fun aDeviceThatRefusesForGoodReadsAsSomethingWentWrongWithRetry() =
        runTest(dispatcher) {
            val vm =
                failingWith(
                    mockk<LedgerException.DeviceRefused>(relaxed = true) {
                        every { isTransient } returns false
                    }
                )

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_unknown_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_unknown_message, sheet.message.resourceId())
            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_handshake_retry, button.text.resourceId())
            assertTrue(button.isEnabled)
        }

    @Test
    fun aLostConnectionReadsAsADisconnectDuringSetupRatherThanAFailedPairing() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.ConnectionFailed>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_disconnected_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_disconnected_message, sheet.message.resourceId())
        }

    @Test
    fun aHandshakeThatTimedOutReadsAsADisconnectDuringSetupWithTryAgainAndRetry() =
        runTest(dispatcher) {
            val vm = failingWith(LedgerPairingTimedOutException())

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_disconnected_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_disconnected_message, sheet.message.resourceId())
            assertEquals(R.string.ledger_error_tryAgain, assertNotNull(sheet.primary).text.resourceId())
            assertEquals(
                R.string.ledger_handshake_retry,
                vm.state.value.primaryButton.text
                    .resourceId()
            )
        }

    @Test
    fun anIssueThatCannotBeRetriedOffersNoTryAgainAnywhere() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.TransactionNotSignable>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertNull(sheet.primary)
            assertNull(sheet.secondary)
            assertEquals(
                R.string.ledger_scan_select_cta,
                vm.state.value.primaryButton.text
                    .resourceId()
            )
            assertFalse(vm.state.value.primaryButton.isEnabled)
        }

    @Test
    fun aReboundAccountReplacesTheScreenWithConnected() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns PairLedgerDeviceResult.Rebound(mockk(relaxed = true))
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            verify(exactly = 1) { navigationRouter.replace(LedgerConnectedArgs) }
            verify(exactly = 0) { navigationRouter.replace(HWNewOrActiveArgs(HWWalletEnrollment.Ledger)) }
            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun theWrongLedgerShowsItsIssueWithTryAgainAndGoesNowhere() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns PairLedgerDeviceResult.WrongLedger
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_sign_error_wrongDevice_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_sign_error_wrongDevice_message, sheet.message.resourceId())
            assertEquals(
                R.string.ledger_handshake_retry,
                vm.state.value.primaryButton.text
                    .resourceId()
            )
            assertFalse(vm.state.value.isConnecting)
            verify(exactly = 0) { navigationRouter.replace(*anyVararg()) }
        }

    @Test
    fun tryAgainRunsTheHandshakeAgainOnTheSameDevice() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            var attempts = 0
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers {
                        attempts++
                        if (attempts == 1) {
                            throw mockk<LedgerException.WrongApp>(relaxed = true)
                        }
                        PairLedgerDeviceResult.Paired
                    }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_tryAgain, assertNotNull(sheet.primary).text.resourceId())
            assertNotNull(sheet.primary).onClick()
            runCurrent()

            coVerify(exactly = 2) { pairLedgerDevice.invoke(device) }
            verify(exactly = 1) { navigationRouter.replace(HWNewOrActiveArgs(HWWalletEnrollment.Ledger)) }
        }

    @Test
    fun dismissingTheSheetLeavesTheHeaderAndAnEnabledRetry() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.UserRejected>(relaxed = true))

            assertNotNull(vm.state.value.errorSheet).onBack()
            runCurrent()

            assertNull(vm.state.value.errorSheet)
            assertFalse(vm.state.value.isConnecting)
            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_handshake_retry, button.text.resourceId())
            assertTrue(button.isEnabled)
            assertEquals(
                R.string.ledger_handshake_title,
                vm.state.value.title
                    .resourceId()
            )
        }

    @Test
    fun anAccountAlreadyInTheWalletShowsTheAlreadyAddedSheet() =
        runTest(dispatcher) {
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns
                        PairLedgerDeviceResult.AlreadyAdded(mockk(relaxed = true))
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_alreadyAdded_title, sheet.title.resourceId())
            assertEquals(ButtonStyle.SECONDARY, assertNotNull(sheet.secondary).style)

            assertNotNull(sheet.secondary).onClick()
            runCurrent()

            assertNull(vm.state.value.errorSheet)
            assertEquals(
                R.string.ledger_error_alreadyAdded_primary,
                vm.state.value.primaryButton.text
                    .resourceId()
            )
        }

    @Test
    fun goToAccountSelectsTheExistingAccountAndUnwindsToTheRoot() =
        runTest(dispatcher) {
            val existing = mockk<WalletAccount>(relaxed = true)
            val selectWalletAccount = mockk<SelectWalletAccountUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns PairLedgerDeviceResult.AlreadyAdded(existing)
                }
            val vm =
                vm(
                    pairLedgerDevice = pairLedgerDevice,
                    selectWalletAccount = selectWalletAccount,
                    navigationRouter = navigationRouter,
                )
            collect(vm)
            runCurrent()

            assertNotNull(
                vm.state.value.errorSheet
                    ?.primary
            ).onClick()
            runCurrent()

            coVerify(exactly = 1) { selectWalletAccount.invoke(existing, false) }
            verify(exactly = 0) { navigationRouter.back() }
            verify(exactly = 1) { navigationRouter.backToRoot() }
        }

    @Test
    fun deniedPermissionsShowThePermissionsSheetAndGrantingThemRestartsTheHandshake() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<PairLedgerDeviceResult>()
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers { pending.await() }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()

            vm.onPermissionsDenied(false)
            runCurrent()
            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_permissions_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_permissions_cta, assertNotNull(sheet.primary).text.resourceId())
            assertFalse(vm.state.value.isConnecting)

            vm.onPermissionsGranted()
            runCurrent()

            assertTrue(vm.state.value.isConnecting)
            assertNull(vm.state.value.errorSheet)
            coVerify(exactly = 2) { pairLedgerDevice.invoke(device) }
        }

    @Test
    fun retryPairsTheSameDeviceAgainAndWaitsBehindADisabledConnect() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<PairLedgerDeviceResult>()
            var attempts = 0
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers {
                        attempts++
                        if (attempts == 1) {
                            throw mockk<LedgerException.UserRejected>(relaxed = true)
                        }
                        pending.await()
                    }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()
            assertNotNull(vm.state.value.errorSheet).onBack()
            runCurrent()

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 2) { pairLedgerDevice.invoke(device) }
            assertTrue(vm.state.value.isConnecting)
            assertNull(vm.state.value.errorSheet)
            assertEquals(
                R.string.ledger_scan_select_cta,
                vm.state.value.primaryButton.text
                    .resourceId()
            )
            assertFalse(vm.state.value.primaryButton.isEnabled)
        }

    @Test
    fun aResumeWhileTheHandshakeRunsDoesNotStartASecondOne() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<PairLedgerDeviceResult>()
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers { pending.await() }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()

            vm.onPermissionsGranted()
            runCurrent()

            coVerify(exactly = 1) { pairLedgerDevice.invoke(device) }
        }

    @Test
    fun aRestartWhileTheHandshakeRunsLeavesItAloneEvenWithoutASelectedDevice() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<PairLedgerDeviceResult>()
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers { pending.await() }
                }
            val ledgerSelectedDeviceRepository =
                mockk<LedgerSelectedDeviceRepository>(relaxed = true) {
                    every { get() } returnsMany listOf(device, null)
                }
            val vm =
                LedgerHandshakeVM(
                    application = mockk<Application>(relaxed = true),
                    pairLedgerDevice = pairLedgerDevice,
                    selectWalletAccount = mockk(relaxed = true),
                    ledgerSelectedDeviceRepository = ledgerSelectedDeviceRepository,
                    navigateToError = mockk(relaxed = true),
                    navigationRouter = navigationRouter,
                )
            collect(vm)
            runCurrent()

            vm.onBluetoothEnabled()
            runCurrent()

            verify(exactly = 0) { navigationRouter.backToRoot() }
            coVerify(exactly = 1) { pairLedgerDevice.invoke(device) }
            assertTrue(vm.state.value.isConnecting)
        }

    @Test
    fun bluetoothOffAsksTheSystemToTurnItOnAndRetriesOnceItIs() =
        runTest(dispatcher) {
            var attempts = 0
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers {
                        attempts++
                        if (attempts == 1) {
                            throw mockk<LedgerException.BluetoothDisabled>(relaxed = true)
                        }
                        CompletableDeferred<PairLedgerDeviceResult>().await()
                    }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_bluetoothOff_title, sheet.title.resourceId())
            assertNotNull(sheet.primary).onClick()
            runCurrent()
            assertNull(vm.state.value.errorSheet)
            assertEquals(1, vm.state.value.enableBluetoothRequestNonce)

            vm.onBluetoothEnableDeclined()
            runCurrent()
            assertNotNull(vm.state.value.errorSheet)

            vm.onBluetoothEnabled()
            runCurrent()
            assertTrue(vm.state.value.isConnecting)
            coVerify(exactly = 2) { pairLedgerDevice.invoke(device) }
        }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun aNonLedgerFailureGoesToTheGeneralErrorScreenAndLeavesARetryBehind() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val failure = IllegalStateException("boom")
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } throws failure
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigateToError = navigateToError)
            collect(vm)
            runCurrent()

            verify(exactly = 1) { navigateToError.invoke(ErrorArgs.General(failure), any()) }
            assertNull(vm.state.value.errorSheet)
            assertTrue(vm.state.value.primaryButton.isEnabled)
        }

    @Test
    fun backReturnsToTheOpenTheAppScreen() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = failingWith(mockk<LedgerException.UserRejected>(relaxed = true), navigationRouter)

            vm.state.value.onBack()

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun backCancelsARunningHandshake() =
        runTest(dispatcher) {
            var isCancelled = false
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers {
                        try {
                            awaitCancellation()
                        } finally {
                            isCancelled = true
                        }
                    }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()
            assertFalse(isCancelled)

            vm.state.value.onBack()
            runCurrent()

            assertTrue(isCancelled)
            verify(exactly = 1) { navigationRouter.back() }
        }

    /**
     * The device can still answer while the screen is on its way out: a pairing that completes
     * after back must not replace the open-the-app screen the user returned to.
     */
    @Test
    fun aPairingThatCompletesAfterBackDoesNotNavigate() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pending = CompletableDeferred<PairLedgerDeviceResult>()
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers {
                        withContext(NonCancellable) { pending.await() }
                    }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            vm.state.value.onBack()
            runCurrent()
            pending.complete(PairLedgerDeviceResult.Paired)
            runCurrent()

            verify(exactly = 1) { navigationRouter.back() }
            verify(exactly = 0) { navigationRouter.replace(*anyVararg()) }
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun leavingTheScreenCancelsARunningHandshake() =
        runTest(dispatcher) {
            var isCancelled = false
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers {
                        try {
                            awaitCancellation()
                        } finally {
                            isCancelled = true
                        }
                    }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()
            assertFalse(isCancelled)

            vm.triggerOnCleared()
            runCurrent()

            assertTrue(isCancelled)
        }

    private fun TestScope.failingWith(
        exception: Exception,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ): LedgerHandshakeVM {
        val pairLedgerDevice =
            mockk<PairLedgerDeviceUseCase> {
                coEvery { this@mockk.invoke(any()) } throws exception
            }
        val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
        collect(vm)
        runCurrent()
        return vm
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

    /**
     * `onCleared` is protected on `ViewModel`, and the test needs the real disposal path rather
     * than a stand-in for it.
     */
    private fun LedgerHandshakeVM.triggerOnCleared() {
        LedgerHandshakeVM::class.java
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(this)
    }

    private fun TestScope.collect(vm: LedgerHandshakeVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun vm(
        pairLedgerDevice: PairLedgerDeviceUseCase = mockk(relaxed = true),
        selectWalletAccount: SelectWalletAccountUseCase = mockk(relaxed = true),
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        selectedDevice: LedgerBluetoothDevice? = device,
    ) = LedgerHandshakeVM(
        application = mockk<Application>(relaxed = true),
        pairLedgerDevice = pairLedgerDevice,
        selectWalletAccount = selectWalletAccount,
        ledgerSelectedDeviceRepository =
            mockk<LedgerSelectedDeviceRepository>(relaxed = true) {
                every { get() } returns selectedDevice
            },
        navigateToError = navigateToError,
        navigationRouter = navigationRouter,
    )
}

private const val WRONG_APP_STATUS = 0x6E00
