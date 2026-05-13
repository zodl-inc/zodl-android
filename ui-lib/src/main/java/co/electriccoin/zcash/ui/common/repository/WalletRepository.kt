package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FastestServersResult
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import cash.z.ecc.sdk.type.fromResources
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.RestoreTimestampDataSource
import co.electriccoin.zcash.ui.common.model.ConnectionMode
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.model.OnboardingState
import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupFlagStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletRestoringStateProvider
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.preference.StandardPreferenceKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface WalletRepository {
    val secretState: StateFlow<SecretState>

    val fastestEndpoints: StateFlow<FastestServersState>

    val walletRestoringState: StateFlow<WalletRestoringState>

    fun createNewWallet()

    fun restoreWallet(
        network: ZcashNetwork,
        seedPhrase: SeedPhrase,
        birthday: BlockHeight
    )

    suspend fun updateWalletEndpoint(endpoint: LightWalletEndpoint)

    fun refreshFastestServers()
}

class WalletRepositoryImpl(
    configurationRepository: ConfigurationRepository,
    private val application: Application,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val serverSelectionProvider: ServerSelectionProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val standardPreferenceProvider: StandardPreferenceProvider,
    private val restoreTimestampDataSource: RestoreTimestampDataSource,
    private val walletRestoringStateProvider: WalletRestoringStateProvider,
    private val walletBackupFlagStorageProvider: WalletBackupFlagStorageProvider,
) : WalletRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val refreshFastestServersRequest = MutableSharedFlow<Unit>(replay = 1)

    private val endpointUpdateMutex = Mutex()

    private val onboardingState =
        flow {
            emitAll(
                StandardPreferenceKeys.ONBOARDING_STATE.observe(standardPreferenceProvider()).map { persistedNumber ->
                    OnboardingState.fromNumber(persistedNumber)
                }
            )
        }

    override val secretState: StateFlow<SecretState> =
        combine(configurationRepository.configurationFlow, onboardingState) { config, onboardingState ->
            if (config == null) {
                SecretState.LOADING
            } else {
                when (onboardingState) {
                    OnboardingState.NEEDS_WARN,
                    OnboardingState.NEEDS_BACKUP,
                    OnboardingState.NONE -> SecretState.NONE

                    OnboardingState.READY -> SecretState.READY
                }
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = SecretState.LOADING
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val fastestEndpoints =
        channelFlow {
            var previousFastestServerState: FastestServersState? = null

            combine(
                refreshFastestServersRequest.onStart { emit(Unit) },
                synchronizerProvider.synchronizer
            ) { _, synchronizer -> synchronizer }
                .withIndex()
                .flatMapLatest { (_, synchronizer) ->
                    synchronizer
                        ?.getFastestServers(lightWalletEndpointProvider.getEndpoints())
                        ?.map {
                            when (it) {
                                FastestServersResult.Measuring -> {
                                    previousFastestServerState?.copy(isLoading = true)
                                        ?: FastestServersState(servers = null, isLoading = true)
                                }

                                is FastestServersResult.Validating -> {
                                    FastestServersState(servers = it.servers, isLoading = true)
                                }

                                is FastestServersResult.Done -> {
                                    FastestServersState(servers = it.servers, isLoading = false)
                                }
                            }
                        } ?: emptyFlow()
                }.onEach {
                    previousFastestServerState = it
                    send(it)
                }.launchIn(this)
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = FastestServersState(servers = emptyList(), isLoading = true)
        )

    init {
        scope.launch {
            migrateServerSelectionIfNeeded()
            keepSelectedEndpointUpdated()
        }
    }

    override val walletRestoringState: StateFlow<WalletRestoringState> =
        walletRestoringStateProvider
            .observe()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = WalletRestoringState.NONE
            )

    override suspend fun updateWalletEndpoint(endpoint: LightWalletEndpoint) = updateWalletEndpointInternal(endpoint)

    private suspend fun updateWalletEndpointInternal(endpoint: LightWalletEndpoint) {
        endpointUpdateMutex.withLock {
            val selectedWallet = persistableWalletProvider.getPersistableWallet() ?: return
            val selectedEndpoint = selectedWallet.endpoint
            if (selectedEndpoint == endpoint) return
            persistWalletInternal(selectedWallet.copy(endpoint = endpoint))
        }
    }

    private suspend fun persistWalletInternal(persistableWallet: PersistableWallet) {
        synchronizerProvider.synchronizer.firstOrNull()?.let { (it as? SdkSynchronizer)?.close() }
        persistableWalletProvider.store(persistableWallet)
    }

    override fun createNewWallet() {
        scope.launch {
            persistOnboardingStateInternal(OnboardingState.READY)
            serverSelectionProvider.store(ServerSelection.automatic())
            val zcashNetwork = ZcashNetwork.fromResources(application)
            val newWallet =
                PersistableWallet.new(
                    application = application,
                    zcashNetwork = zcashNetwork,
                    endpoint = lightWalletEndpointProvider.getDefaultEndpoint(),
                    walletInitMode = WalletInitMode.NewWallet,
                )
            persistWalletInternal(newWallet)
            walletRestoringStateProvider.store(WalletRestoringState.INITIATING)
        }
    }

    private suspend fun persistOnboardingStateInternal(onboardingState: OnboardingState) {
        StandardPreferenceKeys.ONBOARDING_STATE.putValue(
            preferenceProvider = standardPreferenceProvider(),
            newValue = onboardingState.toNumber()
        )
    }

    override fun refreshFastestServers() {
        scope.launch {
            if (!fastestEndpoints.first().isLoading) {
                refreshFastestServersRequest.emit(Unit)
            }
        }
    }

    override fun restoreWallet(
        network: ZcashNetwork,
        seedPhrase: SeedPhrase,
        birthday: BlockHeight
    ) {
        scope.launch {
            serverSelectionProvider.store(ServerSelection.automatic())
            val restoredWallet =
                PersistableWallet(
                    network = network,
                    birthday = birthday,
                    endpoint = lightWalletEndpointProvider.getDefaultEndpoint(),
                    seedPhrase = seedPhrase,
                    walletInitMode = WalletInitMode.RestoreWallet,
                )
            persistWalletInternal(restoredWallet)
            walletRestoringStateProvider.store(WalletRestoringState.RESTORING)
            walletBackupFlagStorageProvider.store(true)
            restoreTimestampDataSource.getOrCreate()
            persistOnboardingStateInternal(OnboardingState.READY)
        }
    }

    private suspend fun migrateServerSelectionIfNeeded() {
        if (serverSelectionProvider.getServerSelection() != null) return

        val existingWallet = persistableWalletProvider.getPersistableWallet()
        val selection =
            ServerSelection.fromPersistedEndpoint(
                endpoint = existingWallet?.endpoint,
                knownEndpoints = lightWalletEndpointProvider.getEndpoints()
            )

        if (serverSelectionProvider.getServerSelection() == null) {
            serverSelectionProvider.store(selection)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun keepSelectedEndpointUpdated() {
        serverSelectionProvider
            .serverSelection
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { selection ->
                try {
                    when (selection.mode) {
                        ConnectionMode.AUTOMATIC -> {
                            updateAutomaticEndpointOnce()
                        }

                        ConnectionMode.MANUAL -> {
                            selection.endpoint?.let { updateWalletEndpointInternal(it) }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Twig.error(e) { "Unable to update selected server endpoint" }
                }
            }
    }

    private suspend fun updateAutomaticEndpointOnce() {
        val endpoint =
            fastestEndpoints
                .first { !it.isLoading }
                .servers
                ?.firstOrNull()
                ?: return

        if (serverSelectionProvider.getServerSelection()?.mode != ConnectionMode.AUTOMATIC) {
            return
        }

        updateWalletEndpointInternal(endpoint)
    }
}
