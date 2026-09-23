package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
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
 * The scan screen's phases and its mapping from [LedgerException] to the error sheets.
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
                R.string.ledger_scan_idle_title,
                vm.state.value.title
                    .resourceId()
            )
            assertEquals(
                R.string.ledger_scan_searching_subtitle,
                vm.state.value.subtitle
                    .resourceId()
            )
            assertFalse(vm.state.value.showDeviceSkeletons)
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
                mockk<ObserveLedgerDevicesUseCase> {
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
            assertEquals(R.string.ledger_error_tryAgain, softSheet.primary.text.resourceId())

            val nonceBefore = soft.state.value.permissionRequestNonce
            softSheet.primary
                .onClick()
            runCurrent()
            assertNull(soft.state.value.errorSheet)
            assertEquals(nonceBefore + 1, soft.state.value.permissionRequestNonce)

            val permanent = vm()
            collect(permanent)
            permanent.onPermissionsDenied(false)
            runCurrent()
            val permanentSheet = assertNotNull(permanent.state.value.errorSheet)
            assertEquals(R.string.ledger_error_permissions_cta, permanentSheet.primary.text.resourceId())
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

            sheet.primary.onClick()

            verify(exactly = 1) { navigationRouter.back() }
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

    @Test
    fun pairingFailuresMapToTheirSheets() =
        runTest(dispatcher) {
            mapOf(
                mockk<LedgerException.Timeout>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                mockk<LedgerException.PairingRefused>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                mockk<LedgerException.ConnectionFailed>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                mockk<LedgerException.DeviceNotFound>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                mockk<LedgerException.WrongApp>(relaxed = true) to R.string.ledger_error_locked_title,
                mockk<LedgerException.DeviceRefused>(relaxed = true) to R.string.ledger_error_locked_title,
                mockk<LedgerException.AppTooOld>(relaxed = true) to R.string.ledger_error_locked_title,
                mockk<LedgerException.DerivationBudgetExhausted>(relaxed = true) to
                    R.string.ledger_error_locked_title,
                mockk<LedgerException.UserRejected>(relaxed = true) to R.string.ledger_error_importRejected_title,
                mockk<LedgerException.Disconnected>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.BluetoothDisabled>(relaxed = true) to R.string.ledger_error_permissions_title,
                mockk<LedgerException.BluetoothUnauthorized>(relaxed = true) to
                    R.string.ledger_error_permissions_title,
            ).forEach { (exception, expectedTitle) ->
                val vm = pairedWithFailure(exception)
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

            assertSheetTitle(pairedWithFailure(exception), R.string.ledger_error_noDevices_title)
        }

    @Test
    fun anUnmappedLedgerFailureGoesToTheGenericErrorDialog() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val exception = mockk<LedgerException.DeviceMismatch>(relaxed = true)
            val vm = pairedWithFailure(exception, navigateToError = navigateToError)

            assertNull(vm.state.value.errorSheet)
            verify(exactly = 1) { navigateToError.invoke(any(), any()) }
        }

    @Test
    fun tryAgainRestartsTheScanAndGestureDismissDoesNot() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase> {
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
                mockk<ObserveLedgerDevicesUseCase> {
                    every { this@mockk.invoke() } returns devices
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices)
            collect(vm)

            vm.onPermissionsDenied(false)
            runCurrent()

            assertSheetTitle(vm, R.string.ledger_error_permissions_title)
            verify(exactly = 0) { observeLedgerDevices.invoke() }
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

            assertNotNull(vm.state.value.errorSheet)
                .primary
                .onClick()
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
                mockk<ObserveLedgerDevicesUseCase> {
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

    private fun TestScope.pairedWithFailure(
        exception: LedgerException,
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
            mockk {
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
