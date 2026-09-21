package co.electriccoin.zcash.ui.screen.migration.sending

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferAttemptOutcome
import cash.z.ecc.android.sdk.TransferResult
import cash.z.ecc.android.sdk.model.TransactionId
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.repository.PendingMigrationTorFailureDecisionRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationSnapshotUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.ScheduleNextMigrationWindowUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessArgs
import co.electriccoin.zcash.ui.screen.migration.torfailure.MigrationTorFailureArgs
import co.electriccoin.zcash.work.MigrationDriveOnce
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import co.electriccoin.zcash.ui.design.R as DesignR

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationSendingVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun invalidNoteShowsRetryableFailureSheetWithMappedMessage() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.Executed(TransferResult.InvalidNote)
            val router = FakeNavigationRouter()
            val vm = vm(sdk = sdk, router = router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            val sheet =
                vm.state.value.content
                    ?.failureSheet
            collectJob.cancel()
            assertEquals(
                DesignR.string.migrationFailureMessage_invalidNote,
                (sheet?.message as? StringResource.ByResource)?.resource,
            )
            assertTrue(sheet != null)
        }

    @Test
    fun persistentNothingDueRetriesThenShowsNotReadySheet() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns TransferAttemptOutcome.NothingDue
            val router = FakeNavigationRouter()
            val vm = vm(sdk = sdk, router = router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            coVerify(exactly = 3) { sdk.executeNextPendingTransfer(any(), any()) }
            assertEquals(
                DesignR.string.migrationSending_notReady,
                (
                    vm.state.value.content
                        ?.failureSheet
                        ?.message as? StringResource.ByResource
                )?.resource,
            )
            collectJob.cancel()
        }

    @Test
    fun persistentAwaitingProofRetriesThenShowsNotReadySheet() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.AwaitingProof(1L)
            val router = FakeNavigationRouter()
            val vm = vm(sdk = sdk, router = router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            coVerify(exactly = 3) { sdk.executeNextPendingTransfer(any(), any()) }
            assertEquals(
                DesignR.string.migrationSending_notReady,
                (
                    vm.state.value.content
                        ?.failureSheet
                        ?.message as? StringResource.ByResource
                )?.resource,
            )
            collectJob.cancel()
        }

    @Test
    fun successExecutedRoutsToSuccessScreen() =
        runTest {
            val txId = TransactionId.new("txid123".toByteArray())
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.Executed(TransferResult.Success(txId))
            val router = FakeNavigationRouter()
            vm(sdk = sdk, router = router)

            advanceUntilIdle()

            assertEquals<List<Any>>(listOf(MigrationSuccessArgs(txId.txIdString())), router.forwardedRoutes)
        }

    @Test
    fun sendIsTriggeredAutomaticallyOnConstructionWithoutAnExternalCall() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.Executed(TransferResult.Success(TransactionId.new("txid456".toByteArray())))
            val router = FakeNavigationRouter()

            // No call to vm.send() anywhere in this test — construction alone must trigger it.
            vm(sdk = sdk, router = router)
            advanceUntilIdle()

            coVerify(exactly = 1) { sdk.executeNextPendingTransfer(any(), any()) }
        }

    @Test
    fun onBackNavigatesBackImmediately() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns TransferAttemptOutcome.NothingDue
            val router = FakeNavigationRouter()
            val vm = vm(sdk = sdk, router = router)

            vm.onBack()

            assertEquals(1, router.backCount)
        }

    @Test
    fun stateOnBackIsNoopWhileSendingIsInProgress() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            // A delay that never resolves within this test: keeps sendOnce() suspended mid-flight, so
            // the LCE stays loading and no failure sheet is ever shown, mirroring "actively sending".
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } coAnswers {
                delay(Long.MAX_VALUE / 2)
                TransferAttemptOutcome.Executed(TransferResult.Success(TransactionId.new("txid".toByteArray())))
            }
            val router = FakeNavigationRouter()
            val vm = vm(sdk = sdk, router = router)
            val collectJob = launch { vm.state.collect {} }

            // Don't advanceUntilIdle() — that fast-forwards virtual time and would resolve the huge
            // delay above too, completing the send. runCurrent() only runs already-ready continuations
            // (registering the in-flight job and emitting the first combine() value) without advancing
            // the virtual clock.
            runCurrent()

            val content = vm.state.value.content
            assertTrue(content != null)
            assertTrue(content.failureSheet == null)
            content.onBack()

            assertEquals(0, router.backCount)
            collectJob.cancel()
        }

    @Test
    fun stateOnBackNavigatesBackOnceFailureSheetIsShowing() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.Executed(TransferResult.InvalidNote)
            val router = FakeNavigationRouter()
            val vm = vm(sdk = sdk, router = router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            assertTrue(
                vm.state.value.content
                    ?.failureSheet != null
            )
            vm.state.value.content
                ?.onBack
                ?.invoke()

            assertEquals(1, router.backCount)
            collectJob.cancel()
        }

    @Test
    fun pendingTorDecisionAtConstructionTimeIsUsedInsteadOfADuplicateDefaultSend() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.Executed(TransferResult.Success(TransactionId.new("txid".toByteArray())))
            val router = FakeNavigationRouter()
            val decisionFlow = MutableStateFlow<Boolean?>(false)
            val torDecisionRepository =
                mockk<PendingMigrationTorFailureDecisionRepository> {
                    every { decision } returns decisionFlow
                    every { clear() } answers { decisionFlow.value = null }
                }

            vm(sdk = sdk, router = router, torDecisionRepository = torDecisionRepository)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                sdk.executeNextPendingTransfer(NetworkPrivacyOptions(useTor = false), useEstimatedTip = true)
            }
        }

    @Test
    fun successfulSendClearsPendingTorFailureFlag() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.Executed(TransferResult.Success(TransactionId.new("txid789".toByteArray())))
            val router = FakeNavigationRouter()
            val pendingTorFailure = mockk<PendingMigrationTorFailureStorageProvider>(relaxed = true)

            vm(sdk = sdk, router = router, pendingMigrationTorFailureStorageProvider = pendingTorFailure)
            advanceUntilIdle()

            coVerify(exactly = 1) { pendingTorFailure.store(false) }
        }

    @Test
    fun notReadyAfterMaxAttemptsAlsoClearsPendingTorFailureFlag() =
        runTest {
            // Regression: NotReady (AwaitingProof/NothingDue after retries) means no send was even
            // attempted, so it can't be a (renewed) Tor failure — a stale flag left set here would
            // keep re-triggering CheckMigrationRecoveryUseCase's app-open Sending redirect for a
            // transfer that was never going to attempt a network send in the first place.
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns TransferAttemptOutcome.NothingDue
            val router = FakeNavigationRouter()
            val pendingTorFailure = mockk<PendingMigrationTorFailureStorageProvider>(relaxed = true)

            vm(sdk = sdk, router = router, pendingMigrationTorFailureStorageProvider = pendingTorFailure)
            advanceUntilIdle()

            coVerify(exactly = 1) { pendingTorFailure.store(false) }
        }

    @Test
    fun networkErrorDoesNotClearPendingTorFailureFlag() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.Executed(TransferResult.NetworkError(retryable = false))
            val router = FakeNavigationRouter()
            val pendingTorFailure = mockk<PendingMigrationTorFailureStorageProvider>(relaxed = true)

            vm(sdk = sdk, router = router, pendingMigrationTorFailureStorageProvider = pendingTorFailure)
            advanceUntilIdle()

            coVerify(exactly = 0) { pendingTorFailure.store(any()) }
        }

    @Test
    fun networkErrorWithIsTorFailureRoutesToTorFailureScreenEvenWhenLocalUseTorFlagIsFalse() =
        runTest {
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            // isTorFailure=true, but the vm() helper's isMigrationTorEnabledStorageProvider mock
            // always returns false for the local useTor flag — proves routing now follows the
            // result's own signal, not the interactive-attempt's local Tor setting.
            coEvery { sdk.executeNextPendingTransfer(any(), any()) } returns
                TransferAttemptOutcome.Executed(TransferResult.NetworkError(retryable = false, isTorFailure = true))
            val router = FakeNavigationRouter()

            vm(sdk = sdk, router = router)
            advanceUntilIdle()

            assertTrue(router.forwardedRoutes.any { it is MigrationTorFailureArgs })
        }

    private fun vm(
        sdk: OrchardMigrationSdk,
        router: FakeNavigationRouter,
        getMigrationSnapshot: GetMigrationSnapshotUseCase =
            mockk {
                coEvery { this@mockk(null) } returns null
            },
        torDecisionRepository: PendingMigrationTorFailureDecisionRepository =
            mockk {
                every { decision } returns MutableStateFlow(null)
            },
        pendingMigrationTorFailureStorageProvider: PendingMigrationTorFailureStorageProvider = mockk(relaxed = true),
        migrationDriveOnce: MigrationDriveOnce =
            mockk {
                coEvery { withExclusiveAccess<Any?>(any()) } coAnswers {
                    firstArg<suspend () -> Any?>().invoke()
                }
            },
    ) = MigrationSendingVM(
        getOrchardMigrationSdk =
            mockk<GetOrchardMigrationSdkUseCase> {
                coEvery { this@mockk() } returns sdk
            },
        getMigrationSnapshot = getMigrationSnapshot,
        scheduleNextMigrationWindow = mockk<ScheduleNextMigrationWindowUseCase>(relaxed = true),
        navigationRouter = router,
        errorStateMapper = mockk<ErrorMapperUseCase>(relaxed = true),
        isMigrationTorEnabledStorageProvider =
            mockk<IsMigrationTorEnabledStorageProvider> {
                coEvery { get() } returns false
            },
        pendingMigrationTorFailureDecisionRepository = torDecisionRepository,
        pendingMigrationTorFailureStorageProvider = pendingMigrationTorFailureStorageProvider,
        migrationDriveOnce = migrationDriveOnce,
    )

    private class FakeNavigationRouter : NavigationRouter {
        val forwardedRoutes = mutableListOf<Any>()
        var backCount = 0

        override fun forward(vararg routes: Any) {
            forwardedRoutes.addAll(routes.toList())
        }

        override fun replace(vararg routes: Any) = Unit

        override fun replaceAll(vararg routes: Any) = Unit

        override fun replaceFrom(route: KClass<*>, vararg routes: Any) = Unit

        override fun back() {
            backCount++
        }

        override fun backTo(route: KClass<*>) = Unit

        override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

        override fun backToRoot() = Unit

        override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
    }
}
