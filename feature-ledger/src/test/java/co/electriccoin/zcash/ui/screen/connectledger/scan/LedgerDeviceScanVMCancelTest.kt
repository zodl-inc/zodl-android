package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository
import co.electriccoin.zcash.ui.common.usecase.ConnectLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
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
import kotlin.test.assertTrue

/**
 * A connection the user abandons, by going back, by denying the Bluetooth permissions or by the
 * screen being disposed, is cancelled at once, and a device that answers after that cannot move the
 * flow on.
 *
 * The connect use case waits on the device outside cancellation, the way a transport that finishes
 * its round trip regardless would, so each test also proves the late answer is dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerDeviceScanVMCancelTest {
    private val dispatcher = StandardTestDispatcher()

    private val devices = MutableStateFlow<List<LedgerBluetoothDevice>>(emptyList())

    private val device =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA",
            rssi = -40,
        )

    private val bonded = CompletableDeferred<Boolean>()

    private var isConnectCancelled = false

    private val connectLedgerDevice =
        mockk<ConnectLedgerDeviceUseCase> {
            coEvery { this@mockk.invoke(any()) } coAnswers {
                val result = withContext(NonCancellable) { bonded.await() }
                isConnectCancelled = !currentCoroutineContext().isActive
                result
            }
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun backWhileConnectingCancelsTheConnectionAndDoesNotGoForward() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = connecting(navigationRouter)

            vm.state.value.onBack()
            runCurrent()
            bonded.complete(false)
            runCurrent()

            assertTrue(isConnectCancelled)
            verify(exactly = 1) { navigationRouter.back() }
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
            assertFalse(vm.state.value.primaryButton.isLoading)
        }

    @Test
    fun deniedPermissionsWhileConnectingCancelTheConnectionAndShowThePermissionsSheet() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = connecting(navigationRouter)

            vm.onPermissionsDenied(false)
            runCurrent()
            bonded.complete(false)
            runCurrent()

            assertTrue(isConnectCancelled)
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_permissions_title, sheet.title.resourceId())
            assertFalse(vm.state.value.primaryButton.isLoading)
        }

    @Test
    fun leavingTheScreenWhileConnectingCancelsTheConnectionAndDoesNotGoForward() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val ledgerSelectedDeviceRepository = mockk<LedgerSelectedDeviceRepository>(relaxed = true)
            val vm = connecting(navigationRouter, ledgerSelectedDeviceRepository)

            vm.triggerOnCleared()
            runCurrent()
            bonded.complete(false)
            runCurrent()

            assertTrue(isConnectCancelled)
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
            verify(exactly = 1) { ledgerSelectedDeviceRepository.clear() }
        }

    private fun TestScope.connecting(
        navigationRouter: NavigationRouter,
        ledgerSelectedDeviceRepository: LedgerSelectedDeviceRepository = mockk(relaxed = true),
    ): LedgerDeviceScanVM {
        val vm =
            LedgerDeviceScanVM(
                application = mockk<Application>(relaxed = true),
                observeLedgerDevices =
                    mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                        every { this@mockk.invoke() } returns devices
                    },
                connectLedgerDevice = connectLedgerDevice,
                ledgerPairingRepository = mockk(relaxed = true),
                ledgerSelectedDeviceRepository = ledgerSelectedDeviceRepository,
                navigateToError = mockk(relaxed = true),
                navigationRouter = navigationRouter,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        vm.onPermissionsGranted()
        devices.value = listOf(device)
        runCurrent()
        vm.state.value.devices
            .single()
            .onClick()
        runCurrent()
        vm.state.value.primaryButton
            .onClick()
        runCurrent()
        assertTrue(vm.state.value.primaryButton.isLoading)
        return vm
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
}
