package co.electriccoin.zcash.di

/*
 * Koin graph smoke-test for all migration ViewModels.
 *
 * WHY THIS TEST EXISTS
 * --------------------
 * Koin's [viewModelOf] resolves every constructor parameter by type at VM-factory-call time
 * (inside the Android ViewModel infrastructure, not at app startup). A constructor param whose
 * type is not registered in the Koin graph (e.g. a primitive [Boolean] or any unbound class)
 * causes a [org.koin.core.error.NoDefinitionFoundException] when the screen opens — NOT when
 * the app starts or compiles. VM tests that construct the VM directly with mock arguments bypass
 * Koin entirely and therefore cannot catch this class of bug.
 *
 * The specific bug that prompted this test: MigrationProgressVM had a `debugSyncEnabled: Boolean`
 * constructor param that Koin couldn't resolve, crashing the screen on open. No test caught it.
 *
 * HOW IT WORKS
 * ------------
 * Each test builds a [koinApplication] containing ONLY the [featureMigrationModule] (the module under
 * test) plus a [stubsModule] that provides mockk stubs for every type the migration VMs need.
 * It then resolves each migration VM via [org.koin.core.Koin.get], which triggers the same
 * reflective constructor-argument resolution that [viewModelOf] uses at runtime. A missing
 * type fails here instead of on-device.
 *
 * The test PASSES on current code (constructor params are all interface/class types registered in
 * the stubs module) and would have FAILED before the Boolean fix.
 */

import android.content.Context
import co.electriccoin.zcash.migration.di.featureMigrationModule
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.ZashiSpendingKeyDataSource
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.HasLockedOrchardDustStorageProvider
import co.electriccoin.zcash.ui.common.provider.HasSeenMigrationCompleteStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsBackgroundExecutionAvailableProvider
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.LastNetworkActivityStorageProvider
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationTorFailureDecisionRepository
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.FinalizeMigrationScheduleUseCase
import co.electriccoin.zcash.ui.common.usecase.GetBalancePoolsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetIronwoodBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationPrivacyOrReviewDestinationUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.LockOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.ScheduleNextMigrationWindowUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewTransactionDetailAfterSuccessfulProposalUseCase
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryVM
import co.electriccoin.zcash.ui.screen.migration.complete.MigrationCompleteArgs
import co.electriccoin.zcash.ui.screen.migration.complete.MigrationCompleteVM
import co.electriccoin.zcash.ui.screen.migration.customservertor.MigrationCustomServerTorArgs
import co.electriccoin.zcash.ui.screen.migration.customservertor.MigrationCustomServerTorVM
import co.electriccoin.zcash.ui.screen.migration.howitworks.MigrationHowItWorksVM
import co.electriccoin.zcash.ui.screen.migration.invalid.MigrationTransferInvalidVM
import co.electriccoin.zcash.ui.screen.migration.keystonescan.MigrationKeystoneScanArgs
import co.electriccoin.zcash.ui.screen.migration.keystonescan.MigrationKeystoneScanVM
import co.electriccoin.zcash.ui.screen.migration.keystonesign.MigrationKeystoneSignArgs
import co.electriccoin.zcash.ui.screen.migration.keystonesign.MigrationKeystoneSignVM
import co.electriccoin.zcash.ui.screen.migration.lockexplainer.MigrationLockExplainerVM
import co.electriccoin.zcash.ui.screen.migration.notification.MigrationNotificationVM
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyArgs
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyVM
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressVM
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewArgs
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewVM
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledVM
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingVM
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupVM
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessArgs
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessVM
import co.electriccoin.zcash.ui.screen.migration.torfailure.MigrationTorFailureVM
import co.electriccoin.zcash.work.MigrationScheduler
import io.mockk.mockk
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Smoke-tests that every migration ViewModel can be resolved through the Koin [featureMigrationModule]
 * without a [org.koin.core.error.NoDefinitionFoundException]. Covers (in [featureMigrationModule]
 * registration order):
 *
 *   MigrationSetupVM, MigrationHowItWorksVM, MigrationProgressVM, MigrationTransferReviewVM,
 *   MigrationReviewVM, MigrationKeystoneSignVM, MigrationKeystoneScanVM, MigrationSendingVM,
 *   MigrationSuccessVM, MigrationScheduledVM, MigrationCompleteVM, MigrationBatteryVM,
 *   MigrationNotificationVM, MigrationPrivacyVM, MigrationLockExplainerVM,
 *   MigrationCustomServerTorVM, MigrationTorFailureVM, MigrationTransferInvalidVM
 */
