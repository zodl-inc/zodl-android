package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.LEDGER_SCAN_TIMEOUT
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepository
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository
import co.electriccoin.zcash.ui.common.usecase.ConnectLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceRowRole
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeArgs
import co.electriccoin.zcash.ui.screen.connectledger.openapp.LedgerOpenAppArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The scan screen's phases, its hand-off to the open-the-app screen once the phone has bonded with
 * the selected device, and its mapping from [LedgerException] to the error sheets.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerDeviceScanVMTest {
    private val dispatcher = StandardTestDispatcher()

    private val devices = MutableStateFlow<List<LedgerBluetoothDevice>>(emptyList())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scanningThenSelectingThenConnectingNavigatesToOpenTheApp() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } returns false
                }
            val vm = vm(navigationRouter = navigationRouter, connectLedgerDevice = connectLedgerDevice)
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()
            assertTrue(vm.state.value.isScanning)
            assertFalse(vm.state.value.primaryButton.isEnabled)

            devices.value = listOf(device("AA"), device("BB"))
            runCurrent()
            assertEquals(2, vm.state.value.devices.size)
            assertFalse(vm.state.value.primaryButton.isEnabled)

            vm.state.value.devices
                .first()
                .onClick()
            runCurrent()
            assertTrue(vm.state.value.primaryButton.isEnabled)

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 1) { connectLedgerDevice.invoke(device("AA"), any()) }
            verify(exactly = 1) { navigationRouter.forward(LedgerOpenAppArgs()) }
            verify(exactly = 0) { navigationRouter.forward(LedgerOpenAppArgs(autoOpen = false), LedgerHandshakeArgs) }
        }

    @Test
    fun whileScanningThePageShowsPlaceholderRowsAndADisabledSearchingButtonWithASpinningIcon() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()

            val state = vm.state.value
            assertEquals(R.string.ledger_scan_searching_title, state.title.resourceId())
            assertEquals(R.string.ledger_scan_searching_subtitle, state.subtitle.resourceId())
            assertTrue(state.showDeviceSkeletons)
            assertTrue(state.isScanning)
            assertEquals(emptyList(), state.devices)
            assertEquals(R.string.ledger_scan_searching_cta, state.primaryButton.text.resourceId())
            assertEquals(R.drawable.ic_ledger_loading, state.primaryButton.icon)
            assertTrue(state.primaryButton.isIconRotating)
            assertFalse(state.primaryButton.isLoading)
            assertFalse(state.primaryButton.isEnabled)
        }

    @Test
    fun aListedDeviceReplacesThePlaceholdersWithSelectYourLedgerAndConnectWaitsForASelection() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            devices.value = listOf(device("AA"))
            runCurrent()

            val listed = vm.state.value
            assertEquals(R.string.ledger_scan_select_title, listed.title.resourceId())
            assertEquals(R.string.ledger_scan_select_subtitle, listed.subtitle.resourceId())
            assertFalse(listed.showDeviceSkeletons)
            assertFalse(listed.isScanning)
            assertFalse(listed.devices.single().isSelected)
            assertEquals(LedgerDeviceRowRole.RADIO, listed.devices.single().role)
            assertEquals(R.string.ledger_scan_select_cta, listed.primaryButton.text.resourceId())
            assertNull(listed.primaryButton.icon)
            assertFalse(listed.primaryButton.isEnabled)

            listed.devices
                .single()
                .onClick()
            runCurrent()

            assertTrue(
                vm.state.value.devices
                    .single()
                    .isSelected
            )
            assertTrue(vm.state.value.primaryButton.isEnabled)
        }

    @Test
    fun aDeviceAlreadyInTheZcashAppGoesStraightToTheHandshakeOverAnIdleOpenAppStep() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } returns true
                }
            val vm = vm(navigationRouter = navigationRouter, connectLedgerDevice = connectLedgerDevice)
            collect(vm)

            vm.onPermissionsGranted()
            devices.value = listOf(device("AA"))
            runCurrent()
            vm.state.value.devices
                .first()
                .onClick()
            runCurrent()
            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            verify(exactly = 1) { navigationRouter.forward(LedgerOpenAppArgs(autoOpen = false), LedgerHandshakeArgs) }
            verify(exactly = 0) { navigationRouter.forward(LedgerOpenAppArgs()) }
            assertEquals(emptyList(), vm.state.value.devices)
            assertFalse(vm.state.value.primaryButton.isLoading)
        }

    @Test
    fun locationOffBelowApi31ShowsTheBluetoothAccessIssueInsteadOfScanning() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                    every { isLocationOffForScan() } returns true
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices)
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()
            advanceTimeBy(60.seconds)
            runCurrent()

            assertFalse(vm.state.value.isScanning)
            assertSheetTitle(vm, R.string.ledger_error_permissions_title)
            assertEquals(
                R.string.ledger_error_permissions_cta,
                vm.state.value.primaryButton.text
                    .resourceId()
            )
            verify(exactly = 0) { observeLedgerDevices.invoke() }
        }

    @Test
    fun whileConnectingTheRowsAreLockedAndThePairingCodeHintIsShown() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val bonded = CompletableDeferred<Boolean>()
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } coAnswers { bonded.await() }
                }
            val vm = vm(navigationRouter = navigationRouter, connectLedgerDevice = connectLedgerDevice)
            collect(vm)

            vm.onPermissionsGranted()
            devices.value = listOf(device("AA"))
            runCurrent()
            vm.state.value.devices
                .first()
                .onClick()
            runCurrent()
            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            assertEquals(
                R.string.ledger_scan_select_subtitle,
                vm.state.value.subtitle
                    .resourceId()
            )
            assertTrue(vm.state.value.primaryButton.isLoading)
            assertFalse(vm.state.value.primaryButton.isEnabled)
            assertFalse(
                vm.state.value.devices
                    .single()
                    .isEnabled
            )
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }

            bonded.complete(false)
            runCurrent()

            verify(exactly = 1) { navigationRouter.forward(LedgerOpenAppArgs()) }
        }

    @Test
    fun theCopyFollowsThePhaseRatherThanPretendingToSearch() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()
            assertEquals(
                R.string.ledger_scan_searching_title,
                vm.state.value.title
                    .resourceId()
            )
            assertTrue(vm.state.value.showDeviceSkeletons)

            advanceTimeBy(LEDGER_SCAN_TIMEOUT + 1.seconds)
            runCurrent()
            assertNotNull(vm.state.value.errorSheet).onBack()
            runCurrent()

            assertEquals(
                R.string.ledger_scan_searching_title,
                vm.state.value.title
                    .resourceId()
            )
            assertEquals(
                R.string.ledger_scan_select_subtitle,
                vm.state.value.subtitle
                    .resourceId()
            )
            assertTrue(vm.state.value.showDeviceSkeletons)
            assertEquals(
                R.string.ledger_scan_retry_cta,
                vm.state.value.primaryButton.text
                    .resourceId()
            )
        }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun aGenericScanFailureStopsTheTimeoutInsteadOfLettingItFireLater() =
        runTest(dispatcher) {
            val failing =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns flow { throw RuntimeException("boom") }
                }
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val vm = vm(observeLedgerDevices = failing, navigateToError = navigateToError)
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()
            verify(exactly = 1) { navigateToError.invoke(any(), any()) }

            advanceTimeBy(LEDGER_SCAN_TIMEOUT + 1.seconds)
            runCurrent()

            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun aSoftDenialOffersAnotherInAppRequestAndAPermanentOneOffersSettings() =
        runTest(dispatcher) {
            val soft = vm()
            collect(soft)
            soft.onPermissionsDenied(true)
            runCurrent()
            val softSheet = assertNotNull(soft.state.value.errorSheet)
            assertEquals(R.string.ledger_error_permissions_title, softSheet.title.resourceId())
            assertEquals(R.string.ledger_error_tryAgain, assertNotNull(softSheet.primary).text.resourceId())

            val nonceBefore = soft.state.value.permissionRequestNonce
            assertNotNull(softSheet.primary)
                .onClick()
            runCurrent()
            assertNull(soft.state.value.errorSheet)
            assertEquals(nonceBefore + 1, soft.state.value.permissionRequestNonce)

            val permanent = vm()
            collect(permanent)
            permanent.onPermissionsDenied(false)
            runCurrent()
            val permanentSheet = assertNotNull(permanent.state.value.errorSheet)
            assertEquals(R.string.ledger_error_permissions_cta, assertNotNull(permanentSheet.primary).text.resourceId())
        }

    @Test
    fun aPermanentDenialLabelsThePageButtonLikeTheSheet() =
        runTest(dispatcher) {
            val soft = vm()
            collect(soft)
            soft.onPermissionsDenied(true)
            runCurrent()
            assertEquals(
                R.string.ledger_scan_retry_cta,
                soft.state.value.primaryButton.text
                    .resourceId()
            )

            val permanent = vm()
            collect(permanent)
            permanent.onPermissionsDenied(false)
            runCurrent()
            val button = permanent.state.value.primaryButton
            assertEquals(R.string.ledger_error_permissions_cta, button.text.resourceId())
            assertTrue(button.isEnabled)
        }

    @Test
    fun aBluetoothUnavailableWithoutAScanCodeShowsTheUnavailableSheetWhichOnlyCloses() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val exception =
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) {
                    every { scanErrorCode } returns null
                }
            val vm = connectedWithFailure(exception, navigationRouter = navigationRouter)

            val sheet = vm.state.value.errorSheet
            assertNotNull(sheet)
            assertEquals(R.string.ledger_error_unavailable_title, sheet.title.resourceId())
            assertNull(sheet.secondary)

            assertEquals(R.string.ledger_error_unavailable_cta, assertNotNull(sheet.primary).text.resourceId())
            assertNotNull(sheet.primary).onClick()

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun theBluetoothUnavailablePageOffersOnlyADisabledConnect() =
        runTest(dispatcher) {
            val exception =
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) {
                    every { scanErrorCode } returns null
                }
            val vm = connectedWithFailure(exception)

            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_scan_select_cta, button.text.resourceId())
            assertFalse(button.isEnabled)
            assertNotNull(vm.state.value.inlineIssue)
        }

    @Test
    fun aScanThatFindsNothingWithinTheScanTimeoutShowsTheNoDevicesSheet() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            advanceTimeBy(LEDGER_SCAN_TIMEOUT + 1.seconds)
            runCurrent()

            assertSheetTitle(vm, R.string.ledger_error_noDevices_title)
        }

    @Test
    fun connectionFailuresMapToTheirSheets() =
        runTest(dispatcher) {
            mapOf(
                mockk<LedgerException.Timeout>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                mockk<LedgerException.PairingRefused>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                mockk<LedgerException.ConnectionFailed>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                mockk<LedgerException.DeviceNotFound>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                mockk<LedgerException.WrongApp>(relaxed = true) to R.string.ledger_error_locked_title,
                mockk<LedgerException.DeviceRefused>(relaxed = true) to R.string.ledger_error_unknown_title,
                mockk<LedgerException.DeviceRefused>(relaxed = true) {
                    every { isTransient } returns true
                } to R.string.ledger_error_locked_title,
                mockk<LedgerException.CapsMismatch>(relaxed = true) to R.string.ledger_error_unknown_title,
                mockk<LedgerException.AppTooOld>(relaxed = true) to R.string.ledger_error_appTooOld_title,
                mockk<LedgerException.DerivationBudgetExhausted>(relaxed = true) to
                    R.string.ledger_error_restartApp_title,
                mockk<LedgerException.UserRejected>(relaxed = true) to R.string.ledger_error_importRejected_title,
                mockk<LedgerException.Disconnected>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.BluetoothDisabled>(relaxed = true) to R.string.ledger_error_bluetoothOff_title,
                mockk<LedgerException.BluetoothUnauthorized>(relaxed = true) to
                    R.string.ledger_error_permissions_title,
            ).forEach { (exception, expectedTitle) ->
                val vm = connectedWithFailure(exception)
                assertSheetTitle(vm, expectedTitle)
            }
        }

    @Test
    fun aBluetoothUnavailableCarryingAScanCodeIsANoDevicesFailure() =
        runTest(dispatcher) {
            val exception =
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) {
                    every { scanErrorCode } returns 1
                }

            assertSheetTitle(connectedWithFailure(exception), R.string.ledger_error_noDevices_title)
        }

    @Test
    fun aLedgerFailureWithoutEnrollmentCopyShowsTheSomethingWentWrongSheet() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val exception = mockk<LedgerException.DeviceMismatch>(relaxed = true)
            val vm = connectedWithFailure(exception, navigateToError = navigateToError)

            assertSheetTitle(vm, R.string.ledger_error_unknown_title)
            verify(exactly = 0) { navigateToError.invoke(any(), any()) }
        }

    @Test
    fun anIssueThatCannotBeRetriedOffersNoTryAgainAnywhere() =
        runTest(dispatcher) {
            val vm = connectedWithFailure(mockk<LedgerException.TransactionNotSignable>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertNull(sheet.primary)
            assertNull(sheet.secondary)
            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_scan_select_cta, button.text.resourceId())
            assertFalse(button.isEnabled)
        }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun aNonLedgerConnectionFailureGoesToTheGeneralErrorScreen() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val failure = IllegalStateException("boom")
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } throws failure
                }
            val vm = vm(connectLedgerDevice = connectLedgerDevice, navigateToError = navigateToError)
            collect(vm)

            vm.onPermissionsGranted()
            devices.value = listOf(device("AA"))
            runCurrent()
            vm.state.value.devices
                .first()
                .onClick()
            runCurrent()
            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            verify(exactly = 1) { navigateToError.invoke(ErrorArgs.General(failure), any()) }
            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun bluetoothOffAsksTheSystemToTurnItOnAndScansOnceItIs() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returnsMany
                        listOf(
                            flow { throw mockk<LedgerException.BluetoothDisabled>(relaxed = true) },
                            devices,
                        )
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices)
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()
            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_bluetoothOff_title, sheet.title.resourceId())
            assertEquals(R.drawable.ic_ledger_bluetooth_off, sheet.icon)
            assertEquals(0, vm.state.value.enableBluetoothRequestNonce)

            assertNotNull(sheet.primary).onClick()
            runCurrent()
            assertNull(vm.state.value.errorSheet)
            assertEquals(1, vm.state.value.enableBluetoothRequestNonce)

            vm.onBluetoothEnableDeclined()
            runCurrent()
            assertSheetTitle(vm, R.string.ledger_error_bluetoothOff_title)
            assertEquals(
                R.string.ledger_error_bluetoothOff_inlineTitle,
                assertNotNull(vm.state.value.inlineIssue).title.resourceId()
            )

            vm.onBluetoothEnabled()
            runCurrent()
            verify(exactly = 2) { observeLedgerDevices.invoke() }
            assertTrue(vm.state.value.isScanning)
            assertNull(vm.state.value.inlineIssue)
            assertNull(vm.state.value.errorSheet)
        }

    @Test
    fun deniedPermissionsShowThePermissionsSheetWithoutScanning() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices)
            collect(vm)

            vm.onPermissionsDenied(false)
            runCurrent()

            assertSheetTitle(vm, R.string.ledger_error_permissions_title)
            verify(exactly = 0) { observeLedgerDevices.invoke() }
        }

    /**
     * The gate reports only "granted" on the resume after Settings granted the permissions, so that
     * alone has to start the scan and keep the sheet down.
     */
    @Test
    fun grantingThePermissionsInSettingsStartsTheScanOnResumeWithoutTheSheet() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices)
            collect(vm)
            vm.onPermissionsDenied(false)
            runCurrent()

            assertNotNull(
                vm.state.value.errorSheet
                    ?.primary
            ).onClick()
            runCurrent()
            assertNull(vm.state.value.errorSheet)

            vm.onPermissionsGranted()
            runCurrent()

            assertTrue(vm.state.value.isScanning)
            assertNull(vm.state.value.errorSheet)
            assertNull(vm.state.value.inlineIssue)
            verify(exactly = 1) { observeLedgerDevices.invoke() }

            devices.value = listOf(device("AA"))
            runCurrent()
            assertEquals(1, vm.state.value.devices.size)
        }

    @Test
    fun leavingTheScreenStopsTheScanAndReleasesTheDeviceAndAnyPendingPairing() =
        runTest(dispatcher) {
            val ledgerPairingRepository = mockk<LedgerPairingRepository>(relaxed = true)
            val ledgerSelectedDeviceRepository = mockk<LedgerSelectedDeviceRepository>(relaxed = true)
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                }
            val vm =
                vm(
                    observeLedgerDevices = observeLedgerDevices,
                    ledgerPairingRepository = ledgerPairingRepository,
                    ledgerSelectedDeviceRepository = ledgerSelectedDeviceRepository,
                )
            collect(vm)
            vm.onPermissionsGranted()
            runCurrent()
            assertTrue(vm.state.value.isScanning)

            vm.triggerOnCleared()
            runCurrent()

            verify(exactly = 1) { ledgerPairingRepository.clear() }
            verify(exactly = 1) { ledgerSelectedDeviceRepository.clear() }
            devices.value = listOf(device("AA"))
            runCurrent()
            assertEquals(emptyList(), vm.state.value.devices)
        }

    private fun TestScope.connectedWithFailure(
        exception: LedgerException,
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ): LedgerDeviceScanVM {
        val connectLedgerDevice =
            mockk<ConnectLedgerDeviceUseCase> {
                coEvery { this@mockk.invoke(any(), any()) } throws exception
            }
        val vm =
            vm(
                connectLedgerDevice = connectLedgerDevice,
                navigateToError = navigateToError,
                navigationRouter = navigationRouter,
            )
        collect(vm)
        vm.onPermissionsGranted()
        devices.value = listOf(device("AA"))
        runCurrent()
        vm.state.value.devices
            .first()
            .onClick()
        runCurrent()
        vm.state.value.primaryButton
            .onClick()
        runCurrent()
        return vm
    }

    private fun assertSheetTitle(vm: LedgerDeviceScanVM, expected: Int) {
        val sheet = vm.state.value.errorSheet
        assertNotNull(sheet)
        assertEquals(expected, sheet.title.resourceId())
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

    /**
     * `onCleared` is protected on `ViewModel`, and the test needs the real disposal path rather
     * than a stand-in for it.
     */
    private fun LedgerDeviceScanVM.triggerOnCleared() {
        LedgerDeviceScanVM::class.java
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(this)
    }

    private fun TestScope.collect(vm: LedgerDeviceScanVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun device(identifier: String) =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = identifier,
            rssi = -40,
        )

    private fun CoroutineScope.vm(
        observeLedgerDevices: ObserveLedgerDevicesUseCase =
            mockk(relaxed = true) {
                every { this@mockk.invoke() } returns devices
            },
        connectLedgerDevice: ConnectLedgerDeviceUseCase = mockk(relaxed = true),
        ledgerPairingRepository: LedgerPairingRepository = mockk(relaxed = true),
        ledgerSelectedDeviceRepository: LedgerSelectedDeviceRepository = mockk(relaxed = true),
        ledgerRepairTargetRepository: LedgerRepairTargetRepository = LedgerRepairTargetRepositoryImpl(),
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = LedgerDeviceScanVM(
        application = mockk<Application>(relaxed = true),
        observeLedgerDevices = observeLedgerDevices,
        connectLedgerDevice = connectLedgerDevice,
        ledgerPairingRepository = ledgerPairingRepository,
        ledgerSelectedDeviceRepository = ledgerSelectedDeviceRepository,
        ledgerRepairTargetRepository = ledgerRepairTargetRepository,
        navigateToError = navigateToError,
        navigationRouter = navigationRouter,
    )
}
