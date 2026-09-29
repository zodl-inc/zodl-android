package co.electriccoin.zcash.ui.screen.connectledger.handshake

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerBondingFailedException
import co.electriccoin.zcash.ui.common.model.LedgerPairingTimedOutException
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * What the approve screen shows for a failed pairing: the header-only error page with Cancel and
 * Retry, and the sheet of the issue on top, told apart by whether the phone was still connecting
 * or the link was already up.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerHandshakeVMIssueTest {
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
    fun pairingFailuresMapToTheirSheetsOverTheErrorPage() =
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
                mockk<LedgerException.WrongApp>(relaxed = true) {
                    every { statusWord } returns WRONG_APP_STATUS
                } to R.string.ledger_error_locked_title,
                mockk<LedgerException.WrongApp>(relaxed = true) {
                    every { statusWord } returns null
                } to R.string.ledger_error_disconnected_title,
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
                val vm = failingWith(exception)

                assertSheetTitle(vm, expectedTitle)
                assertFalse(vm.state.value.isWaiting)
                assertNotNull(vm.state.value.retryButton)
            }
        }

    @Test
    fun aBluetoothUnavailableCarryingAScanCodeIsANoDevicesFailure() =
        runTest(dispatcher) {
            val exception =
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) {
                    every { scanErrorCode } returns 1
                }

            assertSheetTitle(failingWith(exception), R.string.ledger_error_noDevices_title)
        }

    @Test
    fun aLedgerFailureWithoutEnrollmentCopyShowsTheSomethingWentWrongSheet() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val vm = failingWith(mockk<LedgerException.DeviceMismatch>(relaxed = true), navigateToError)

            assertSheetTitle(vm, R.string.ledger_error_unknown_title)
            verify(exactly = 0) { navigateToError.invoke(any(), any()) }
        }

    @Test
    fun aLostConnectionWhileConnectingReadsAsAFailedPairing() =
        runTest(dispatcher) {
            val vm = failingWith(bonding(mockk<LedgerException.ConnectionFailed>(relaxed = true)))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_pairingFailed_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_pairingFailed_message, sheet.message.resourceId())
        }

    @Test
    fun aLostConnectionAfterTheLinkWasUpReadsAsADisconnectDuringSetup() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.ConnectionFailed>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_disconnected_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_disconnected_message, sheet.message.resourceId())
        }

    @Test
    fun aPairingThatTimedOutReadsAsADisconnectDuringSetupWithTryAgainAndRetry() =
        runTest(dispatcher) {
            val vm = failingWith(LedgerPairingTimedOutException())

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertEquals(R.string.ledger_error_disconnected_title, sheet.title.resourceId())
            assertEquals(R.string.ledger_error_disconnected_message, sheet.message.resourceId())
            assertEquals(R.string.ledger_error_tryAgain, assertNotNull(sheet.primary).text.resourceId())
            assertEquals(
                R.string.ledger_handshake_retry,
                assertNotNull(vm.state.value.retryButton).text.resourceId()
            )
        }

    @Test
    fun anIssueThatCannotBeRetriedOffersNoTryAgainAnywhere() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.TransactionNotSignable>(relaxed = true))

            val sheet = assertNotNull(vm.state.value.errorSheet)
            assertNull(sheet.primary)
            assertNull(sheet.secondary)
            assertNull(vm.state.value.retryButton)
            assertFalse(vm.state.value.isWaiting)
        }

    @Test
    fun dismissingTheSheetLeavesTheErrorPageWithCancelAndRetry() =
        runTest(dispatcher) {
            val vm = failingWith(mockk<LedgerException.UserRejected>(relaxed = true))

            assertNotNull(vm.state.value.errorSheet).onBack()
            runCurrent()

            assertNull(vm.state.value.errorSheet)
            assertFalse(vm.state.value.isWaiting)
            assertNotNull(vm.state.value.retryButton)
            assertEquals(true, vm.state.value.cancelButton.isEnabled)
        }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun aNonLedgerFailureGoesToTheGeneralErrorScreenAndLeavesARetryBehind() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val failure = IllegalStateException("boom")
            val vm = failingWith(failure, navigateToError)

            verify(exactly = 1) { navigateToError.invoke(ErrorArgs.General(failure), any()) }
            assertNull(vm.state.value.errorSheet)
            assertFalse(vm.state.value.isWaiting)
            assertNotNull(vm.state.value.retryButton)
        }

    private fun bonding(exception: LedgerException) = LedgerBondingFailedException(exception)

    private fun TestScope.failingWith(
        exception: Exception,
        navigateToError: NavigateToErrorUseCase = mockk(relaxed = true),
    ): LedgerHandshakeVM {
        val pairLedgerDevice =
            mockk<PairLedgerDeviceUseCase> {
                coEvery { this@mockk.invoke(any()) } throws exception
            }
        val vm =
            LedgerHandshakeVM(
                application = mockk<Application>(relaxed = true),
                pairLedgerDevice = pairLedgerDevice,
                selectWalletAccount = mockk(relaxed = true),
                ledgerSelectedDeviceRepository =
                    mockk<LedgerSelectedDeviceRepository>(relaxed = true) {
                        every { get() } returns device
                    },
                navigateToError = navigateToError,
                navigationRouter = mockk<NavigationRouter>(relaxed = true),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
        return vm
    }

    private fun assertSheetTitle(vm: LedgerHandshakeVM, expected: Int) {
        assertEquals(expected, assertNotNull(vm.state.value.errorSheet).title.resourceId())
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource
}

private const val WRONG_APP_STATUS = 0x6E00
