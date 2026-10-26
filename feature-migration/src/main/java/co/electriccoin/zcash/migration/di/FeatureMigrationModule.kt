package co.electriccoin.zcash.migration.di

import co.electriccoin.zcash.migration.MigrationAppHooksImpl
import co.electriccoin.zcash.migration.MigrationDebugActionsImpl
import co.electriccoin.zcash.migration.MigrationGateImpl
import co.electriccoin.zcash.migration.MigrationNavContributorImpl
import co.electriccoin.zcash.migration.MigrationNavigatorImpl
import co.electriccoin.zcash.migration.MigrationSyncedHookImpl
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.migration.MigrationDebugActions
import co.electriccoin.zcash.ui.common.migration.MigrationGate
import co.electriccoin.zcash.ui.common.migration.MigrationHomeMessageSource
import co.electriccoin.zcash.ui.common.migration.MigrationNavContributor
import co.electriccoin.zcash.ui.common.migration.MigrationNavigator
import co.electriccoin.zcash.ui.common.migration.MigrationSyncedHook
import co.electriccoin.zcash.ui.common.provider.HasLockedOrchardDustStorageProvider
import co.electriccoin.zcash.ui.common.provider.HasLockedOrchardDustStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.HasSeenMigrationCompleteStorageProvider
import co.electriccoin.zcash.ui.common.provider.HasSeenMigrationCompleteStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.ui.common.provider.MigrationUpgradeNoticeStorageProvider
import co.electriccoin.zcash.ui.common.provider.MigrationUpgradeNoticeStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProviderImpl
import co.electriccoin.zcash.ui.common.repository.MigrationTransferStateRepository
import co.electriccoin.zcash.ui.common.repository.MigrationTransferStateRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepository
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.PendingMigrationTorFailureDecisionRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationTorFailureDecisionRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.CheckMigrationRecoveryUseCase
import co.electriccoin.zcash.ui.common.usecase.DebugStartMigrationE2EUseCase
import co.electriccoin.zcash.ui.common.usecase.FinalizeMigrationScheduleUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationGateInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationPrivacyOrReviewDestinationUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationSnapshotUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.LockOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.MigrationHomeMessageSourceImpl
import co.electriccoin.zcash.ui.common.usecase.ObserveMigrationLiveReadoutUseCase
import co.electriccoin.zcash.ui.common.usecase.OnMigrationSyncCompletedUseCase
import co.electriccoin.zcash.ui.common.usecase.RestartMigrationUseCase
import co.electriccoin.zcash.ui.common.usecase.ScheduleNextMigrationWindowUseCase
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryVM
import co.electriccoin.zcash.ui.screen.migration.complete.MigrationCompleteVM
import co.electriccoin.zcash.ui.screen.migration.customservertor.MigrationCustomServerTorVM
import co.electriccoin.zcash.ui.screen.migration.howitworks.MigrationHowItWorksVM
import co.electriccoin.zcash.ui.screen.migration.invalid.MigrationTransferInvalidVM
import co.electriccoin.zcash.ui.screen.migration.keystonescan.MigrationKeystoneScanVM
import co.electriccoin.zcash.ui.screen.migration.keystonesign.MigrationKeystoneSignVM
import co.electriccoin.zcash.ui.screen.migration.lockexplainer.MigrationLockExplainerVM
import co.electriccoin.zcash.ui.screen.migration.notification.MigrationNotificationVM
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyVM
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressVM
import co.electriccoin.zcash.ui.screen.migration.restart.MigrationRestartVM
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewVM
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledVM
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingVM
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupVM
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessVM
import co.electriccoin.zcash.ui.screen.migration.torfailure.MigrationTorFailureVM
import co.electriccoin.zcash.work.MigrationDriveOnce
import co.electriccoin.zcash.work.MigrationLiveDriver
import co.electriccoin.zcash.work.MigrationLiveDriverImpl
import co.electriccoin.zcash.work.MigrationScheduler
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Everything the migration feature contributes to the app's Koin graph — its own providers,
 * repositories, use cases, workers' schedulers, view models, and the implementations of ui-lib's
 * migration contracts (see MigrationContracts.kt). Wired in ZcashApplication.startKoin; when the
 * migration era ends, drop this module from that list and delete the feature module.
 */
