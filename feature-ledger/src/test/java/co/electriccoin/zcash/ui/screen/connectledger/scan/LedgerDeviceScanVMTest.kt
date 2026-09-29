package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerBondingFailedException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceResult
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthw.neworactive.HWNewOrActiveArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * The scan screen's phases, its permission and Bluetooth handling and the sheets it keeps; which
 * sheet each pairing outcome shows is in [LedgerDeviceScanVMPairingTest].
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
    fun scanningThenSelectingThenPairingNavigatesOnward() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns PairLedgerDeviceResult.Paired
                }
            val vm = vm(navigationRouter = navigationRouter, pairLedgerDevice = pairLedgerDevice)
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

            verify(exactly = 1) {
                navigationRouter.forward(HWNewOrActiveArgs(HWWalletEnrollment.Ledger))
            }
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
    fun aSuccessfulPairingLeavesTheScreenRetryableInsteadOfStuckInPairing() =
        runTest(dispatcher) {
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns PairLedgerDeviceResult.Paired
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
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

            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_scan_retry_cta, button.text.resourceId())
            assertTrue(button.isEnabled)
            assertFalse(button.isLoading)
            assertEquals(emptyList(), vm.state.value.devices)
            assertEquals(
                R.string.ledger_scan_idle_title,
                vm.state.value.title
                    .resourceId()
            )
            assertFalse(vm.state.value.showDeviceSkeletons)
            assertFalse(vm.state.value.isScanning)
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

            advanceTimeBy(31.seconds)
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
            assertEquals(LedgerDeviceScanNavigation.CLOSE, vm.state.value.navigation)
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

            advanceTimeBy(31.seconds)
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
            val vm = pairedWithFailure(exception, navigationRouter = navigationRouter)

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
            val vm = pairedWithFailure(exception)

            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_scan_select_cta, button.text.resourceId())
            assertFalse(button.isEnabled)
            assertNotNull(vm.state.value.inlineIssue)
        }

    @Test
    fun aScanThatFindsNothingWithinThirtySecondsShowsTheNoDevicesSheet() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            advanceTimeBy(31.seconds)
            runCurrent()

            assertSheetTitle(vm, R.string.ledger_error_noDevices_title)
        }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun aNonLedgerPairingFailureGoesToTheGeneralErrorScreen() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val failure = IllegalStateException("boom")
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } throws failure
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigateToError = navigateToError)
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
    fun dismissingAnIssueSheetLeavesTheInlineIssueAndAnEnabledTryAgain() =
        runTest(dispatcher) {
            val vm = pairedWithFailure(bonding(mockk<LedgerException.ConnectionFailed>(relaxed = true)))
            assertNotNull(vm.state.value.errorSheet).onBack()
            runCurrent()

            assertNull(vm.state.value.errorSheet)
            assertEquals(
                R.string.ledger_error_pairingFailed_title,
                assertNotNull(vm.state.value.inlineIssue).title.resourceId()
            )
            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_scan_retry_cta, button.text.resourceId())
            assertTrue(button.isEnabled)
        }

    @Test
    fun tryAgainAfterARejectedImportAsksTheSameDeviceAgain() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                }
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } throws
                        mockk<LedgerException.UserRejected>(relaxed = true) { every { isRestartable } returns true }
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices, pairLedgerDevice = pairLedgerDevice)
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

            assertSheetTitle(vm, R.string.ledger_error_importRejected_title)
            assertNull(vm.state.value.inlineIssue)
            assertTrue(
                vm.state.value.devices
                    .single()
                    .isSelected
            )

            assertNotNull(
                vm.state.value.errorSheet
                    ?.primary
            ).onClick()
            runCurrent()

            coVerify(exactly = 2) { pairLedgerDevice.invoke(device("AA")) }
            verify(exactly = 1) { observeLedgerDevices.invoke() }
            assertSheetTitle(vm, R.string.ledger_error_importRejected_title)
            assertNull(vm.state.value.inlineIssue)
            assertTrue(
                vm.state.value.devices
                    .single()
                    .isSelected
            )
        }

    @Test
    fun aFailedPairingClearsTheListAndShowsTheInlineIssue() =
        runTest(dispatcher) {
            val vm = pairedWithFailure(bonding(mockk<LedgerException.ConnectionFailed>(relaxed = true)))

            assertSheetTitle(vm, R.string.ledger_error_pairingFailed_title)
            assertNotNull(vm.state.value.inlineIssue)
            assertEquals(emptyList(), vm.state.value.devices)
            assertTrue(vm.state.value.showDeviceSkeletons)
        }

    @Test
    fun tryAgainRestartsTheScanAndGestureDismissDoesNot() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices)
            collect(vm)

            vm.onPermissionsGranted()
            advanceTimeBy(31.seconds)
            runCurrent()
            assertNotNull(vm.state.value.errorSheet)

            vm.state.value.errorSheet!!
                .onBack()
            runCurrent()
            assertNull(vm.state.value.errorSheet)
            assertFalse(vm.state.value.isScanning)
            verify(exactly = 1) { observeLedgerDevices.invoke() }

            vm.state.value.primaryButton
                .onClick()
            runCurrent()
            assertTrue(vm.state.value.isScanning)
            verify(exactly = 2) { observeLedgerDevices.invoke() }
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
    fun aDeviceAlreadyInTheWalletShowsTheAlreadyAddedSheet() =
        runTest(dispatcher) {
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns
                        PairLedgerDeviceResult.AlreadyAdded(mockk(relaxed = true))
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice)
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

            assertSheetTitle(vm, R.string.ledger_error_alreadyAdded_title)
            val secondary =
                assertNotNull(
                    vm.state.value.errorSheet
                        ?.secondary
                )
            assertEquals(ButtonStyle.SECONDARY, secondary.style)
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
    fun leavingTheScreenStopsTheScanAndReleasesAnyPendingPairing() =
        runTest(dispatcher) {
            val ledgerPairingRepository = mockk<LedgerPairingRepository>(relaxed = true)
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                }
            val vm =
                vm(
                    observeLedgerDevices = observeLedgerDevices,
                    ledgerPairingRepository = ledgerPairingRepository,
                )
            collect(vm)
            vm.onPermissionsGranted()
            runCurrent()
            assertTrue(vm.state.value.isScanning)

            vm.triggerOnCleared()
            runCurrent()

            verify(exactly = 1) { ledgerPairingRepository.clear() }
            devices.value = listOf(device("AA"))
            runCurrent()
            assertEquals(emptyList(), vm.state.value.devices)
        }

    private fun bonding(exception: LedgerException) = LedgerBondingFailedException(exception)

    private fun TestScope.pairedWithFailure(
        exception: Exception,
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ): LedgerDeviceScanVM {
        val pairLedgerDevice =
            mockk<PairLedgerDeviceUseCase> {
                coEvery { this@mockk.invoke(any()) } throws exception
            }
        val vm =
            vm(
                pairLedgerDevice = pairLedgerDevice,
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
        pairLedgerDevice: PairLedgerDeviceUseCase = mockk(relaxed = true),
        selectWalletAccount: SelectWalletAccountUseCase = mockk(relaxed = true),
        ledgerPairingRepository: LedgerPairingRepository = mockk(relaxed = true),
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = LedgerDeviceScanVM(
        application = mockk<Application>(relaxed = true),
        observeLedgerDevices = observeLedgerDevices,
        pairLedgerDevice = pairLedgerDevice,
        selectWalletAccount = selectWalletAccount,
        ledgerPairingRepository = ledgerPairingRepository,
        navigateToError = navigateToError,
        navigationRouter = navigationRouter,
    )
}
