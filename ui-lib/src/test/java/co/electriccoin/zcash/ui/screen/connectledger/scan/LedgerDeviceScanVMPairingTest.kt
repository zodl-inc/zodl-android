package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LedgerBondingFailedException
import co.electriccoin.zcash.ui.common.model.LedgerPairingTimedOutException
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceResult
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthw.neworactive.HWNewOrActiveArgs
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
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

/**
 * What the scan screen does with the outcome of a pairing: which sheet each failure shows, told
 * apart by whether the phone was still connecting or the link was already up, and where a pairing
 * that succeeded leads.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerDeviceScanVMPairingTest {
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
    fun pairingFailuresMapToTheirSheets() =
        runTest(dispatcher) {
            mapOf(
                bonding(mockk<LedgerException.Timeout>(relaxed = true)) to R.string.ledger_error_pairingFailed_title,
                bonding(mockk<LedgerException.ConnectionFailed>(relaxed = true)) to
                    R.string.ledger_error_pairingFailed_title,
                bonding(mockk<LedgerException.DeviceNotFound>(relaxed = true)) to
                    R.string.ledger_error_pairingFailed_title,
                bonding(mockk<LedgerException.PairingRefused>(relaxed = true)) to
                    R.string.ledger_error_pairingFailed_title,
                bonding(mockk<LedgerException.BluetoothDisabled>(relaxed = true)) to
                    R.string.ledger_error_bluetoothOff_title,
                mockk<LedgerException.Timeout>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.ConnectionFailed>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.DeviceNotFound>(relaxed = true) to R.string.ledger_error_disconnected_title,
                mockk<LedgerException.PairingRefused>(relaxed = true) to R.string.ledger_error_pairingFailed_title,
                LedgerPairingTimedOutException() to R.string.ledger_error_disconnected_title,
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
    fun aLedgerFailureWithoutEnrollmentCopyShowsTheSomethingWentWrongSheet() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val exception = mockk<LedgerException.DeviceMismatch>(relaxed = true)
            val vm = pairedWithFailure(exception, navigateToError = navigateToError)

            assertSheetTitle(vm, R.string.ledger_error_unknown_title)
            verify(exactly = 0) { navigateToError.invoke(any(), any()) }
        }

    @Test
    fun aPairingThatTimedOutReadsAsADisconnectDuringSetupWithTryAgain() =
        runTest(dispatcher) {
            val vm = pairedWithFailure(LedgerPairingTimedOutException())

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_disconnected_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_disconnected_message, sheet.message.resourceId())
            assertEquals(R.string.ledger_error_tryAgain, assertNotNull(sheet.primary).text.resourceId())
        }

    @Test
    fun anIssueThatCannotBeRetriedOffersNoTryAgainAnywhere() =
        runTest(dispatcher) {
            val vm = pairedWithFailure(mockk<LedgerException.TransactionNotSignable>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertNull(sheet.primary)
            assertNull(sheet.secondary)
            val button = vm.state.value.primaryButton
            assertEquals(R.string.ledger_scan_select_cta, button.text.resourceId())
            assertFalse(button.isEnabled)
        }

    @Test
    fun aReboundAccountGoesStraightToConnected() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val pairLedgerDevice =
                mockk<PairLedgerDeviceUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns PairLedgerDeviceResult.Rebound(mockk(relaxed = true))
                }
            val vm = vm(pairLedgerDevice = pairLedgerDevice, navigationRouter = navigationRouter)
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

            verify(exactly = 1) { navigationRouter.forward(LedgerConnectedArgs) }
            verify(exactly = 0) { navigationRouter.forward(HWNewOrActiveArgs(HWWalletEnrollment.Ledger)) }
            assertNull(vm.state.value.errorSheet)
            assertEquals(emptyList(), vm.state.value.devices)
        }

    private fun bonding(exception: LedgerException) = LedgerBondingFailedException(exception)

    private fun TestScope.pairedWithFailure(
        exception: Exception,
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
    ): LedgerDeviceScanVM {
        val pairLedgerDevice =
            mockk<PairLedgerDeviceUseCase> {
                coEvery { this@mockk.invoke(any()) } throws exception
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
        return vm
    }

    private fun assertSheetTitle(vm: LedgerDeviceScanVM, expected: Int) {
        val sheet = vm.state.value.errorSheet
        assertNotNull(sheet)
        assertEquals(expected, sheet.title.resourceId())
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
        pairLedgerDevice: PairLedgerDeviceUseCase = mockk(relaxed = true),
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = LedgerDeviceScanVM(
        application = mockk<Application>(relaxed = true),
        observeLedgerDevices = mockk { every { this@mockk.invoke() } returns devices },
        pairLedgerDevice = pairLedgerDevice,
        selectWalletAccount = mockk(relaxed = true),
        ledgerPairingRepository = mockk(relaxed = true),
        navigateToError = navigateToError,
        navigationRouter = navigationRouter,
    )
}