class MigrationKoinGraphSmokeTest {
    private lateinit var koin: KoinApplication

    @BeforeTest
    fun setUp() {
        // The stubs module provides mockk instances for every type the migration VMs inject.
        // Concrete arg data-classes (MigrationReviewArgs, etc.) are provided as real instances
        // since data classes cannot be created lazily by Koin without additional factory setup.
        // Context is mocked because viewModelModule contains MigrationProgressVM which injects it.
        val stubsModule =
            module {
                // Framework
                single<Context> { mockk(relaxed = true) }

                // NavigationRouter
                single<NavigationRouter> { mockk(relaxed = true) }

                // UseCases
                factory { mockk<ErrorMapperUseCase>(relaxed = true) }
                factory { mockk<GetOrchardMigrationSdkUseCase>(relaxed = true) }
                factory { mockk<GetSelectedWalletAccountUseCase>(relaxed = true) }
                factory { mockk<GetOrchardBalanceUseCase>(relaxed = true) }
                factory { mockk<GetIronwoodBalanceUseCase>(relaxed = true) }
                factory { mockk<GetBalancePoolsUseCase>(relaxed = true) }
                factory { mockk<LockOrchardBalanceUseCase>(relaxed = true) }
                factory { mockk<FinalizeMigrationScheduleUseCase>(relaxed = true) }
                factory { mockk<ScheduleNextMigrationWindowUseCase>(relaxed = true) }
                factory { mockk<ViewTransactionDetailAfterSuccessfulProposalUseCase>(relaxed = true) }
                factory { mockk<SubmitProposalUseCase>(relaxed = true) }
                factory { mockk<GetMigrationPrivacyOrReviewDestinationUseCase>(relaxed = true) }

                // Repositories
                single<ExchangeRateRepository> { mockk(relaxed = true) }
                single<PendingMigrationScheduleRepository> { mockk(relaxed = true) }
                single<RestartMigrationScheduleRepository> { mockk(relaxed = true) }
                single<PendingMigrationTorFailureDecisionRepository> { mockk(relaxed = true) }
                single<PendingKeystoneMigrationPcztsRepository> { mockk(relaxed = true) }
                single<ZashiProposalRepository> { mockk(relaxed = true) }
                single<KeystoneProposalRepository> { mockk(relaxed = true) }
                single<BiometricRepository> { mockk(relaxed = true) }

                // DataSources
                single<ZashiSpendingKeyDataSource> { mockk(relaxed = true) }
                single<ProposalDataSource> { mockk(relaxed = true) }

                // Providers
                single<SynchronizerProvider> { mockk(relaxed = true) }
                single<LastNetworkActivityStorageProvider> { mockk(relaxed = true) }
                single<IsMigrationTorEnabledStorageProvider> { mockk(relaxed = true) }
                single<PendingMigrationTorFailureStorageProvider> { mockk(relaxed = true) }
                single<HasSeenMigrationCompleteStorageProvider> { mockk(relaxed = true) }
                single<HasLockedOrchardDustStorageProvider> { mockk(relaxed = true) }
                single<IsBackgroundExecutionAvailableProvider> { mockk(relaxed = true) }
                single<ApplicationStateProvider> { mockk(relaxed = true) }

                // Concrete scheduler classes (registered as factoryOf in providerModule; migration VMs
                // inject them directly, not via an interface).
                factory { mockk<MigrationScheduler>(relaxed = true) }

                // Args data-classes (carried as constructor params for VMs that accept navigation args).
                // Providing a canonical instance is the simplest way to satisfy the Koin type lookup.
                factory { MigrationCustomServerTorArgs(mode = MigrationMode.AUTOMATIC) }
                factory { MigrationKeystoneScanArgs(mode = MigrationMode.AUTOMATIC) }
                factory { MigrationKeystoneSignArgs(mode = MigrationMode.AUTOMATIC) }
                factory { MigrationPrivacyArgs(mode = MigrationMode.AUTOMATIC) }
                factory { MigrationReviewArgs(mode = MigrationMode.AUTOMATIC) }
                factory { MigrationSuccessArgs(txId = null) }
                factory { MigrationCompleteArgs() }
            }

        koin =
            koinApplication {
                modules(featureMigrationModule, stubsModule)
            }
    }

