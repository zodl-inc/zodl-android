package co.electriccoin.zcash.app

import androidx.lifecycle.ProcessLifecycleOwner
import cash.z.ecc.android.sdk.Synchronizer
import co.electriccoin.zcash.crash.android.GlobalCrashReporter
import co.electriccoin.zcash.crash.android.di.CrashReportersProvider
import co.electriccoin.zcash.crash.android.di.crashProviderModule
import co.electriccoin.zcash.di.addressBookModule
import co.electriccoin.zcash.di.coreModule
import co.electriccoin.zcash.di.dataSourceModule
import co.electriccoin.zcash.di.mapperModule
import co.electriccoin.zcash.di.metadataModule
import co.electriccoin.zcash.di.providerModule
import co.electriccoin.zcash.di.repositoryModule
import co.electriccoin.zcash.di.useCaseModule
import co.electriccoin.zcash.di.viewModelModule
import co.electriccoin.zcash.migration.di.featureMigrationModule
import co.electriccoin.zcash.spackle.StrictModeCompat
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.spackle.process.ProcessNameCompat
import co.electriccoin.zcash.ui.common.provider.CrashReportingStorageProvider
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.ApplicationStateRepository
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.repository.FlexaRepository
import co.electriccoin.zcash.ui.common.repository.HomeMessageCacheRepository
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.repository.WalletSnapshotRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveSeedMismatchUseCase
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.voting.di.featureVotingModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf

class ZcashApplication : CoroutineApplication() {
    private val flexaRepository by inject<FlexaRepository>()
    private val getAvailableCrashReporters: CrashReportersProvider by inject()
    private val homeMessageCacheRepository: HomeMessageCacheRepository by inject()
    private val walletSnapshotRepository: WalletSnapshotRepository by inject()
    private val crashReportingStorageProvider: CrashReportingStorageProvider by inject()
    private val applicationStateRepository: ApplicationStateRepository by inject {
        parametersOf(ProcessLifecycleOwner.get().lifecycle)
    }
    private val walletRepository: WalletRepository by inject()
    private val automaticServerRepository: AutomaticServerRepository by inject()
    private val synchronizerProvider: SynchronizerProvider by inject()
    private val navigateToError: NavigateToErrorUseCase by inject()
    private val migrationNotifier: MigrationNotifier by inject()
    private val observeSeedMismatch: ObserveSeedMismatchUseCase by inject()

    override fun onCreate() {
        super.onCreate()

        configureLogging()

        if (isCrashProcess()) {
            Twig.info { "Skipping app initialization in the crash-reporting process" }
            return
        }

        preloadSdkNativeLibrary()

        configureStrictMode()

        startKoin {
            androidLogger()
            androidContext(this@ZcashApplication)
            modules(
                coreModule,
                providerModule,
                crashProviderModule,
                dataSourceModule,
                repositoryModule,
                addressBookModule,
                metadataModule,
                useCaseModule,
                mapperModule,
                viewModelModule,
                featureMigrationModule,
                featureVotingModule
            )
        }

        // Since analytics will need disk IO internally, we want this to be registered after strict
        // mode is configured to ensure none of that IO happens on the main thread
        configureAnalytics()

        migrationNotifier.createChannel()
        flexaRepository.init()
        homeMessageCacheRepository.init()
        walletSnapshotRepository.init()
        applicationStateRepository.init()
        automaticServerRepository.init()
        walletRepository.init()
        observeSynchronizerError()
        applicationScope.launch { observeSeedMismatch() }
    }

    /**
     * Kicks off the SDK's native-library load off the main thread during app startup, so the
     * one-time [System.loadLibrary] cost is already paid (overlapping the splash/authentication
     * screens) before the Synchronizer is constructed, shortening its cold-start critical path.
     * Best-effort: if it fails, [Synchronizer.new] will simply load the library lazily as before.
     */
    private fun preloadSdkNativeLibrary() {
        applicationScope.launch(Dispatchers.Default) {
            runCatching { Synchronizer.preloadNativeLibrary() }
                .onFailure { Twig.info { "SDK native library preload failed; will load lazily: $it" } }
        }
    }

    private fun observeSynchronizerError() {
        applicationScope.launch {
            synchronizerProvider.synchronizer
                .map { it?.initializationError }
                .collect {
                    if (it == Synchronizer.InitializationError.TOR_NOT_AVAILABLE) {
                        navigateToError(ErrorArgs.SynchronizerTorInitError)
                    }
                }
        }
    }

    private fun configureLogging() {
        Twig.initialize(applicationContext)
        Twig.info { "Starting application…" }

        if (!BuildConfig.DEBUG) {
            // In release builds, logs should be stripped by R8 rules
            Twig.assertLoggingStripped()
        }
    }

    /**
     * The `:crash` secondary process only hosts `ExceptionReceiver` and the crash content
     * provider, which are Koin-free — everything below this check assumes Koin is started.
     * [co.electriccoin.zcash.crash.android.GlobalCrashReporter.register] has its own copy of the
     * same `:crash` suffix check, using a constant `internal` to crash-android-lib, so the literal
     * is deliberately repeated here rather than shared.
     */
    private fun isCrashProcess() = ProcessNameCompat.getProcessName(this).endsWith(CRASH_PROCESS_NAME_SUFFIX)

    private fun configureStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictModeCompat.enableStrictMode(BuildConfig.IS_STRICT_MODE_CRASH_ENABLED)
        }
    }

    private fun configureAnalytics() {
        if (GlobalCrashReporter.register(this, getAvailableCrashReporters())) {
            applicationScope.launch {
                crashReportingStorageProvider.observe().collect {
                    Twig.debug { "Is crashlytics enabled: $it" }
                    if (it == true) {
                        GlobalCrashReporter.enable()
                    } else {
                        GlobalCrashReporter.disableAndDelete()
                    }
                }
            }
        }
    }
}

private const val CRASH_PROCESS_NAME_SUFFIX = ":crash"
