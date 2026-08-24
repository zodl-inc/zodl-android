package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationNextAction
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.MigrationTransferState
import cash.z.ecc.android.sdk.MigrationTransferStates
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.fixture.AccountFixture
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.screen.home.HomeArgs
import co.electriccoin.zcash.ui.screen.migration.complete.MigrationCompleteArgs
import co.electriccoin.zcash.ui.screen.migration.invalid.MigrationTransferInvalidArgs
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressArgs
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingArgs
import co.electriccoin.zcash.work.MigrationLiveDriver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test

class CheckMigrationRecoveryUseCaseTest {
    // A single test account — mirrors the pattern used across the other migration VM/use-case
    // tests (e.g. MigrationKeystoneScanVMTest) so accountDataSource.getAllAccounts() has exactly
    // one account for the multi-account driver-start/worker-revival loop to iterate.
    private val testSdkAccount =
        AccountFixture.new(
            accountUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        )
    private val testWalletAccount: WalletAccount =
        mockk(relaxed = true) {
            every { sdkAccount } returns testSdkAccount
        }

    @kotlin.test.BeforeTest
    fun resetThrottle() {
        CheckMigrationRecoveryUseCase.resetRunThrottleForTests()
    }

    /** A single not-yet-sent transfer, [action] controlling whether it reads as broadcast-ready. */
    private fun transferState(action: MigrationNextAction?) =
        MigrationTransferState(
            id = 1L,
            isTransfer = true,
            isSent = false,
            isProved = action == MigrationNextAction.BROADCAST,
            scheduledHeight = 100L,
            anchorBoundaryHeight = null,
            ready = action != null,
            action = action,
        )

    private fun useCase(
        sdk: OrchardMigrationSdk?,
        navigationRouter: NavigationRouter,
        pendingMigrationTorFailure: Boolean = false,
        pendingMigrationTorFailureStorageProvider: PendingMigrationTorFailureStorageProvider =
            mockk(relaxed = true) {
                coEvery { get() } returns pendingMigrationTorFailure
            },
        // Default: the worker is already RUNNING in tests so the reconciliation branch is a no-op,
        // keeping existing test behaviour unchanged. Override to test reconciliation explicitly.
        getWorkerRunState: suspend (String) -> MigrationWorkerRunState = { MigrationWorkerRunState.RUNNING },
        scheduleNow: suspend (String) -> Unit = {},
        migrationLiveDriver: MigrationLiveDriver = mockk(relaxed = true),
    ) = CheckMigrationRecoveryUseCase(
        getOrchardMigrationSdk =
            mockk<GetOrchardMigrationSdkUseCase> {
                if (sdk != null) {
                    coEvery { this@mockk() } returns sdk
                    // Explicit-account overload — used by the driver-start/worker-revival loop,
                    // which now enumerates accountDataSource.getAllAccounts() instead of only the
                    // selected account.
                    coEvery { this@mockk(any()) } returns sdk
                }
            },
        persistableWalletProvider =
            mockk<PersistableWalletProvider>(relaxed = true) {
                coEvery { getPersistableWallet() } returns if (sdk != null) mockk(relaxed = true) else null
            },
        navigationRouter = navigationRouter,
        pendingMigrationTorFailureStorageProvider = pendingMigrationTorFailureStorageProvider,
        accountDataSource =
            mockk<AccountDataSource>(relaxed = true) {
                coEvery { getAllAccounts() } returns if (sdk != null) listOf(testWalletAccount) else emptyList()
            },
        context = mockk<Context>(relaxed = true),
        getWorkerRunState = getWorkerRunState,
        scheduleNow = scheduleNow,
        migrationLiveDriver = migrationLiveDriver,
    )

    // ── Task 6: auto-navigation removal — only Tor-failure fires on app-open ─────────────

    @Test
    fun hasOverdueTransfers_doesNotAutoNavigateToMigrationProgress() =
        runTest {
            // After Task 6 the overdue branch no longer auto-navigates — user reaches Progress via
            // home banner only.
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { hasOverdueTransfers() } returns true
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                }
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(sdk = sdk, navigationRouter = router, pendingMigrationTorFailure = false).invoke()

            coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationProgressArgs) }
        }

    @Test
    fun pendingTorFailureStillAutoNavigatesToMigrationSending_whenNextTransferIsBroadcastReady() =
        runTest {
            // The Tor-failure branch is the ONE auto-navigation kept after Task 6 — but only when
            // the engine's next due transfer is actually broadcast-ready right now (see the
            // staleness tests below for why this is re-checked rather than trusted blindly).
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { hasOverdueTransfers() } returns true
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationTransferStates() } returns
                        MigrationTransferStates(
                            transfers = listOf(transferState(MigrationNextAction.BROADCAST)),
                            tipHeight = 100L,
                        )
                }
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(sdk = sdk, navigationRouter = router, pendingMigrationTorFailure = true).invoke()

            coVerify(exactly = 1) { router.replaceAll(HomeArgs, MigrationSendingArgs) }
        }

    @Test
    fun pendingTorFailureDoesNotAutoNavigateToAnyOtherScreen() =
        runTest {
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { hasOverdueTransfers() } returns true
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationTransferStates() } returns
                        MigrationTransferStates(
                            transfers = listOf(transferState(MigrationNextAction.BROADCAST)),
                            tipHeight = 100L,
                        )
                }
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(sdk = sdk, navigationRouter = router, pendingMigrationTorFailure = true).invoke()

            coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationTransferInvalidArgs) }
            coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationProgressArgs) }
            coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationCompleteArgs()) }
        }

    @Test
    fun pendingTorFailureIsStale_whenNextTransferStillNeedsProof_clearsFlagInsteadOfNavigating() =
        runTest {
            // Regression: the flag only records THAT a background attempt once failed on Tor, not
            // what the engine's next due transfer needs NOW. Observed live: the flag survived from
            // an old Tor failure while the actual next-due transfer was stuck needing PROVE for an
            // unrelated reason (a stale anchor checkpoint) — navigating to Sending here just hits a
            // guaranteed AwaitingProof and shows a "Couldn't Send" sheet on every app open.
            val storageProvider =
                mockk<PendingMigrationTorFailureStorageProvider>(relaxed = true) {
                    coEvery { get() } returns true
                }
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { hasOverdueTransfers() } returns true
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationTransferStates() } returns
                        MigrationTransferStates(
                            transfers = listOf(transferState(MigrationNextAction.PROVE)),
                            tipHeight = 100L,
                        )
                }
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(
                sdk = sdk,
                navigationRouter = router,
                pendingMigrationTorFailureStorageProvider = storageProvider,
            ).invoke()

            coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationSendingArgs) }
            coVerify(exactly = 1) { storageProvider.store(false) }
        }

    @Test
    fun pendingTorFailureIsStale_whenNothingIsDue_clearsFlagInsteadOfNavigating() =
        runTest {
            val storageProvider =
                mockk<PendingMigrationTorFailureStorageProvider>(relaxed = true) {
                    coEvery { get() } returns true
                }
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { hasOverdueTransfers() } returns true
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationTransferStates() } returns
                        MigrationTransferStates(transfers = emptyList(), tipHeight = 100L)
                }
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(
                sdk = sdk,
                navigationRouter = router,
                pendingMigrationTorFailureStorageProvider = storageProvider,
            ).invoke()

            coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationSendingArgs) }
            coVerify(exactly = 1) { storageProvider.store(false) }
        }

    @Test
    fun requiresAttention_doesNotAutoNavigate() =
        runTest {
            // After Task 6, RequiresAttention no longer auto-navigates to MigrationTransferInvalidArgs.
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { getMigrationState() } returns
                        MigrationState.RequiresAttention(AttentionReason.TransferExpired)
                    coEvery { getMigrationStateUnreconciled() } returns
                        MigrationState.RequiresAttention(AttentionReason.TransferExpired)
                }
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(sdk = sdk, navigationRouter = router, pendingMigrationTorFailure = false).invoke()

            coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationTransferInvalidArgs) }
            coVerify(exactly = 0) { router.replaceAll(any()) }
        }

    @Test
    fun complete_doesNotAutoNavigateToCelebration() =
        runTest {
            // After Task 6, MigrationState.Complete no longer auto-routes to MigrationCompleteArgs.
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { hasInvalidTransfers() } returns false
                    coEvery { hasOverdueTransfers() } returns false
                    coEvery { getMigrationState() } returns MigrationState.Complete
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.Complete
                }
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(
                sdk = sdk,
                navigationRouter = router,
                pendingMigrationTorFailure = false,
            ).invoke()

            coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationCompleteArgs()) }
            coVerify(exactly = 0) { router.replaceAll(any()) }
        }

    // ── NotStarted (nothing persisted app-side anymore) ──────────────────────────────────

    @Test
    fun notStartedLeavesEverythingAloneAndDoesNotNavigate() =
        runTest {
            // Nothing plan-shaped is persisted app-side anymore — a commit that never happened
            // simply leaves the engine NotStarted and there is nothing to reconcile or clear.
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { hasInvalidTransfers() } returns false
                    coEvery { hasOverdueTransfers() } returns false
                    coEvery { getMigrationState() } returns MigrationState.NotStarted
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.NotStarted
                }
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(sdk = sdk, navigationRouter = router).invoke()

            coVerify(exactly = 0) { router.replaceAll(any()) }
        }

    @Test
    fun noWalletAvailableDoesNothing() =
        runTest {
            val router = mockk<NavigationRouter>(relaxed = true)

            useCase(sdk = null, navigationRouter = router).invoke()

            coVerify(exactly = 0) { router.replaceAll(any()) }
        }

    // ── Worker reconciliation + app-open acceleration ─────────────────────────────────────

    @Test
    fun workerReconciliation_planExistsAndWorkerRunning_doesNotSchedule() =
        runTest {
            var asked = false
            var scheduled = false
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { hasOverdueTransfers() } returns false
                }

            useCase(
                sdk = sdk,
                navigationRouter = mockk(relaxed = true),
                getWorkerRunState = {
                    asked = true
                    MigrationWorkerRunState.RUNNING
                },
                scheduleNow = { scheduled = true },
            ).invoke()

            // The check ran and, because the worker is already executing, nothing was scheduled —
            // a RUNNING worker is already doing the work this trigger wants.
            kotlin.test.assertTrue(asked)
            kotlin.test.assertFalse(scheduled)
        }

    @Test
    fun workerReconciliation_planExistsAndWorkerScheduledForLater_doesNotAccelerateViaScheduleNow() =
        runTest {
            // SCHEDULED no longer triggers scheduleNow directly — the live driver (started
            // unconditionally whenever engineInProgress) is the fast path now.
            var scheduled = false
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { hasOverdueTransfers() } returns false
                }

            useCase(
                sdk = sdk,
                navigationRouter = mockk(relaxed = true),
                getWorkerRunState = { MigrationWorkerRunState.SCHEDULED },
                scheduleNow = { scheduled = true },
            ).invoke()

            kotlin.test.assertFalse(
                scheduled,
                "SCHEDULED must not call scheduleNow anymore — the live driver covers acceleration",
            )
        }

    @Test
    fun workerReconciliation_planExists_alwaysStartsTheLiveDriver() =
        runTest {
            var startedAccountKeyId: String? = null
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { hasOverdueTransfers() } returns false
                }
            val liveDriver =
                mockk<MigrationLiveDriver> {
                    every { startIfNotRunning(any()) } answers { startedAccountKeyId = firstArg() }
                }

            useCase(
                sdk = sdk,
                navigationRouter = mockk(relaxed = true),
                getWorkerRunState = { MigrationWorkerRunState.RUNNING },
                migrationLiveDriver = liveDriver,
            ).invoke()

            kotlin.test.assertTrue(
                startedAccountKeyId != null,
                "an in-progress migration must always start (or no-op) the live driver",
            )
        }

    @Test
    fun workerReconciliation_planExistsAndWorkerAbsent_schedulesNow() =
        runTest {
            var scheduled = false
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.InProgress(mockk(relaxed = true))
                    coEvery { hasOverdueTransfers() } returns false
                }

            useCase(
                sdk = sdk,
                navigationRouter = mockk(relaxed = true),
                getWorkerRunState = { MigrationWorkerRunState.ABSENT },
                scheduleNow = { scheduled = true },
            ).invoke()

            // Revival: an absent worker chain (killed process, cleared WorkManager state) is
            // rescheduled immediately.
            kotlin.test.assertTrue(scheduled)
        }
}
