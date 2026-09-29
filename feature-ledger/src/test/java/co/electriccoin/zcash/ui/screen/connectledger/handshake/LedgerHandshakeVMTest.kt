package co.electriccoin.zcash.ui.screen.connectledger.handshake

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import co.electriccoin.zcash.ui.design.R as DesignR

/**
 * The approve screen pairs the device the scan screen picked as soon as it opens, hands a fresh
 * pairing on to the New/Active question and a re-bound account on to Connected, and stops the
 * pairing on Cancel, back and disposal. Which sheet each failure shows is in
 * [LedgerHandshakeVMIssueTest].
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
    fun whileThePairingRunsThePageWaitsWithOnlyCancel() =
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
            assertTrue(state.isWaiting)
            assertEquals(DesignR.string.general_cancel, state.cancelButton.text.resourceId())
            assertEquals(ButtonStyle.DESTRUCTIVE1, state.cancelButton.style)
            assertNull(state.retryButton)
            assertNull(state.errorSheet)
            coVerify(exactly = 1) { pairLedgerDevice.invoke(device) }
        }

    @Test
    fun aFreshPairingReplacesTheScreenWithNewOrActive() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns PairLedgerDeviceResult.Paired
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            verify(exactly = 1) { navigationRouter.replace(HWNewOrActiveArgs(HWWalletEnrollment.Ledger)) }
            verify(exactly = 0) { navigationRouter.replace(LedgerConnectedArgs) }
            assertNull(vm.state.value.errorSheet)
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
            assertNotNull(vm.state.value.retryButton)
            assertFalse(vm.state.value.isWaiting)
            verify(exactly = 0) { navigationRouter.replace(*anyVararg()) }
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

    @Test
    fun anAccountAlreadyInTheWalletShowsTheAlreadyAddedSheetOverThePageWithOnlyCancel() =
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
            assertFalse(vm.state.value.isWaiting)
            assertNull(vm.state.value.retryButton)

            assertNotNull(sheet.secondary).onClick()
            runCurrent()
            assertNull(vm.state.value.errorSheet)
            assertFalse(vm.state.value.isWaiting)
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
    fun retryOnThePageRunsThePairingAgainOnTheSameDevice() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            var attempts = 0
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers {
                        attempts++
                        if (attempts == 1) {
                            throw mockk<LedgerException.UserRejected>(relaxed = true)
                        }
                        PairLedgerDeviceResult.Paired
                    }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            assertNotNull(vm.state.value.errorSheet).onBack()
            runCurrent()
            val retry = assertNotNull(vm.state.value.retryButton)
            assertEquals(R.string.ledger_handshake_retry, retry.text.resourceId())
            assertEquals(ButtonStyle.PRIMARY, retry.style)
            retry.onClick()
            runCurrent()

            coVerify(exactly = 2) { pairLedgerDevice.invoke(device) }
            verify(exactly = 1) { navigationRouter.replace(HWNewOrActiveArgs(HWWalletEnrollment.Ledger)) }
        }

    @Test
    fun theSheetsTryAgainRunsThePairingAgainAndWaits() =
        runTest(dispatcher) {
            var attempts = 0
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } coAnswers {
                        attempts++
                        if (attempts == 1) {
                            throw mockk<LedgerException.Disconnected>(relaxed = true)
                        }
                        CompletableDeferred<PairLedgerDeviceResult>().await()
                    }
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_tryAgain, assertNotNull(sheet.primary).text.resourceId())
            assertNotNull(sheet.primary).onClick()
            runCurrent()

            coVerify(exactly = 2) { pairLedgerDevice.invoke(device) }
            assertTrue(vm.state.value.isWaiting)
            assertNull(vm.state.value.retryButton)
            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun cancelStopsARunningPairingAndGoesBack() =
        runTest(dispatcher) {
            var isCancelled = false
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(pairLedgerDevice = waitingForever { isCancelled = true }, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()
            assertFalse(isCancelled)

            vm.state.value.cancelButton
                .onClick()
            runCurrent()

            assertTrue(isCancelled)
            verify(exactly = 1) { navigationRouter.back() }
            verify(exactly = 0) { navigationRouter.backToRoot() }
        }

    @Test
    fun backStopsARunningPairingAndGoesBack() =
        runTest(dispatcher) {
            var isCancelled = false
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(pairLedgerDevice = waitingForever { isCancelled = true }, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            vm.state.value.onBack()
            runCurrent()

            assertTrue(isCancelled)
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun cancelAfterAFailureGoesBackWithoutPairingAgain() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } throws mockk<LedgerException.UserRejected>(relaxed = true)
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            vm.state.value.cancelButton
                .onClick()
            runCurrent()

            verify(exactly = 1) { navigationRouter.back() }
            coVerify(exactly = 1) { pairLedgerDevice.invoke(device) }
        }

    @Test
    fun leavingTheScreenCancelsARunningPairing() =
        runTest(dispatcher) {
            var isCancelled = false
            val vm = vm(pairLedgerDevice = waitingForever { isCancelled = true })
            collect(vm)
            runCurrent()
            assertFalse(isCancelled)

            vm.triggerOnCleared()
            runCurrent()

            assertTrue(isCancelled)
        }

    @Test
    fun deniedPermissionsCancelThePairingAndGrantingThemRestartsIt() =
        runTest(dispatcher) {
            var cancellations = 0
            val pairLedgerDevice = waitingForever { cancellations++ }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()

            vm.onPermissionsDenied(false)
            runCurrent()
            assertEquals(1, cancellations)
            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_permissions_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_permissions_cta, assertNotNull(sheet.primary).text.resourceId())
            assertFalse(vm.state.value.isWaiting)

            vm.onPermissionsGranted()
            runCurrent()

            assertTrue(vm.state.value.isWaiting)
            assertNull(vm.state.value.errorSheet)
            coVerify(exactly = 2) { pairLedgerDevice.invoke(device) }
        }

    @Test
    fun aResumeWhileThePairingRunsDoesNotStartASecondOne() =
        runTest(dispatcher) {
            val pairLedgerDevice = waitingForever {}
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
            collect(vm)
            runCurrent()

            vm.onPermissionsGranted()
            vm.onBluetoothEnabled()
            runCurrent()

            coVerify(exactly = 1) { pairLedgerDevice.invoke(device) }
        }

    /**
     * The running attempt is checked before the device is looked up, so a restart while it runs
     * can never send the flow to the wallet root, even once the device has gone.
     */
    @Test
    fun aRestartWhileThePairingRunsLeavesItAloneEvenWithoutASelectedDevice() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice = waitingForever {}
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
            verify(exactly = 1) { ledgerSelectedDeviceRepository.get() }
            coVerify(exactly = 1) { pairLedgerDevice.invoke(device) }
            assertTrue(vm.state.value.isWaiting)
        }

    @Test
    fun bluetoothOffAsksTheSystemToTurnItOnAndPairsAgainOnceItIs() =
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
            assertTrue(vm.state.value.isWaiting)
            coVerify(exactly = 2) { pairLedgerDevice.invoke(device) }
        }

    private fun waitingForever(onCancelled: () -> Unit) =
        mockk<PairLedgerDeviceUseCase> {
            coEvery { this@mockk.invoke(any()) } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    onCancelled()
                }
            }
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
        navigateToError = mockk(relaxed = true),
        navigationRouter = navigationRouter,
    )
}
