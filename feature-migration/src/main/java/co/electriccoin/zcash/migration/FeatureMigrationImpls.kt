package co.electriccoin.zcash.migration

import android.content.Context
import android.content.Intent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.migration.MigrationDebugActions
import co.electriccoin.zcash.ui.common.migration.MigrationGate
import co.electriccoin.zcash.ui.common.migration.MigrationNavContributor
import co.electriccoin.zcash.ui.common.migration.MigrationNavigator
import co.electriccoin.zcash.ui.common.migration.MigrationSyncedHook
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.usecase.CheckMigrationRecoveryUseCase
import co.electriccoin.zcash.ui.common.usecase.DebugStartMigrationE2EUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.OnMigrationSyncCompletedUseCase
import co.electriccoin.zcash.ui.common.usecase.RestartMigrationUseCase
import co.electriccoin.zcash.ui.dialogComposable
import co.electriccoin.zcash.ui.screen.home.HomeArgs
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryArgs
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryScreen
import co.electriccoin.zcash.ui.screen.migration.complete.MigrationCompleteArgs
import co.electriccoin.zcash.ui.screen.migration.complete.MigrationCompleteScreen
import co.electriccoin.zcash.ui.screen.migration.customservertor.MigrationCustomServerTorArgs
import co.electriccoin.zcash.ui.screen.migration.customservertor.MigrationCustomServerTorScreen
import co.electriccoin.zcash.ui.screen.migration.howitworks.MigrationHowItWorksArgs
import co.electriccoin.zcash.ui.screen.migration.howitworks.MigrationHowItWorksScreen
import co.electriccoin.zcash.ui.screen.migration.invalid.MigrationTransferInvalidArgs
import co.electriccoin.zcash.ui.screen.migration.invalid.MigrationTransferInvalidScreen
import co.electriccoin.zcash.ui.screen.migration.keystonescan.MigrationKeystoneScanArgs
import co.electriccoin.zcash.ui.screen.migration.keystonescan.MigrationKeystoneScanScreen
import co.electriccoin.zcash.ui.screen.migration.keystonesign.MigrationKeystoneSignArgs
import co.electriccoin.zcash.ui.screen.migration.keystonesign.MigrationKeystoneSignScreen
import co.electriccoin.zcash.ui.screen.migration.lockexplainer.MigrationLockExplainerArgs
import co.electriccoin.zcash.ui.screen.migration.lockexplainer.MigrationLockExplainerScreen
import co.electriccoin.zcash.ui.screen.migration.notification.MigrationNotificationArgs
import co.electriccoin.zcash.ui.screen.migration.notification.MigrationNotificationScreen
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyArgs
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyScreen
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressArgs
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressScreen
import co.electriccoin.zcash.ui.screen.migration.restart.MigrationRestartArgs
import co.electriccoin.zcash.ui.screen.migration.restart.MigrationRestartScreen
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewArgs
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewScreen
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledArgs
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledScreen
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingArgs
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingScreen
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupArgs
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupScreen
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessArgs
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessScreen
import co.electriccoin.zcash.ui.screen.migration.torfailure.MigrationTorFailureArgs
import co.electriccoin.zcash.ui.screen.migration.torfailure.MigrationTorFailureScreen
import co.electriccoin.zcash.work.MigrationScheduler
import co.electriccoin.zcash.work.MigrationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MigrationGateImpl(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
) : MigrationGate {
    // Engine state IS the gate now — no app-side plan marker exists. Selected-account scoped
    // (matches the sync it gates).
    // TODO [#0]: see post-adoption task C2 for the any-account variant.
    //
    // Both methods guard their state read (2026-08-07 Fable review): this had been unguarded, and
    // isRestartAvailable() is called from AdvancedSettingsVM's bare `viewModelScope.launch` (no
    // LCE, no try/catch of its own) — a "database is locked" throw there would crash the app
    // straight from the Settings screen. isMigrationActive() is called from SyncWorker.doWork()
    // every scheduled background-sync run; a throw there would just fail that one worker run
    // (WorkManager retries), but is guarded the same way for consistency and to avoid a spurious
    // retry loop on a transient DB-lock read.
    //
    // getMigrationStateUnreconciled(), not getMigrationState(): a gate never mutates (2026-08-07
    // read/write-separation design) — a just-mined final transfer this account hasn't reconciled
    // yet keeps the gate conservative for at most one drive-loop publish cycle, which fails safe
    // (sync stays gated slightly longer, never the reverse).
    override suspend fun isMigrationActive(): Boolean =
        runCatching {
            getOrchardMigrationSdk().getMigrationStateUnreconciled() is cash.z.ecc.android.sdk.MigrationState.InProgress
        }.getOrDefault(false)

    override suspend fun isRestartAvailable(): Boolean =
        runCatching {
            when (getOrchardMigrationSdk().getMigrationStateUnreconciled()) {
                is cash.z.ecc.android.sdk.MigrationState.InProgress,
                is cash.z.ecc.android.sdk.MigrationState.RequiresAttention -> true

                else -> false
            }
        }.getOrDefault(false)
}