val featureMigrationModule =
    module {
        // Contract implementations (the ui-lib seam)
        singleOf(::MigrationHomeMessageSourceImpl) bind MigrationHomeMessageSource::class
        singleOf(::MigrationGateImpl) bind MigrationGate::class
        singleOf(::MigrationSyncedHookImpl) bind MigrationSyncedHook::class
        singleOf(::MigrationAppHooksImpl) bind MigrationAppHooks::class
        singleOf(::MigrationNavigatorImpl) bind MigrationNavigator::class
        singleOf(::MigrationNavContributorImpl) bind MigrationNavContributor::class
        singleOf(::MigrationDebugActionsImpl) bind MigrationDebugActions::class

        // Providers
        singleOf(::HasSeenMigrationCompleteStorageProviderImpl) bind
            HasSeenMigrationCompleteStorageProvider::class
        singleOf(::MigrationUpgradeNoticeStorageProviderImpl) bind MigrationUpgradeNoticeStorageProvider::class
        singleOf(::HasLockedOrchardDustStorageProviderImpl) bind
            HasLockedOrchardDustStorageProvider::class
        singleOf(::IsMigrationTorEnabledStorageProviderImpl) bind IsMigrationTorEnabledStorageProvider::class
        singleOf(::PendingMigrationTorFailureStorageProviderImpl) bind
            PendingMigrationTorFailureStorageProvider::class
        singleOf(::MigrationNotifier)
        factoryOf(::MigrationScheduler)
        singleOf(::MigrationDriveOnce)
        single<MigrationLiveDriver> {
            MigrationLiveDriverImpl(
                migrationDriveOnce = get(),
                getOrchardMigrationSdk = { accountKeyId -> get<GetOrchardMigrationSdkUseCase>().invoke(accountKeyId) },
                migrationTransferStateRepository = get(),
            )
        }

        // Repositories
        singleOf(::PendingMigrationScheduleRepositoryImpl) bind PendingMigrationScheduleRepository::class
        singleOf(::RestartMigrationScheduleRepositoryImpl) bind RestartMigrationScheduleRepository::class
        singleOf(::PendingMigrationTorFailureDecisionRepositoryImpl) bind
            PendingMigrationTorFailureDecisionRepository::class
        singleOf(::PendingKeystoneMigrationPcztsRepositoryImpl) bind PendingKeystoneMigrationPcztsRepository::class
        singleOf(::MigrationTransferStateRepositoryImpl) bind MigrationTransferStateRepository::class

        // Use cases
        factoryOf(::GetOrchardMigrationSdkUseCase)
        factoryOf(::ObserveMigrationLiveReadoutUseCase)
        factoryOf(::GetMigrationSnapshotUseCase)
        factoryOf(::LockOrchardBalanceUseCase)
        factoryOf(::GetMigrationPrivacyOrReviewDestinationUseCase)
        factoryOf(::GetMigrationGateInfoUseCase)
        // Explicit factory: the defaulted isWorkerActive lambda must use its Kotlin default —
        // factoryOf resolves ALL constructor params via Koin and dies on the Function1
        // (NoDefinitionFoundException at startup, caught on-emulator 2026-07-28).
        factory {
            CheckMigrationRecoveryUseCase(
                getOrchardMigrationSdk = get(),
                persistableWalletProvider = get(),
                navigationRouter = get(),
                pendingMigrationTorFailureStorageProvider = get(),
                accountDataSource = get(),
                context = get(),
                migrationLiveDriver = get(),
            )
        }
        factoryOf(::FinalizeMigrationScheduleUseCase)
        factoryOf(::DebugStartMigrationE2EUseCase)
        factoryOf(::ScheduleNextMigrationWindowUseCase)
        factoryOf(::OnMigrationSyncCompletedUseCase)
        factoryOf(::RestartMigrationUseCase)

        // View models
        viewModelOf(::MigrationSetupVM)
        viewModelOf(::MigrationHowItWorksVM)
        viewModelOf(::MigrationProgressVM)
        viewModelOf(::MigrationReviewVM)
        viewModelOf(::MigrationKeystoneSignVM)
        viewModelOf(::MigrationKeystoneScanVM)
        viewModelOf(::MigrationSendingVM)
        viewModelOf(::MigrationSuccessVM)
        viewModelOf(::MigrationScheduledVM)
        viewModelOf(::MigrationCompleteVM)
        viewModelOf(::MigrationBatteryVM)
        viewModelOf(::MigrationNotificationVM)
        viewModelOf(::MigrationPrivacyVM)
        viewModelOf(::MigrationLockExplainerVM)
        viewModelOf(::MigrationCustomServerTorVM)
        viewModelOf(::MigrationTorFailureVM)
        viewModelOf(::MigrationTransferInvalidVM)
        viewModelOf(::MigrationRestartVM)
    }
