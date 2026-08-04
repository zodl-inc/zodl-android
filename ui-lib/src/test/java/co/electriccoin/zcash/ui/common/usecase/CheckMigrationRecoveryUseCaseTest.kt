package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.migration.MigrationPlan
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.repository.MigrationPlanRepository
import co.electriccoin.zcash.ui.screen.home.HomeArgs
import co.electriccoin.zcash.ui.screen.migration.complete.MigrationCompleteArgs
import co.electriccoin.zcash.ui.screen.migration.invalid.MigrationTransferInvalidArgs
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressArgs
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingArgs
import co.electriccoin.zcash.ui.screen.migration.transferreview.MigrationTransferReviewArgs
import co.electriccoin.zcash.work.MigrationSyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class CheckMigrationRecoveryUseCaseTest {

    @kotlin.test.BeforeTest
    fun resetThrottle() {
        CheckMigrationRecoveryUseCase.resetRunThrottleForTests()
    }

    private fun useCase(
        sdk: OrchardMigrationSdk?,
        navigationRouter: NavigationRouter,
        pendingMigrationTorFailure: Boolean = false,
        savedPlan: MigrationPlan? = mockk(relaxed = true),
        migrationPlanRepository: MigrationPlanRepository = mockk(relaxed = true) {
            coEvery { load() } returns savedPlan
        },
        migrationSyncScheduler: MigrationSyncScheduler = mockk(relaxed = true),
        // Default: Lane A is always active in tests so the reconciliation branch is skipped,
        // keeping existing test behaviour unchanged. Override to test reconciliation explicitly.
        isLaneAActive: suspend () -> Boolean = { true },
        isLaneBActive: suspend (String) -> Boolean = { true },
    ) = CheckMigrationRecoveryUseCase(
        getOrchardMigrationSdk = mockk<GetOrchardMigrationSdkUseCase> {
            coEvery { this@mockk() } returns sdk
        },
        navigationRouter = navigationRouter,
        migrationPlanRepository = migrationPlanRepository,
        pendingMigrationTorFailureStorageProvider = mockk<PendingMigrationTorFailureStorageProvider> {
            coEvery { get() } returns pendingMigrationTorFailure
        },
        getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>(relaxed = true),
        migrationSyncScheduler = migrationSyncScheduler,
        context = mockk<Context>(relaxed = true),
        isLaneAActive = isLaneAActive,
        isLaneBActive = isLaneBActive,
    )

    // ── Task 6: auto-navigation removal — only Tor-failure fires on app-open ─────────────

    @Test
    fun hasOverdueTransfers_doesNotAutoNavigateToMigrationProgress() = runTest {
        // After Task 6 the overdue branch no longer auto-navigates — user reaches Progress via
        // home banner only.
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { hasOverdueTransfers() } returns true
            coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
        }
        val router = mockk<NavigationRouter>(relaxed = true)

        useCase(sdk = sdk, navigationRouter = router, pendingMigrationTorFailure = false).invoke()

        coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationProgressArgs) }
    }

    @Test
    fun pendingTorFailureStillAutoNavigatesToMigrationSending() = runTest {
        // The Tor-failure branch is the ONE auto-navigation kept after Task 6.
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { hasOverdueTransfers() } returns true
            coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
        }
        val router = mockk<NavigationRouter>(relaxed = true)

        useCase(sdk = sdk, navigationRouter = router, pendingMigrationTorFailure = true).invoke()

        coVerify(exactly = 1) { router.replaceAll(HomeArgs, MigrationSendingArgs) }
    }

    @Test
    fun pendingTorFailureDoesNotAutoNavigateToAnyOtherScreen() = runTest {
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { hasOverdueTransfers() } returns true
            coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
        }
        val router = mockk<NavigationRouter>(relaxed = true)

        useCase(sdk = sdk, navigationRouter = router, pendingMigrationTorFailure = true).invoke()

        coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationTransferInvalidArgs) }
        coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationTransferReviewArgs) }
        coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationProgressArgs) }
        coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationCompleteArgs) }
    }

    @Test
    fun requiresAttention_doesNotAutoNavigate() = runTest {
        // After Task 6, RequiresAttention no longer auto-navigates to MigrationTransferInvalidArgs.
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { getMigrationState() } returns MigrationState.RequiresAttention(AttentionReason.TransferExpired)
        }
        val router = mockk<NavigationRouter>(relaxed = true)

        useCase(sdk = sdk, navigationRouter = router, pendingMigrationTorFailure = false).invoke()

        coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationTransferInvalidArgs) }
        coVerify(exactly = 0) { router.replaceAll(any()) }
    }

    @Test
    fun complete_doesNotAutoNavigateToCelebration() = runTest {
        // After Task 6, MigrationState.Complete no longer auto-routes to MigrationCompleteArgs.
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { hasInvalidTransfers() } returns false
            coEvery { hasOverdueTransfers() } returns false
            coEvery { getMigrationState() } returns MigrationState.Complete
        }
        val router = mockk<NavigationRouter>(relaxed = true)

        useCase(
            sdk = sdk,
            navigationRouter = router,
            pendingMigrationTorFailure = false,
            savedPlan = mockk(relaxed = true),
        ).invoke()

        coVerify(exactly = 0) { router.replaceAll(HomeArgs, MigrationCompleteArgs) }
        coVerify(exactly = 0) { router.replaceAll(any()) }
    }

    // ── Stale write-ahead plan clearing (not navigation) ─────────────────────────────────

    @Test
    fun notStartedWithStaleWriteAheadPlanClearsTheStalePlan() = runTest {
        // MigrationReviewVM persists the plan just before the irreversible SDK commit; if that commit
        // never happened the SDK stays NotStarted while a stale plan lingers. The SDK state is
        // authoritative, so the stale plan is discarded (and nothing is navigated).
        val plans = mockk<MigrationPlanRepository>(relaxed = true) {
            coEvery { load() } returns mockk(relaxed = true)
        }
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { hasInvalidTransfers() } returns false
            coEvery { hasOverdueTransfers() } returns false
            coEvery { getMigrationState() } returns MigrationState.NotStarted
        }
        val router = mockk<NavigationRouter>(relaxed = true)

        useCase(sdk = sdk, navigationRouter = router, migrationPlanRepository = plans).invoke()

        coVerify(exactly = 1) { plans.clear() }
        coVerify(exactly = 0) { router.replaceAll(any()) }
    }

    @Test
    fun notStartedWithNoPlanLeavesEverythingAlone() = runTest {
        val plans = mockk<MigrationPlanRepository>(relaxed = true) {
            coEvery { load() } returns null
        }
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { hasInvalidTransfers() } returns false
            coEvery { hasOverdueTransfers() } returns false
            coEvery { getMigrationState() } returns MigrationState.NotStarted
        }
        val router = mockk<NavigationRouter>(relaxed = true)

        useCase(sdk = sdk, navigationRouter = router, migrationPlanRepository = plans).invoke()

        coVerify(exactly = 0) { plans.clear() }
        coVerify(exactly = 0) { router.replaceAll(any()) }
    }

    @Test
    fun noWalletAvailableDoesNothing() = runTest {
        val router = mockk<NavigationRouter>(relaxed = true)

        useCase(sdk = null, navigationRouter = router).invoke()

        coVerify(exactly = 0) { router.replaceAll(any()) }
    }

    // ── Lane A/B reconciliation tests ─────────────────────────────────────────────────────

    @Test
    fun laneAReconciliation_planExistsAndLaneInactive_schedulesLaneA() = runTest {
        // plan exists + isLaneAActive = false → migrationSyncScheduler.schedule called.
        val syncScheduler = mockk<MigrationSyncScheduler>(relaxed = true)
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
            coEvery { hasOverdueTransfers() } returns false
        }

        useCase(
            sdk = sdk,
            navigationRouter = mockk(relaxed = true),
            savedPlan = mockk(relaxed = true),
            migrationSyncScheduler = syncScheduler,
            isLaneBActive = { true },
            isLaneAActive = { false },
        ).invoke()

        // A short flat first arm — the worker's first run computes the precise boundary wake.
        verify { syncScheduler.schedule(any(), 60.seconds) }
    }

    @Test
    fun laneAReconciliation_planExistsAndLaneActive_doesNotScheduleLaneA() = runTest {
        // plan exists + isLaneAActive = true → migrationSyncScheduler.schedule NOT called.
        val syncScheduler = mockk<MigrationSyncScheduler>(relaxed = true)
        val sdk = mockk<OrchardMigrationSdk>(relaxed = true) {
            coEvery { getMigrationState() } returns MigrationState.InProgress(mockk(relaxed = true))
            coEvery { hasOverdueTransfers() } returns false
        }

        useCase(
            sdk = sdk,
            navigationRouter = mockk(relaxed = true),
            savedPlan = mockk(relaxed = true),
            migrationSyncScheduler = syncScheduler,
            isLaneBActive = { true },
            isLaneAActive = { true },
        ).invoke()

        verify(exactly = 0) { syncScheduler.schedule(any(), any()) }
    }
}
