package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.common.provider.LEDGER_SCAN_TIMEOUT
import co.electriccoin.zcash.ui.common.usecase.ConnectLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * What the scan screen offers once something ended the attempt: the idle page a successful
 * connection leaves behind, and Try again after an issue, which connects to a retained device again
 * or starts a fresh scan.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerDeviceScanVMRetryTest {
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
    fun aSuccessfulConnectionLeavesTheScreenIdleWithoutTryAgainInsteadOfStuckInConnecting() =
        runTest(dispatcher) {
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns false
                }
            val vm = vm(connectLedgerDevice = connectLedgerDevice)
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
            assertEquals(R.string.ledger_scan_select_cta, button.text.resourceId())
            assertFalse(button.isEnabled)
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
    fun comingBackToTheIdlePageAfterAConnectionScansAgain() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                }
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns false
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices, connectLedgerDevice = connectLedgerDevice)
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
            assertFalse(vm.state.value.isScanning)

            devices.value = emptyList()
            vm.onPermissionsGranted()
            runCurrent()

            assertTrue(vm.state.value.isScanning)
            verify(exactly = 2) { observeLedgerDevices.invoke() }
        }

    @Test
    fun dismissingAnIssueSheetLeavesTheInlineIssueAndAnEnabledTryAgain() =
        runTest(dispatcher) {
            val vm = connectedWithFailure(mockk<LedgerException.ConnectionFailed>(relaxed = true))
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
    fun aDeviceThatAnswersLockedShowsTheUnlockSheetAndTryAgainConnectsToItAgain() =
        runTest(dispatcher) {
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } throws
                        mockk<LedgerException.DeviceRefused>(relaxed = true) {
                            every { statusWord } returns LOCKED_STATUS_WORD
                            every { isTransient } returns true
                        }
                }
            val vm = vm(connectLedgerDevice = connectLedgerDevice)
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

            assertSheetTitle(vm, R.string.ledger_error_locked_title)
            val primary =
                assertNotNull(
                    vm.state.value.errorSheet
                        ?.primary
                )
            assertEquals(R.string.ledger_error_tryAgain, primary.text.resourceId())
            assertTrue(
                vm.state.value.devices
                    .single()
                    .isSelected
            )

            primary.onClick()
            runCurrent()

            coVerify(exactly = 2) { connectLedgerDevice.invoke(device("AA")) }
        }

    @Test
    fun tryAgainAfterAnIssueThatKeepsTheLinkConnectsToTheSameDeviceAgain() =
        runTest(dispatcher) {
            val observeLedgerDevices =
                mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                    every { this@mockk.invoke() } returns devices
                }
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } throws
                        mockk<LedgerException.UserRejected>(relaxed = true) { every { isRestartable } returns true }
                }
            val vm = vm(observeLedgerDevices = observeLedgerDevices, connectLedgerDevice = connectLedgerDevice)
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

            coVerify(exactly = 2) { connectLedgerDevice.invoke(device("AA")) }
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
    fun aFailedConnectionClearsTheListAndShowsTheInlineIssue() =
        runTest(dispatcher) {
            val vm = connectedWithFailure(mockk<LedgerException.ConnectionFailed>(relaxed = true))

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
            advanceTimeBy(LEDGER_SCAN_TIMEOUT + 1.seconds)
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

    private fun TestScope.connectedWithFailure(exception: LedgerException): LedgerDeviceScanVM {
        val connectLedgerDevice =
            mockk<ConnectLedgerDeviceUseCase> {
                coEvery { this@mockk.invoke(any()) } throws exception
            }
        val vm = vm(connectLedgerDevice = connectLedgerDevice)
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
            mockk(relaxed = true) {
                every { this@mockk.invoke() } returns devices
            },
        connectLedgerDevice: ConnectLedgerDeviceUseCase = mockk(relaxed = true),
    ) = LedgerDeviceScanVM(
        application = mockk<Application>(relaxed = true),
        observeLedgerDevices = observeLedgerDevices,
        connectLedgerDevice = connectLedgerDevice,
        ledgerPairingRepository = mockk(relaxed = true),
        ledgerSelectedDeviceRepository = mockk(relaxed = true),
        navigateToError = mockk(relaxed = true),
        navigationRouter = mockk(relaxed = true),
    )
}

private const val LOCKED_STATUS_WORD = 0x5515
