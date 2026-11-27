package co.electriccoin.zcash.ui.common.model.migration.sim

import android.content.Context
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.fixture.AccountFixture
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.usecase.CheckMigrationRecoveryUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.MigrationWorkerRunState
import co.electriccoin.zcash.work.MigrationLiveDriver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives the real [CheckMigrationRecoveryUseCase] — the app-open re-entry router — against the
 * shared stateful [FakeOrchardMigrationSdk] instead of the ad-hoc `mockk` SDK the existing
 * [co.electriccoin.zcash.ui.common.usecase.CheckMigrationRecoveryUseCaseTest] uses. The difference
 * is that `getMigrationState()` is computed from a real seeded migration plan, so the recovery
 * decision is exercised against a self-consistent in-progress world.
 *
 * What is asserted:
 *  - A HEALTHY in-progress migration consults the worker-run-state check (revival would fire if
 *    the worker were absent) and does NOT auto-navigate (Task 6: only a pending Tor failure
 *    auto-navigates on app-open).
 *  - A completed migration with no saved plan skips reconciliation entirely.
 *  - A worker merely SCHEDULED for later (armed, not yet due) is accelerated to run now — the
 *    app-open forward-progress trigger, for users with no reliable background execution.
 *
 * Scope note: `getWorkerRunState`/`scheduleNow` are injected specifically so this file's
 * reconciliation/acceleration assertions don't need a real WorkManager-initialised `Context`
 * (the production defaults call `WorkManager`/`AlarmManager`, neither of which work under a plain
 * unit-test `Context` mock); this file drives them against the FakeSdk-derived InProgress state.
 */
class MigrationBackgroundRecoveryScenarioTest {
    // A single test account so accountDataSource.getAllAccounts() has exactly one account for the
    // driver-start/worker-revival loop to iterate — mirrors CheckMigrationRecoveryUseCaseTest.
    private val testSdkAccount =
        AccountFixture.new(
            accountUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        )
    private val testWalletAccount: WalletAccount =
        mockk(relaxed = true) {
            every { sdkAccount } returns testSdkAccount
            every { isSpendable } returns true
        }

    @BeforeTest
    fun resetThrottle() {
        CheckMigrationRecoveryUseCase.resetRunThrottleForTests()
    }

    private companion object {
        const val ANCHOR: Long = 4_000_000L
        const val PREP_ID: Long = 1L
        const val TRANSFER_A: Long = 20L
        const val TRANSFER_B: Long = 21L
    }

    /** An in-progress migration: one transfer already sent, one still pending. */
    private fun inProgressDriver(): MigrationSimDriver {
        val driver = MigrationSimDriver()
        driver.seedPlan(
            preparations =
                listOf(
                    MigrationSimDriver.SimPrep(id = PREP_ID, layer = 0, scheduledHeight = ANCHOR - 40L),
                ),
            transfers =
                listOf(
                    MigrationSimDriver.SimTransfer(
                        id = TRANSFER_A,
                        scheduledHeight = ANCHOR + 5L,
                        anchorBoundary = ANCHOR,
                        dependsOn = listOf(PREP_ID),
                    ),
                    MigrationSimDriver.SimTransfer(
                        id = TRANSFER_B,
                        scheduledHeight = ANCHOR + 15L,
                        anchorBoundary = ANCHOR,
                        dependsOn = listOf(PREP_ID),
                    ),
                ),
            startTip = ANCHOR - 40L,
        )
        driver.mine(id = PREP_ID, height = ANCHOR - 2L)
        driver.setTip(ANCHOR + 20L)
        return driver
    }

