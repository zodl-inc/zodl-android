package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepository
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.ConnectLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The "Advanced options" card of the scan screen: which ZIP 32 account of the Ledger the wallet
 * pairs, how the input is validated, that Connect waits for a valid one and hands it on with the
 * device, and that pairing an account again starts from the account's stored index.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerDeviceScanVMAccountIndexTest {
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
    fun theCardAppearsCollapsedUnderAListedDeviceWithTheFirstAccountChosen() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)

            vm.onPermissionsGranted()
            runCurrent()
            assertNull(vm.state.value.advancedOptions, "no card while searching")

            listAndSelect(vm)

            val options = assertNotNull(vm.state.value.advancedOptions)
            assertFalse(options.isExpanded)
            assertEquals(R.string.ledger_scan_advanced_title, options.title.resourceId())
            assertEquals(R.string.ledger_scan_advanced_message, options.message.resourceId())
            assertEquals(R.string.ledger_scan_accountIndex_label, options.accountIndexLabel.resourceId())
            assertEquals(R.string.ledger_scan_accountIndex_hint, options.hint.resourceId())
            assertEquals(StringResource.ByString("0"), options.accountIndex.value)
            assertFalse(options.accountIndex.isError)
            assertTrue(vm.state.value.primaryButton.isEnabled)
        }

    @Test
    fun theHeaderExpandsAndCollapsesTheCard() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)

            assertNotNull(vm.state.value.advancedOptions).onToggle()
            runCurrent()
            assertTrue(assertNotNull(vm.state.value.advancedOptions).isExpanded)

            assertNotNull(vm.state.value.advancedOptions).onToggle()
            runCurrent()
            assertFalse(assertNotNull(vm.state.value.advancedOptions).isExpanded)
        }

    @Test
    fun aWholeNumberFromZeroToOneHundredIsValid() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)

            listOf("0", "1", "42", "100").forEach { input ->
                type(vm, input)

                assertFalse(assertNotNull(vm.state.value.advancedOptions).accountIndex.isError, input)
                assertTrue(vm.state.value.primaryButton.isEnabled, input)
            }
        }

    @Test
    fun anythingElseShowsTheErrorAndDisablesConnect() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)

            listOf("101", "150", "", "1a", "-1", " 5", "1.0", "99999999999999999999").forEach { input ->
                type(vm, input)

                val options = assertNotNull(vm.state.value.advancedOptions)
                assertTrue(options.accountIndex.isError, input)
                assertEquals(StringResource.ByString(input), options.accountIndex.value, input)
                assertFalse(vm.state.value.primaryButton.isEnabled, input)
            }

            type(vm, "100")
            assertTrue(vm.state.value.primaryButton.isEnabled)
        }

    @Test
    fun anInvalidIndexKeepsTheCardOpenUntilItIsFixed() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)
            assertNotNull(vm.state.value.advancedOptions).onToggle()
            runCurrent()
            type(vm, "101")

            assertNotNull(vm.state.value.advancedOptions).onToggle()
            runCurrent()
            assertTrue(assertNotNull(vm.state.value.advancedOptions).isExpanded)

            type(vm, "5")
            assertNotNull(vm.state.value.advancedOptions).onToggle()
            runCurrent()
            assertFalse(assertNotNull(vm.state.value.advancedOptions).isExpanded)
        }

    /**
     * A successful connection clears the device list for the scan that starts once the user is
     * back on the page; the index they typed is kept for the next attempt.
     */
    @Test
    fun theTypedIndexSurvivesAConnectionAndComingBack() =
        runTest(dispatcher) {
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } returns false
                }
            val vm = vm(connectLedgerDevice = connectLedgerDevice)
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)
            type(vm, "7")
            vm.state.value.primaryButton
                .onClick()
            runCurrent()
            assertNull(vm.state.value.advancedOptions, "the list is cleared once connected")

            vm.onPermissionsGranted()
            runCurrent()
            listAndSelect(vm)

            val options = assertNotNull(vm.state.value.advancedOptions)
            assertEquals(StringResource.ByString("7"), options.accountIndex.value)
            vm.state.value.primaryButton
                .onClick()
            runCurrent()
            coVerify(exactly = 2) { connectLedgerDevice.invoke(device(), Zip32AccountIndex.new(7)) }
        }

    @Test
    fun anInvalidIndexKeepsConnectFromConnecting() =
        runTest(dispatcher) {
            val connectLedgerDevice = mockk<ConnectLedgerDeviceUseCase>(relaxed = true)
            val vm = vm(connectLedgerDevice = connectLedgerDevice)
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)
            type(vm, "101")

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 0) { connectLedgerDevice.invoke(any(), any()) }
        }

    @Test
    fun connectHandsTheChosenAccountOnWithTheDevice() =
        runTest(dispatcher) {
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } returns false
                }
            val vm = vm(connectLedgerDevice = connectLedgerDevice)
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)
            type(vm, "7")

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 1) { connectLedgerDevice.invoke(device(), Zip32AccountIndex.new(7)) }
        }

    @Test
    fun connectWithTheCardUntouchedUsesTheFirstAccount() =
        runTest(dispatcher) {
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } returns false
                }
            val vm = vm(connectLedgerDevice = connectLedgerDevice)
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 1) { connectLedgerDevice.invoke(device(), Zip32AccountIndex.new(0)) }
        }

    @Test
    fun theFieldIsLockedWhileConnecting() =
        runTest(dispatcher) {
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } coAnswers { awaitCancellation() }
                }
            val vm = vm(connectLedgerDevice = connectLedgerDevice)
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            assertFalse(assertNotNull(vm.state.value.advancedOptions).accountIndex.isEnabled)
        }

    /**
     * Pairing an account again starts from the index its binding stored, and the user may still
     * choose another; whether it is the right one is up to the viewing key the device exports.
     */
    @Test
    fun pairingAnAccountAgainStartsFromItsStoredIndexAndStaysEditable() =
        runTest(dispatcher) {
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } returns false
                }
            val vm =
                vm(
                    connectLedgerDevice = connectLedgerDevice,
                    ledgerRepairTargetRepository = repairTarget(Zip32AccountIndex.new(4)),
                )
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)

            val options = assertNotNull(vm.state.value.advancedOptions)
            assertTrue(options.isExpanded, "a stored index other than 0 is shown at once")
            assertEquals(StringResource.ByString("4"), options.accountIndex.value)
            assertTrue(options.accountIndex.isEnabled)
            assertFalse(options.accountIndex.isError)
            assertTrue(vm.state.value.primaryButton.isEnabled)

            type(vm, "2")
            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 1) { connectLedgerDevice.invoke(device(), Zip32AccountIndex.new(2)) }
        }

    @Test
    fun pairingAnAccountAgainWithoutAStoredIndexStartsFromTheFirstAccount() =
        runTest(dispatcher) {
            val connectLedgerDevice =
                mockk<ConnectLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } returns false
                }
            val vm =
                vm(
                    connectLedgerDevice = connectLedgerDevice,
                    ledgerRepairTargetRepository = repairTarget(null),
                )
            collect(vm)
            vm.onPermissionsGranted()
            listAndSelect(vm)

            val options = assertNotNull(vm.state.value.advancedOptions)
            assertFalse(options.isExpanded)
            assertEquals(StringResource.ByString("0"), options.accountIndex.value)
            assertTrue(options.accountIndex.isEnabled)

            vm.state.value.primaryButton
                .onClick()
            runCurrent()

            coVerify(exactly = 1) { connectLedgerDevice.invoke(device(), Zip32AccountIndex.new(0)) }
        }

    private fun repairTarget(zip32AccountIndex: Zip32AccountIndex?) =
        LedgerRepairTargetRepositoryImpl().apply {
            set(AccountUuid.new(ByteArray(16) { it.toByte() }), zip32AccountIndex)
        }

    private fun TestScope.listAndSelect(vm: LedgerDeviceScanVM) {
        devices.value = listOf(device())
        runCurrent()
        vm.state.value.devices
            .single()
            .onClick()
        runCurrent()
    }

    private fun TestScope.type(
        vm: LedgerDeviceScanVM,
        input: String
    ) {
        assertNotNull(vm.state.value.advancedOptions)
            .accountIndex
            .onValueChange(input)
        runCurrent()
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

    private fun TestScope.collect(vm: LedgerDeviceScanVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun device() =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA",
            rssi = -40,
        )

    private fun CoroutineScope.vm(
        connectLedgerDevice: ConnectLedgerDeviceUseCase = mockk(relaxed = true),
        ledgerRepairTargetRepository: LedgerRepairTargetRepository = LedgerRepairTargetRepositoryImpl(),
    ) = LedgerDeviceScanVM(
        application = mockk<Application>(relaxed = true),
        observeLedgerDevices =
            mockk<ObserveLedgerDevicesUseCase>(relaxed = true) {
                every { this@mockk.invoke() } returns devices
            },
        connectLedgerDevice = connectLedgerDevice,
        ledgerPairingRepository = mockk(relaxed = true),
        ledgerSelectedDeviceRepository = mockk(relaxed = true),
        ledgerRepairTargetRepository = ledgerRepairTargetRepository,
        navigateToError = mockk(relaxed = true),
        navigationRouter = mockk(relaxed = true),
    )
}
