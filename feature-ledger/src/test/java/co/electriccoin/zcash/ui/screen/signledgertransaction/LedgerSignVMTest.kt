package co.electriccoin.zcash.ui.screen.signledgertransaction

import android.app.Application
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerSigningDevice
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.model.toLedgerIssue
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.CancelLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToLedgerRepairUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerSigningStateUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.RetryLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectLedgerSigningDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.StartLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitLedgerProposalUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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

            listOf(
                null to R.string.ledger_sign_scanning,
                LedgerSigningState.Scanning to R.string.ledger_sign_scanning,
                LedgerSigningState.Connecting to R.string.ledger_sign_connecting,
                LedgerSigningState.OpeningZcashApp to R.string.ledger_sign_openingApp,
                LedgerSigningState.Preparing to R.string.ledger_sign_preparing,
                LedgerSigningState.Streaming(0, 0) to R.string.ledger_sign_streaming,
                LedgerSigningState.Streaming(1, 5) to R.string.ledger_sign_streaming,
                LedgerSigningState.AwaitingReview to R.string.ledger_sign_awaitingReview,
                LedgerSigningState.Signing to R.string.ledger_sign_signing,
                LedgerSigningState.Signed to R.string.ledger_sign_signing,
            ).forEach { (state, status) ->
                signingState.value = state
                runCurrent()
                assertProgress(vm, status, expectedBody(state), state.toString())
            }
        }

    @Test
    fun theBodySwitchesToTheReviewLineExactlyAtAwaitingReview() =
        runTest(dispatcher) {
            val signingState = MutableStateFlow<LedgerSigningState?>(LedgerSigningState.Scanning)
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()

            val bodies =
                listOf(
                    LedgerSigningState.Scanning,
                    LedgerSigningState.Selecting(listOf(signingDevice("AA"))),
                    LedgerSigningState.Connecting,
                    LedgerSigningState.OpeningZcashApp,
                    LedgerSigningState.Preparing,
                    LedgerSigningState.Streaming(0, 0),
                    LedgerSigningState.Streaming(3, 7),
                    LedgerSigningState.AwaitingReview,
                    LedgerSigningState.Signing,
                    LedgerSigningState.Signed,
                ).map { state ->
                    signingState.value = state
                    runCurrent()
                    when (val content = vm.state.value?.content) {
                        is LedgerSignContent.Progress -> content.body.resourceId()
                        is LedgerSignContent.Devices -> content.body.resourceId()
                        else -> null
                    }
                }

            assertEquals(
                List(7) { R.string.ledger_sign_body_beforeReview } + List(3) { R.string.ledger_sign_body_review },
                bodies
            )
        }

    @Test
    fun awaitingReviewShowsTheSpinnerLikeEveryOtherWaitingPhase() =
        runTest(dispatcher) {
            val signingState = MutableStateFlow<LedgerSigningState?>(LedgerSigningState.AwaitingReview)
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()

            val content = vm.state.value?.content
            assertTrue(content is LedgerSignContent.Progress)
            assertEquals(R.string.ledger_sign_awaitingReview, content.status.resourceId())
            assertEquals(R.string.ledger_sign_body_review, content.body.resourceId())
        }

    @Test
    fun selectingListsTheDevicesUnderChooseYourLedgerWithoutAConnectButton() =
        runTest(dispatcher) {
            val signingState =
                MutableStateFlow<LedgerSigningState?>(
                    LedgerSigningState.Selecting(
                        devices = listOf(signingDevice("AA"), signingDevice("BB")),
                    )
                )
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()

            val devices = vm.state.value?.content
            assertTrue(devices is LedgerSignContent.Devices)
            assertEquals(R.string.ledger_sign_select_title, devices.title.resourceId())
            assertEquals(R.string.ledger_sign_body_beforeReview, devices.body.resourceId())
            assertEquals(
                listOf(stringRes("Ledger Device AA"), stringRes("Ledger Device BB")),
                devices.devices.map { it.name }
            )
            assertTrue(devices.devices.none { it.isSelected })
            assertTrue(devices.devices.all { it.isEnabled })
        }

    @Test
    fun aDeviceTapSelectsThatDeviceStraightAway() =
        runTest(dispatcher) {
            val selectLedgerSigningDevice = mockk<SelectLedgerSigningDeviceUseCase>(relaxed = true)
            val signingState =
                MutableStateFlow<LedgerSigningState?>(
                    LedgerSigningState.Selecting(
                        devices = listOf(signingDevice("AA"), signingDevice("BB")),
                    )
                )
            val vm = vm(signingState = signingState, selectLedgerSigningDevice = selectLedgerSigningDevice)
            collect(vm)
            runCurrent()

            val devices = vm.state.value?.content as LedgerSignContent.Devices
            devices.devices[1].onClick()
            runCurrent()

            verify(exactly = 1) { selectLedgerSigningDevice.invoke("BB") }
            verify(exactly = 0) { selectLedgerSigningDevice.invoke("AA") }
            assertTrue((vm.state.value?.content as LedgerSignContent.Devices).devices.none { it.isSelected })
        }

    @Test
    fun thePickerFollowsTheDevicesInRange() =
        runTest(dispatcher) {
            val signingState =
                MutableStateFlow<LedgerSigningState?>(
                    LedgerSigningState.Selecting(devices = listOf(signingDevice("AA")))
                )
            val vm = vm(signingState = signingState)
            collect(vm)
            runCurrent()
            assertEquals(1, (vm.state.value?.content as LedgerSignContent.Devices).devices.size)

            signingState.value =
                LedgerSigningState.Selecting(
                    devices = listOf(signingDevice("AA"), signingDevice("BB"), signingDevice("CC")),
                )
            runCurrent()

            assertEquals(
                listOf(stringRes("Ledger Device AA"), stringRes("Ledger Device BB"), stringRes("Ledger Device CC")),
                (vm.state.value?.content as LedgerSignContent.Devices).devices.map { it.name }
            )
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
    fun aClearedSheetStopsItsSessionWithoutNavigating() =
        runTest(dispatcher) {
            val cancelLedgerSigning = mockk<CancelLedgerSigningUseCase>(relaxed = true)
            val vm = vm(cancelLedgerSigning = cancelLedgerSigning)
            runCurrent()

            LedgerSignVM::class.java
                .getDeclaredMethod("onCleared")
                .apply { isAccessible = true }
                .invoke(vm)

            verify(exactly = 1) { cancelLedgerSigning.stopSession() }
            verify(exactly = 0) { cancelLedgerSigning.invoke() }
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
    fun aDeclinedOpenShowsZcashAppNotOpenedWithTryAgainAndCancel() =
        runTest(dispatcher) {
            val retryLedgerSigning = mockk<RetryLedgerSigningUseCase>(relaxed = true)
            val signingState =
                MutableStateFlow<LedgerSigningState?>(
                    LedgerSigningState.Failed(
                        mockk<LedgerException.AppOpenRejected>(relaxed = true)
                            .toLedgerIssue(LedgerIssueContext.SIGNING)
                    )
                )
            val vm = vm(signingState = signingState, retryLedgerSigning = retryLedgerSigning)
            collect(vm)
            runCurrent()

            val content = vm.state.value?.content as LedgerSignContent.Issue
            assertEquals(R.string.ledger_sign_error_openAppRejected_title, content.title.resourceId())
            assertEquals(R.string.ledger_sign_error_openAppRejected_message, content.message.resourceId())
            val primary = assertNotNull(content.primary)
            assertEquals(R.string.ledger_error_tryAgain, primary.text.resourceId())
            assertEquals(
                R.string.ledger_sign_cancel,
                vm.state.value
                    ?.cancelButton
                    ?.text
                    ?.resourceId()
            )

            primary.onClick()
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
    fun anUnpairedAccountOffersPairLedgerWhichPairsTheAccountAgain() =
        runTest(dispatcher) {
            val navigateToLedgerRepair = mockk<NavigateToLedgerRepairUseCase>(relaxed = true)
            val retryLedgerSigning = mockk<RetryLedgerSigningUseCase>(relaxed = true)
            val signingState = MutableStateFlow<LedgerSigningState?>(LedgerSigningState.Failed(LedgerIssue.unbound))
            val vm =
                vm(
                    signingState = signingState,
                    retryLedgerSigning = retryLedgerSigning,
                    navigateToLedgerRepair = navigateToLedgerRepair,
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

            coVerify(exactly = 1) { navigateToLedgerRepair.invoke() }
            verify(exactly = 0) { retryLedgerSigning.invoke() }
        }

    @Test
    fun aSecondPairLedgerTapWhileTheFirstReadsTheAccountOpensTheConnectFlowOnce() =
        runTest(dispatcher) {
            val selectedAccount = CompletableDeferred<LedgerAccount>()
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val navigateToLedgerRepair =
                NavigateToLedgerRepairUseCase(
                    accountDataSource =
                        mockk<AccountDataSource> {
                            coEvery { getSelectedAccount() } coAnswers { selectedAccount.await() }
                        },
                    ledgerRepairTargetRepository = LedgerRepairTargetRepositoryImpl(),
                    cancelLedgerSigning = mockk(relaxed = true),
                    navigationRouter = navigationRouter,
                )
            val signingState = MutableStateFlow<LedgerSigningState?>(LedgerSigningState.Failed(LedgerIssue.unbound))
            val vm =
                vm(
                    signingState = signingState,
                    navigateToLedgerRepair = navigateToLedgerRepair,
                    navigationRouter = navigationRouter,
                )
            collect(vm)
            runCurrent()

            val primary = assertNotNull((vm.state.value?.content as LedgerSignContent.Issue).primary)
            primary.onClick()
            runCurrent()
            primary.onClick()
            runCurrent()

            selectedAccount.complete(
                mockk {
                    every { sdkAccount.accountUuid } returns AccountUuid.new(ByteArray(16) { it.toByte() })
                }
            )
            runCurrent()

            verify(exactly = 1) { navigationRouter.forward(LedgerConnectArgs) }
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

    /**
     * A waiting phase is [LedgerSignContent.Progress], which the sheet always draws with the
     * spinner; the header title is the sheet's own and the same in every phase.
     */
    private fun assertProgress(
        vm: LedgerSignVM,
        expectedStatus: Int,
        expectedBody: Int,
        message: String,
    ) {
        val content = vm.state.value?.content
        assertTrue(content is LedgerSignContent.Progress, message)
        assertEquals(expectedStatus, content.status.resourceId(), message)
        assertEquals(expectedBody, content.body.resourceId(), message)
    }

    private fun expectedBody(state: LedgerSigningState?) =
        when (state) {
            LedgerSigningState.AwaitingReview,
            LedgerSigningState.Signing,
            LedgerSigningState.Signed -> R.string.ledger_sign_body_review

            else -> R.string.ledger_sign_body_beforeReview
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
        navigateToLedgerRepair: NavigateToLedgerRepairUseCase = mockk(relaxed = true),
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
        navigateToLedgerRepair = navigateToLedgerRepair,
        navigationRouter = navigationRouter,
    )
}