class MigrationSyncedHookImpl(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val onMigrationSyncCompleted: OnMigrationSyncCompletedUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : MigrationSyncedHook {
    override suspend fun onSynced() {
        // Engine state is the only "migration active" signal — no plan cache exists anymore.
        // getMigrationStateUnreconciled(): this gate never mutates (2026-08-07 read/write-
        // separation design) — onMigrationSyncCompleted below still runs the real reconcile pass
        // via the drive loop's own cadence regardless of this gate's staleness.
        val isInProgress =
            getOrchardMigrationSdk().getMigrationStateUnreconciled() is cash.z.ecc.android.sdk.MigrationState.InProgress
        if (!isInProgress) return
        val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
        onMigrationSyncCompleted(accountKeyId)
    }
}

class MigrationAppHooksImpl(
    private val checkMigrationRecovery: CheckMigrationRecoveryUseCase,
    private val debugStartMigrationE2E: DebugStartMigrationE2EUseCase,
    private val navigationRouter: NavigationRouter,
    private val accountDataSource: AccountDataSource,
    private val migrationNotifier: MigrationNotifier,
    private val context: Context,
) : MigrationAppHooks {
    override fun handleIntent(
        intent: Intent,
        scope: CoroutineScope
    ): Boolean =
        when {
            BuildConfig.DEBUG &&
                intent.getBooleanExtra(DebugStartMigrationE2EUseCase.EXTRA_START_MIGRATION, false) -> {
                // Debug-only E2E driver: reset + commit a fresh AUTOMATIC plan from adb, no UI taps.
                scope.launch { debugStartMigrationE2E() }
                true
            }

            intent.getBooleanExtra(MigrationNotifier.EXTRA_OPEN_MIGRATION, false) -> {
                // replaceAll ensures Home is always on the back stack regardless of how the app
                // was opened. Account selection first — the migration screens read the SELECTED
                // account, so a Keystone notification tapped with Zodl selected must switch.
                scope.launch {
                    selectNotificationAccount(intent)
                    navigationRouter.replaceAll(HomeArgs, MigrationProgressArgs)
                }
                true
            }

            intent.getBooleanExtra(MigrationNotifier.EXTRA_RUN_STEP, false) -> {
                // Dead-man's-switch tap: everything is pre-signed, so no review UI exists — the
                // app open exists purely to give the OS a live process. RE-KICK the worker
                // immediately (it runs fine while the app is foreground) and land on Progress so
                // the user watches the step happen; the worker's run start clears the
                // notification.
                scope.launch {
                    selectNotificationAccount(intent)
                    intent.getStringExtra(MigrationNotifier.EXTRA_ACCOUNT_KEY_ID)?.let { key ->
                        migrationLog("AppHooks: step-due tap — kicking the worker for $key")
                        MigrationScheduler(context).schedule(key, kotlin.time.Duration.ZERO)
                    }
                    navigationRouter.replaceAll(HomeArgs, MigrationProgressArgs)
                }
                true
            }

            else -> {
                false
            }
        }

    /**
     * Selects the account the tapped notification belongs to (its storage-key id travels in
     * [MigrationNotifier.EXTRA_ACCOUNT_KEY_ID]) before the migration screens — which all read the
     * SELECTED account — are pushed. No-op when the extra is absent (pre-upgrade notification),
     * the account no longer exists (deleted/disconnected — the kill switch already cancelled its
     * notifications; navigation then just shows the selected account, same as before), or it is
     * already selected. Best-effort: a failure here must never block the navigation itself.
     */
    private suspend fun selectNotificationAccount(intent: Intent) {
        val accountKeyId = intent.getStringExtra(MigrationNotifier.EXTRA_ACCOUNT_KEY_ID) ?: return
        runCatching {
            val target =
                accountDataSource
                    .getAllAccounts()
                    .firstOrNull { it.sdkAccount.accountUuid.toStorageKeyId() == accountKeyId }
                    ?: return
            if (accountDataSource
                    .getSelectedAccount()
                    .sdkAccount.accountUuid
                    .toStorageKeyId() != accountKeyId
            ) {
                migrationLog("AppHooks: notification tap — switching to account $accountKeyId")
                accountDataSource.selectAccount(target)
            }
        }.onFailure {
            migrationLog(
                "AppHooks: notification account switch failed (${it.message}) — navigating on the selected account."
            )
        }
    }

    override suspend fun checkRecovery() = checkMigrationRecovery()

    override suspend fun cancelMigrationWork(accountKeyId: String?) {
        val keys =
            accountKeyId?.let { listOf(it) }
                ?: runCatching {
                    accountDataSource.getAllAccounts().map { it.sdkAccount.accountUuid.toStorageKeyId() }
                }.getOrDefault(emptyList())
        keys.forEach { key ->
            MigrationScheduler(context).cancel(key)
            migrationNotifier.cancel(key)
        }
        // Belt-and-braces (wallet reset): WorkManager auto-tags every request with its worker
        // class name, so a tag sweep also catches jobs whose account key we can no longer resolve.
        if (accountKeyId == null) {
            val wm = androidx.work.WorkManager.getInstance(context)
            wm.cancelAllWorkByTag(MigrationWorker::class.java.name)
        }
        migrationLog("AppHooks: cancelled migration work (accounts=${keys.size}, sweep=${accountKeyId == null})")
    }
}

class MigrationNavigatorImpl(
    private val navigationRouter: NavigationRouter,
) : MigrationNavigator {
    override fun backToMigrationReview() = navigationRouter.backTo(MigrationReviewArgs::class)

    override fun forwardToRestartMigration() = navigationRouter.forward(MigrationRestartArgs)
}

class MigrationNavContributorImpl : MigrationNavContributor {
    override fun contribute(navGraphBuilder: NavGraphBuilder) {
        with(navGraphBuilder) {
            composable<MigrationSetupArgs> { MigrationSetupScreen() }
            composable<MigrationHowItWorksArgs> { MigrationHowItWorksScreen() }
            composable<MigrationReviewArgs> { MigrationReviewScreen(it.toRoute()) }
            composable<MigrationKeystoneSignArgs> { MigrationKeystoneSignScreen(it.toRoute()) }
            composable<MigrationKeystoneScanArgs> { MigrationKeystoneScanScreen(it.toRoute()) }
            composable<MigrationBatteryArgs> { MigrationBatteryScreen() }
            composable<MigrationNotificationArgs> { MigrationNotificationScreen() }
            dialogComposable<MigrationPrivacyArgs> { MigrationPrivacyScreen(it.toRoute()) }
            dialogComposable<MigrationCustomServerTorArgs> { MigrationCustomServerTorScreen(it.toRoute()) }
            dialogComposable<MigrationTorFailureArgs> { MigrationTorFailureScreen() }
            dialogComposable<MigrationLockExplainerArgs> { MigrationLockExplainerScreen() }
            composable<MigrationSendingArgs> { MigrationSendingScreen() }
            composable<MigrationSuccessArgs> { MigrationSuccessScreen(it.toRoute()) }
            composable<MigrationScheduledArgs> { MigrationScheduledScreen() }
            composable<MigrationCompleteArgs> { MigrationCompleteScreen(it.toRoute()) }
            composable<MigrationProgressArgs> { MigrationProgressScreen() }
            composable<MigrationTransferInvalidArgs> { MigrationTransferInvalidScreen() }
            composable<MigrationRestartArgs> { MigrationRestartScreen() }
        }
    }
}

class MigrationDebugActionsImpl(
    private val restartMigrationUseCase: RestartMigrationUseCase,
    private val accountDataSource: AccountDataSource,
    private val pendingMigrationTorFailureStorageProvider: PendingMigrationTorFailureStorageProvider,
    private val migrationNotifier: MigrationNotifier,
    private val checkMigrationRecovery: CheckMigrationRecoveryUseCase,
) : MigrationDebugActions {
    // Promoted to the production RestartMigrationUseCase (see its kdoc) so there is a single
    // orchestration for both the debug action and the user-facing "Restart Migration" flow.
    override suspend fun restartMigration(): String {
        restartMigrationUseCase()
        return "Migration reset. Propose a new migration to test."
    }

    // Reproduces spec §6.2's "background Tor failure" state (MigrationWorker's non-retryable
    // NetworkError-while-useTor branch) without waiting for a real background run to fail — sets
    // the same persisted flag and posts the same notification, then immediately re-runs the same
    // on-launch reconciliation HomeVM's init{} triggers, so the Sending screen shows up right away
    // instead of only on the next app relaunch/foreground.
    override suspend fun simulateTorFailure(): String {
        val accountKeyId =
            accountDataSource
                .getSelectedAccount()
                .sdkAccount.accountUuid
                .toStorageKeyId()
        pendingMigrationTorFailureStorageProvider.store(true)
        migrationNotifier.notifyMigrationTorFailure(accountKeyId)
        checkMigrationRecovery()
        return "Pending Tor failure flag set. Routing to the Sending screen now " +
            "(same routing HomeVM triggers on every launch/foreground)."
    }
}