    @AfterTest
    fun tearDown() {
        koin.close()
    }

    // ── individual VM resolution tests ────────────────────────────────────────
    // Each test calls koin.koin.get<VM>() which exercises the same reflective
    // constructor-argument lookup that Koin's viewModelOf lambda performs at
    // runtime. A missing binding throws NoDefinitionFoundException → test fails.

    @Test
    fun migrationSetupVM_resolvesFromKoin() {
        koin.koin.get<MigrationSetupVM>()
    }

    @Test
    fun migrationHowItWorksVM_resolvesFromKoin() {
        koin.koin.get<MigrationHowItWorksVM>()
    }

    /**
     * Primary regression guard: this VM had a `debugSyncEnabled: Boolean` constructor param that
     * Koin could not resolve, crashing the screen at open (NoDefinitionFoundException).
     * The param was removed in favour of reading [co.electriccoin.zcash.ui.BuildConfig.DEBUG]
     * inline. This test would have failed BEFORE that fix.
     */
    @Test
    fun migrationProgressVM_resolvesFromKoin() {
        koin.koin.get<MigrationProgressVM>()
    }

    @Test
    fun migrationReviewVM_resolvesFromKoin() {
        koin.koin.get<MigrationReviewVM>()
    }

    @Test
    fun migrationKeystoneSignVM_resolvesFromKoin() {
        koin.koin.get<MigrationKeystoneSignVM>()
    }

    @Test
    fun migrationKeystoneScanVM_resolvesFromKoin() {
        koin.koin.get<MigrationKeystoneScanVM>()
    }

    @Test
    fun migrationSendingVM_resolvesFromKoin() {
        koin.koin.get<MigrationSendingVM>()
    }

    @Test
    fun migrationSuccessVM_resolvesFromKoin() {
        koin.koin.get<MigrationSuccessVM>()
    }

    @Test
    fun migrationScheduledVM_resolvesFromKoin() {
        koin.koin.get<MigrationScheduledVM>()
    }

    @Test
    fun migrationCompleteVM_resolvesFromKoin() {
        koin.koin.get<MigrationCompleteVM>()
    }

    @Test
    fun migrationBatteryVM_resolvesFromKoin() {
        koin.koin.get<MigrationBatteryVM>()
    }

    @Test
    fun migrationNotificationVM_resolvesFromKoin() {
        koin.koin.get<MigrationNotificationVM>()
    }

    @Test
    fun migrationPrivacyVM_resolvesFromKoin() {
        koin.koin.get<MigrationPrivacyVM>()
    }

    @Test
    fun migrationLockExplainerVM_resolvesFromKoin() {
        koin.koin.get<MigrationLockExplainerVM>()
    }

    @Test
    fun migrationCustomServerTorVM_resolvesFromKoin() {
        koin.koin.get<MigrationCustomServerTorVM>()
    }

    @Test
    fun migrationTorFailureVM_resolvesFromKoin() {
        koin.koin.get<MigrationTorFailureVM>()
    }

    @Test
    fun migrationTransferInvalidVM_resolvesFromKoin() {
        koin.koin.get<MigrationTransferInvalidVM>()
    }
}
