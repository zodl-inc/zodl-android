package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.common.model.LedgerBondingFailedException
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceResult
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The scan page behind each Figma connect error frame: its title, subtitle and navigation icon,
 * and the indicator it shows over the placeholder rows, or none where the device list stays.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerDeviceScanVMPageTest {
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
    fun theSearchShowsTheSearchingCopyBehindAClose() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()

            assertPage(
                vm,
                R.string.ledger_scan_searching_title,
                R.string.ledger_scan_searching_subtitle,
                LedgerDeviceScanNavigation.CLOSE
            )
            assertNull(vm.state.value.inlineIssue)
        }

    @Test
    fun noDevicesKeepsTheSearchingTitleOverTheSelectionSubtitle() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            advanceTimeBy(31.seconds)
            runCurrent()

            assertPage(
                vm,
                R.string.ledger_scan_searching_title,
                R.string.ledger_scan_select_subtitle,
                LedgerDeviceScanNavigation.CLOSE
            )
            assertInlineIssue(
                vm,
                R.drawable.ic_ledger_alert_circle,
                R.string.ledger_error_noDevices_inlineTitle,
                R.string.ledger_error_noDevices_message
            )
        }

    @Test
    fun bluetoothOffShowsTheSelectionCopyAndItsOwnIndicatorTitle() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase> {
                    every { this@mockk.invoke() } returns
                        flow { throw mockk<LedgerException.BluetoothDisabled>(relaxed = true) }
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices)
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()

            assertPage(
                vm,
                R.string.ledger_scan_select_title,
                R.string.ledger_scan_select_subtitle,
                LedgerDeviceScanNavigation.BACK
            )
            assertInlineIssue(
                vm,
                R.drawable.ic_ledger_bluetooth_off,
                R.string.ledger_error_bluetoothOff_inlineTitle,
                R.string.ledger_error_bluetoothOff_message
            )
        }

    @Test
    fun aListedDeviceShowsTheSelectionCopyBehindABackArrow() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            devices.value = listOf(device("AA"))
            runCurrent()

            assertPage(
                vm,
                R.string.ledger_scan_select_title,
                R.string.ledger_scan_select_subtitle,
                LedgerDeviceScanNavigation.BACK
            )
        }

    @Test
    fun deniedPermissionsShowTheSelectionCopyAndTheBluetoothAccessIndicator() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsDenied(true)
            runCurrent()

            assertPage(
                vm,
                R.string.ledger_scan_select_title,
                R.string.ledger_scan_select_subtitle,
                LedgerDeviceScanNavigation.BACK
            )
            assertInlineIssue(
                vm,
                R.drawable.ic_ledger_bluetooth_on,
                R.string.ledger_error_permissions_inlineTitle,
                R.string.ledger_error_permissions_inlineMessage
            )
            assertEquals(R.drawable.ic_ledger_alert_circle, assertNotNull(vm.state.value.errorSheet).icon)
        }

    @Test
    fun bluetoothUnavailableShowsTheSelectionCopyAndItsOwnIndicatorTitle() =
        runTest(dispatcher) {
            val exception =
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) {
                    every { scanErrorCode } returns null
                }
            val vm = pairedWithFailure(exception)

            assertPage(
                vm,
                R.string.ledger_scan_select_title,
                R.string.ledger_scan_select_subtitle,
                LedgerDeviceScanNavigation.BACK
            )
            assertInlineIssue(
                vm,
                R.drawable.ic_ledger_bluetooth_off,
                R.string.ledger_error_unavailable_inlineTitle,
                R.string.ledger_error_unavailable_message
            )
        }

    @Test
    fun aFailedPairingShowsTheSearchingCopyBehindACloseAndTheSheetCopyInline() =
        runTest(dispatcher) {
            val vm = pairedWithFailure(bonding(mockk<LedgerException.ConnectionFailed>(relaxed = true)))

            assertPage(
                vm,
                R.string.ledger_scan_searching_title,
                R.string.ledger_scan_searching_subtitle,
                LedgerDeviceScanNavigation.CLOSE
            )
            assertInlineIssue(
                vm,
                R.drawable.ic_ledger_alert_circle,
                R.string.ledger_error_pairingFailed_title,
                R.string.ledger_error_pairingFailed_message
            )
        }

    @Test
    fun anUnknownFailureBreaksTheIndicatorMessageOverTwoLines() =
        runTest(dispatcher) {
            val vm = pairedWithFailure(mockk<LedgerException.Internal>(relaxed = true))

            assertPage(
                vm,
                R.string.ledger_scan_searching_title,
                R.string.ledger_scan_searching_subtitle,
                LedgerDeviceScanNavigation.CLOSE
            )
            assertInlineIssue(
                vm,
                R.drawable.ic_ledger_alert_circle,
                R.string.ledger_error_unknown_title,
                R.string.ledger_error_unknown_inlineMessage
            )
            assertEquals(
                R.string.ledger_error_unknown_message,
                assertNotNull(vm.state.value.errorSheet).message.resourceId()
            )
        }

    @Test
    fun anIssueThatKeepsTheDeviceShowsTheSelectionWithoutAnIndicator() =
        runTest(dispatcher) {
            val vm = pairedWithFailure(mockk<LedgerException.WrongApp>(relaxed = true))

            assertSheetTitle(vm, R.string.ledger_error_locked_title)
            assertPage(
                vm,
                R.string.ledger_scan_select_title,
                R.string.ledger_scan_select_subtitle,
                LedgerDeviceScanNavigation.BACK
            )
            assertNull(vm.state.value.inlineIssue)
            assertTrue(vm.state.value.primaryButton.isEnabled)
        }

    @Test
    fun anAccountAlreadyAddedShowsTheSelectionWithoutAnIndicator() =
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
            assertPage(
                vm,
                R.string.ledger_scan_select_title,
                R.string.ledger_scan_select_subtitle,
                LedgerDeviceScanNavigation.BACK
            )
            assertNull(vm.state.value.inlineIssue)
        }

    private fun bonding(exception: LedgerException) = LedgerBondingFailedException(exception)

    private fun assertPage(
        vm: LedgerDeviceScanVM,
        title: Int,
        subtitle: Int,
        navigation: LedgerDeviceScanNavigation,
    ) {
        val state = vm.state.value
        assertEquals(title, state.title.resourceId())
        assertEquals(subtitle, state.subtitle.resourceId())
        assertEquals(navigation, state.navigation)
    }

    private fun assertInlineIssue(
        vm: LedgerDeviceScanVM,
        icon: Int,
        title: Int,
        message: Int,
    ) {
        val inline = assertNotNull(vm.state.value.inlineIssue)
        assertEquals(icon, inline.icon)
        assertEquals(title, inline.title.resourceId())
        assertEquals(message, inline.message.resourceId())
    }

    private fun TestScope.pairedWithFailure(exception: Exception): LedgerDeviceScanVM {
        val pairLedgerDevice =
            mockk<PairLedgerDeviceUseCase> {
                coEvery { this@mockk.invoke(any()) } throws exception
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
        return vm
    }

    private fun assertSheetTitle(vm: LedgerDeviceScanVM, expected: Int) {
        assertEquals(expected, assertNotNull(vm.state.value.errorSheet).title.resourceId())
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

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
    ) = LedgerDeviceScanVM(
        application = mockk<Application>(relaxed = true),
        observeLedgerDevices = observeLedgerDevices,
        pairLedgerDevice = pairLedgerDevice,
        selectWalletAccount = mockk(relaxed = true),
        ledgerPairingRepository = mockk(relaxed = true),
        navigateToError = mockk(relaxed = true),
        navigationRouter = mockk(relaxed = true),
    )
}
