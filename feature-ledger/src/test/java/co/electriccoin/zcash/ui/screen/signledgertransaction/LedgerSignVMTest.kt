package co.electriccoin.zcash.ui.screen.signledgertransaction

import android.app.Application
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerSigningDevice
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.usecase.CancelLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerSigningStateUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.RetryLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectLedgerSigningDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.StartLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitLedgerProposalUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectArgs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
 * The sign sheet's phase mapping, its device picker, the permission/Bluetooth-off issue handling
 * layered on top of the signing state, and the single Signed -> submit transition.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerSignVMTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun contentFollowsThePhase() =
        runTest(dispatcher) {
            val signingState = MutableStateFlow<LedgerSigningState?>(null)
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()

            assertProgress(vm, R.string.ledger_sign_scanning, isSpinning = true)

            signingState.value = LedgerSigningState.Connecting
            runCurrent()
            assertProgress(vm, R.string.ledger_sign_connecting, isSpinning = true)

            signingState.value = LedgerSigningState.Preparing
            runCurrent()
            assertProgress(vm, R.string.ledger_sign_preparing, isSpinning = true)

            signingState.value = LedgerSigningState.Streaming(1, 5)
            runCurrent()
            assertProgress(vm, R.string.ledger_sign_streaming, isSpinning = true)

            signingState.value = LedgerSigningState.AwaitingReview
            runCurrent()
            assertProgress(vm, R.string.ledger_sign_awaitingReview, isSpinning = false)

            signingState.value = LedgerSigningState.Signing
            runCurrent()
            assertProgress(vm, R.string.ledger_sign_signing, isSpinning = true)
        }

    @Test
    fun selectingRowsEnableConnectOnlyAfterAClick() =
        runTest(dispatcher) {
            val signingState =
                MutableStateFlow<LedgerSigningState?>(
                    LedgerSigningState.Selecting(
                        devices = listOf(signingDevice("AA"), signingDevice("BB")),
                        selectedIdentifier = null,
                    )
                )
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()

            val devicesBefore = vm.state.value?.content as LedgerSignContent.Devices
            assertFalse(devicesBefore.connectButton.isEnabled)

            devicesBefore.devices.first().onClick()
            runCurrent()

            val devicesAfter = vm.state.value?.content as LedgerSignContent.Devices
            assertTrue(devicesAfter.connectButton.isEnabled)
            assertTrue(devicesAfter.devices.first().isSelected)
        }

    @Test
    fun signedSubmitsExactlyOnce() =
        runTest(dispatcher) {
            val signingState = MutableStateFlow<LedgerSigningState?>(null)
            val submitLedgerProposal = mockk<SubmitLedgerProposalUseCase>(relaxed = true)
            val vm = vm(signingState = signingState, submitLedgerProposal = submitLedgerProposal)
            collect(vm)
            runCurrent()

            signingState.value = LedgerSigningState.Signed
            runCurrent()

            coVerify(exactly = 1) { submitLedgerProposal.invoke() }

            val alreadySigned = MutableStateFlow<LedgerSigningState?>(LedgerSigningState.Signed)
            val submitAlreadySigned = mockk<SubmitLedgerProposalUseCase>(relaxed = true)
            val vmAlreadySigned = vm(signingState = alreadySigned, submitLedgerProposal = submitAlreadySigned)
            collect(vmAlreadySigned)
            runCurrent()

            coVerify(exactly = 1) { submitAlreadySigned.invoke() }
        }

    @Test
    fun cancelIsDisabledOnlyOnceSigned() =
        runTest(dispatcher) {
            val signingState = MutableStateFlow<LedgerSigningState?>(LedgerSigningState.Signing)
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()

            assertTrue(
                vm.state.value
                    ?.cancelButton
                    ?.isEnabled == true
            )

            signingState.value = LedgerSigningState.Signed
            runCurrent()

            assertFalse(
                vm.state.value
                    ?.cancelButton
                    ?.isEnabled ?: true
            )
        }

    @Test
    fun cancelInvokesTheCancelUseCase() =
        runTest(dispatcher) {
            val cancelLedgerSigning = mockk<CancelLedgerSigningUseCase>(relaxed = true)
            val vm = vm(cancelLedgerSigning = cancelLedgerSigning)
            collect(vm)
            runCurrent()

            vm.state.value
                ?.cancelButton
                ?.onClick
                ?.invoke()
            runCurrent()

            verify(exactly = 1) { cancelLedgerSigning.invoke() }
        }

    @Test
    fun onBackIsANoOp() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            vm.state.value
                ?.onBack
                ?.invoke()
            runCurrent()

            verify(exactly = 0) { navigationRouter.back() }
            verify(exactly = 0) { navigationRouter.backToRoot() }
        }

    @Test
    fun tryAgainInvokesRetry() =
        runTest(dispatcher) {
            val retryLedgerSigning = mockk<RetryLedgerSigningUseCase>(relaxed = true)
            val signingState =
                MutableStateFlow<LedgerSigningState?>(
                    LedgerSigningState.Failed(issue(LedgerIssueKind.PAIRING_FAILED, LedgerIssueRetry.RECONNECT))
                )
            val vm = vm(signingState = signingState, retryLedgerSigning = retryLedgerSigning)
            collect(vm)
            runCurrent()

            val content = vm.state.value?.content as LedgerSignContent.Issue
            content.primary?.onClick?.invoke()
            runCurrent()

            verify(exactly = 1) { retryLedgerSigning.invoke() }
        }

    @Test
    fun aLocalPermissionDenialWinsOverTheSigningState() =
        runTest(dispatcher) {
            val signingState = MutableStateFlow<LedgerSigningState?>(LedgerSigningState.Scanning)
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()

            vm.onPermissionsDenied(true)
            runCurrent()

            val content = vm.state.value?.content as LedgerSignContent.Issue
            assertEquals(R.string.ledger_error_permissions_title, content.title.resourceId())
            assertEquals(R.string.ledger_error_tryAgain, content.primary?.text?.resourceId())
        }

    @Test
    fun tryAgainOnAReDeniablePermissionBumpsTheRequestNonce() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)
            runCurrent()
            vm.onPermissionsDenied(true)
            runCurrent()
            val nonceBefore = vm.state.value?.permissionRequestNonce ?: 0

            val content = vm.state.value?.content as LedgerSignContent.Issue
            content.primary?.onClick?.invoke()
            runCurrent()

            assertEquals(nonceBefore + 1, vm.state.value?.permissionRequestNonce)
        }

    @Test
    fun aPermanentPermissionDenialOffersOpenSystemSettingsInstead() =
        runTest(dispatcher) {
            val vm = vm()
            collect(vm)
            runCurrent()

            vm.onPermissionsDenied(false)
            runCurrent()

            val content = vm.state.value?.content as LedgerSignContent.Issue
            assertEquals(R.string.ledger_error_permissions_cta, content.primary?.text?.resourceId())
        }

    @Test
    fun aFailedPermissionsSessionRetriesOncePermissionsAreGranted() =
        runTest(dispatcher) {
            val retryLedgerSigning = mockk<RetryLedgerSigningUseCase>(relaxed = true)
            val signingState =
                MutableStateFlow<LedgerSigningState?>(
                    LedgerSigningState.Failed(issue(LedgerIssueKind.PERMISSIONS, LedgerIssueRetry.RECONNECT))
                )
            val vm = vm(signingState = signingState, retryLedgerSigning = retryLedgerSigning)
            collect(vm)
            runCurrent()

            vm.onPermissionsGranted()
            runCurrent()

            verify(exactly = 1) { retryLedgerSigning.invoke() }
        }

    @Test
    fun bluetoothOffTryAgainBumpsTheEnableNonceAndOnBluetoothEnabledRetries() =
        runTest(dispatcher) {
            val retryLedgerSigning = mockk<RetryLedgerSigningUseCase>(relaxed = true)
            val signingState =
                MutableStateFlow<LedgerSigningState?>(
                    LedgerSigningState.Failed(issue(LedgerIssueKind.BLUETOOTH_OFF, LedgerIssueRetry.RECONNECT))
                )
            val vm = vm(signingState = signingState, retryLedgerSigning = retryLedgerSigning)
            collect(vm)
            runCurrent()

            val nonceBefore = vm.state.value?.enableBluetoothRequestNonce ?: 0
            val content = vm.state.value?.content as LedgerSignContent.Issue
            content.primary?.onClick?.invoke()
            runCurrent()
            assertEquals(nonceBefore + 1, vm.state.value?.enableBluetoothRequestNonce)

            vm.onBluetoothEnabled()
            runCurrent()

            verify(exactly = 1) { retryLedgerSigning.invoke() }
        }

    @Test
    fun issuesWithNoAppFixOfferNoPrimaryButton() =
        runTest(dispatcher) {
            listOf(
                LedgerIssueKind.BLUETOOTH_UNAVAILABLE,
                LedgerIssueKind.NOT_SIGNABLE,
            ).forEach { kind ->
                val signingState =
                    MutableStateFlow<LedgerSigningState?>(
                        LedgerSigningState.Failed(issue(kind, LedgerIssueRetry.NONE))
                    )
                val vm = vm(signingState = signingState)
                collect(vm)
                runCurrent()

                val content = vm.state.value?.content as LedgerSignContent.Issue
                assertNull(content.primary, kind.name)
            }
        }

    @Test
    fun anIssueThatCannotBeRetriedOffersNoTryAgainWhateverItsKind() =
        runTest(dispatcher) {
            listOf(
                LedgerIssueKind.UNKNOWN,
                LedgerIssueKind.PAIRING_FAILED,
                LedgerIssueKind.DISCONNECTED,
                LedgerIssueKind.LOCKED,
            ).forEach { kind ->
                val signingState =
                    MutableStateFlow<LedgerSigningState?>(
                        LedgerSigningState.Failed(issue(kind, LedgerIssueRetry.NONE))
                    )
                val vm = vm(signingState = signingState)
                collect(vm)
                runCurrent()

                val content = vm.state.value?.content as LedgerSignContent.Issue
                assertNull(content.primary, kind.name)
                assertTrue(
                    vm.state.value
                        ?.cancelButton
                        ?.isEnabled == true,
                    kind.name
                )
            }
        }

    @Test
    fun theSignedSessionWithoutAProposalLeavesOnlyCancel() =
        runTest(dispatcher) {
            val signingState =
                MutableStateFlow<LedgerSigningState?>(LedgerSigningState.Failed(LedgerIssue.unknownWithoutRetry))
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()

            val content = vm.state.value?.content as LedgerSignContent.Issue
            assertNull(content.primary)
            assertEquals(
                R.string.ledger_sign_cancel,
                vm.state.value
                    ?.cancelButton
                    ?.text
                    ?.resourceId()
            )
            assertTrue(
                vm.state.value
                    ?.cancelButton
                    ?.isEnabled == true
            )
        }

    @Test
    fun anUnpairedAccountOffersPairLedgerWhichCancelsAndOpensTheConnectFlow() =
        runTest(dispatcher) {
            val cancelLedgerSigning = mockk<CancelLedgerSigningUseCase>(relaxed = true)
            val retryLedgerSigning = mockk<RetryLedgerSigningUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val signingState = MutableStateFlow<LedgerSigningState?>(LedgerSigningState.Failed(LedgerIssue.unbound))
            val vm =
                vm(
                    signingState = signingState,
                    retryLedgerSigning = retryLedgerSigning,
                    cancelLedgerSigning = cancelLedgerSigning,
                    navigationRouter = navigationRouter,
                )
            collect(vm)
            runCurrent()

            val content = vm.state.value?.content as LedgerSignContent.Issue
            assertEquals(R.string.ledger_sign_error_unbound_title, content.title.resourceId())
            assertEquals(R.string.ledger_sign_error_unbound_message, content.message.resourceId())
            val primary = assertNotNull(content.primary)
            assertEquals(R.string.ledger_sign_error_unbound_cta, primary.text.resourceId())

            primary.onClick()
            runCurrent()

            verifyOrder {
                cancelLedgerSigning.invoke()
                navigationRouter.forward(LedgerConnectArgs)
            }
            verify(exactly = 0) { retryLedgerSigning.invoke() }
        }

    @Test
    fun anEmptyRepositoryUnwindsToTheRoot() =
        runTest(dispatcher) {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(navigationRouter = navigationRouter, hasProposal = false)
            collect(vm)
            runCurrent()

            verify(exactly = 1) { navigationRouter.backToRoot() }
        }

    @Test
    fun onPermissionsGrantedStartsSigningWhenTheStateIsNull() =
        runTest(dispatcher) {
            val startLedgerSigning = mockk<StartLedgerSigningUseCase>(relaxed = true)
            val vm = vm(startLedgerSigning = startLedgerSigning)
            collect(vm)
            runCurrent()

            vm.onPermissionsGranted()
            runCurrent()

            verify(exactly = 1) { startLedgerSigning.invoke() }
        }

    private fun assertProgress(
        vm: LedgerSignVM,
        expectedMessage: Int,
        isSpinning: Boolean,
    ) {
        val content = vm.state.value?.content as LedgerSignContent.Progress
        assertEquals(R.string.ledger_sign_title, content.title.resourceId())
        assertEquals(expectedMessage, content.message.resourceId())
        assertEquals(isSpinning, content.isSpinning)
    }

    private fun issue(
        kind: LedgerIssueKind,
        retry: LedgerIssueRetry,
    ) = LedgerIssue(
        kind = kind,
        retry = retry,
        title = stringRes("issue title"),
        message = stringRes("issue message"),
    )

    private fun signingDevice(identifier: String) =
        LedgerSigningDevice(identifier = identifier, name = "Ledger Device $identifier")

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

    private fun TestScope.collect(vm: LedgerSignVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun vm(
        signingState: MutableStateFlow<LedgerSigningState?> = MutableStateFlow(null),
        hasProposal: Boolean = true,
        startLedgerSigning: StartLedgerSigningUseCase = mockk(relaxed = true),
        selectLedgerSigningDevice: SelectLedgerSigningDeviceUseCase = mockk(relaxed = true),
        retryLedgerSigning: RetryLedgerSigningUseCase = mockk(relaxed = true),
        cancelLedgerSigning: CancelLedgerSigningUseCase = mockk(relaxed = true),
        submitLedgerProposal: SubmitLedgerProposalUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = LedgerSignVM(
        application = mockk<Application>(relaxed = true),
        observeLedgerSigningState =
            mockk {
                every { this@mockk.invoke() } returns signingState
            },
        observeProposal =
            mockk<ObserveProposalUseCase> {
                every { observeNullable() } returns
                    if (hasProposal) flowOf(mockk<TransactionProposal>()) else flowOf(null)
            },
        startLedgerSigning = startLedgerSigning,
        selectLedgerSigningDevice = selectLedgerSigningDevice,
        retryLedgerSigning = retryLedgerSigning,
        cancelLedgerSigning = cancelLedgerSigning,
        submitLedgerProposal = submitLedgerProposal,
        navigationRouter = navigationRouter,
    )
}