    private fun useCase(
        driver: MigrationSimDriver,
        navigationRouter: NavigationRouter,
        pendingMigrationTorFailure: Boolean = false,
        getWorkerRunState: suspend (String) -> MigrationWorkerRunState = { MigrationWorkerRunState.RUNNING },
        scheduleNow: suspend (String) -> Unit = {},
        migrationLiveDriver: MigrationLiveDriver = mockk(relaxed = true),
    ) = CheckMigrationRecoveryUseCase(
        getOrchardMigrationSdk =
            mockk<GetOrchardMigrationSdkUseCase> {
                coEvery { this@mockk() } returns driver.sdk
                // Explicit-account overload — used by the driver-start/worker-revival loop, which
                // now enumerates accountDataSource.getAllAccounts() instead of only the selected
                // account.
                coEvery { this@mockk(any()) } returns driver.sdk
            },
        persistableWalletProvider =
            mockk<PersistableWalletProvider>(relaxed = true) {
                coEvery { getPersistableWallet() } returns mockk(relaxed = true)
            },
        navigationRouter = navigationRouter,
        pendingMigrationTorFailureStorageProvider =
            mockk<PendingMigrationTorFailureStorageProvider> {
                coEvery { get() } returns pendingMigrationTorFailure
            },
        accountDataSource =
            mockk<AccountDataSource>(relaxed = true) {
                coEvery { getAllAccounts() } returns listOf(testWalletAccount)
            },
        context = mockk<Context>(relaxed = true),
        getWorkerRunState = getWorkerRunState,
        scheduleNow = scheduleNow,
        migrationLiveDriver = migrationLiveDriver,
    )

    @Test
    fun `healthy in-progress migration consults the worker-active check and does not auto-navigate`() =
        runTest {
            val driver = inProgressDriver()
            // Precondition sanity: the fake really is InProgress (not Complete/RequiresAttention).
            assertTrue(driver.sdk.getMigrationState() is MigrationState.InProgress)

            val router = mockk<NavigationRouter>(relaxed = true)
            var workerChecked = false

            useCase(
                driver = driver,
                navigationRouter = router,
                getWorkerRunState = {
                    workerChecked = true
                    MigrationWorkerRunState.RUNNING
                },
            ).invoke()

            // The reconciliation block ran against the live in-progress engine state.
            assertTrue(workerChecked, "an in-progress migration must check whether the worker chain is alive")
            // A healthy in-progress migration must NOT hijack the screen on app-open (Task 6).
            coVerify(exactly = 0) { router.replaceAll(any()) }
        }

    @Test
    fun `a completed migration skips reconciliation and does not navigate`() =
        runTest {
            // Drain the plan to Complete, then app-open: the engine is no longer InProgress, so with no
            // saved plan there is nothing to reconcile and nothing to navigate.
            val driver = inProgressDriver()
            val opts =
                cash.z.ecc.android.sdk
                    .NetworkPrivacyOptions(useTor = false)
            driver.sdk.finalizeReadyTransfers()
            driver.sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            driver.sdk.executeNextPendingTransfer(opts, useEstimatedTip = true)
            assertTrue(driver.sdk.getMigrationState() is MigrationState.Complete)

            val router = mockk<NavigationRouter>(relaxed = true)
            var workerChecked = false

            useCase(
                driver = driver,
                navigationRouter = router,
                getWorkerRunState = {
                    workerChecked = true
                    MigrationWorkerRunState.RUNNING
                },
            ).invoke()

            assertFalse(workerChecked, "a completed migration must not consult the worker-active check")
            coVerify(exactly = 0) { router.replaceAll(any()) }
        }

    @Test
    fun `an in-progress migration with a worker scheduled for later does not accelerate via scheduleNow`() =
        runTest {
            // SCHEDULED no longer triggers scheduleNow directly — the live driver (started
            // unconditionally whenever the engine is InProgress) is the fast path now.
            val driver = inProgressDriver()
            assertTrue(driver.sdk.getMigrationState() is MigrationState.InProgress)

            val router = mockk<NavigationRouter>(relaxed = true)
            var scheduled = false

            useCase(
                driver = driver,
                navigationRouter = router,
                getWorkerRunState = { MigrationWorkerRunState.SCHEDULED },
                scheduleNow = { scheduled = true },
            ).invoke()

            assertFalse(scheduled, "SCHEDULED must not call scheduleNow anymore — the live driver covers acceleration")
        }
}
