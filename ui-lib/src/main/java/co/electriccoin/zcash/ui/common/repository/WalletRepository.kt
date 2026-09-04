package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import cash.z.ecc.android.sdk.Synchronizer
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
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.model.OnboardingState
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.IsIronwoodAnnouncementShownStorageProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SdkEncryptedPreferenceRecoveryProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupFlagStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletRestoringStateProvider
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.preference.StandardPreferenceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface WalletRepository {
    val secretState: StateFlow<SecretState>

    val isIronwoodAnnouncementShown: StateFlow<Boolean?>

    val fastestEndpoints: StateFlow<FastestServersState>

    val walletRestoringState: StateFlow<WalletRestoringState>

    fun createNewWallet()

    suspend fun markIronwoodAnnouncementShown()

    fun restoreWallet(
        network: ZcashNetwork,
        seedPhrase: SeedPhrase,
        birthday: BlockHeight
    )

    suspend fun updateWalletEndpoint(endpoint: LightWalletEndpoint)

    fun init()

    fun refreshFastestServers()
}

class WalletRepositoryImpl(
    configurationRepository: ConfigurationRepository,
    private val application: Application,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val standardPreferenceProvider: StandardPreferenceProvider,
    private val restoreTimestampDataSource: RestoreTimestampDataSource,
    private val walletRestoringStateProvider: WalletRestoringStateProvider,
    private val walletBackupFlagStorageProvider: WalletBackupFlagStorageProvider,
    private val isIronwoodAnnouncementShownStorageProvider: IsIronwoodAnnouncementShownStorageProvider,
    private val sdkEncryptedPreferenceRecoveryProvider: SdkEncryptedPreferenceRecoveryProvider,
) : WalletRepository {
    /**
     * Scope the repository's `stateIn`-shared flows run on. Captured by those flows at
     * construction time, so unlike [scope] it is never reassigned for tests — only exposed so
     * test teardown can cancel it too.
     */
    @VisibleForTesting
    internal val sharingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Serializes the startup self-heal ([init]) against wallet creation and restore, so the erase
     * in [resetOnboardingIfWalletMissing] can never interleave with a wallet those paths are in
     * the middle of storing.
     */
    private val walletMutation = Mutex()

    /**
     * Scope the repository's fire-and-forget jobs run on. A test seam: unit tests replace it
     * with a test dispatcher before invoking any method.
     */
    @set:RestrictTo(RestrictTo.Scope.TESTS)
    @VisibleForTesting
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val refreshFastestServersRequest = MutableSharedFlow<Unit>()

    private val onboardingState =
        flow {
            emitAll(
                StandardPreferenceKeys.ONBOARDING_STATE.observe(standardPreferenceProvider()).map { persistedNumber ->
                    OnboardingState.fromNumber(persistedNumber)
                }
            )
        }

    /**
     * The encrypted wallet store is only awaited when [onboardingState] already claims a wallet
     * exists; a fresh install with the flag at its default has nothing to read there, so its
     * splash screen must not wait on the Keystore for [persistableWalletProvider]'s flow to first
     * emit.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val onboardingWithWallet: Flow<Pair<OnboardingState, Boolean>> =
        onboardingState.flatMapLatest { state ->
            if (state == OnboardingState.READY) {
                persistableWalletProvider.persistableWallet.map { state to (it != null) }
            } else {
                flowOf(state to false)
            }
        }

    /**
     * READY additionally requires a stored wallet ([resolveSecretState]), because the plain
     * onboarding flag and the encrypted wallet store can diverge when the encrypted store is
     * recreated after provable corruption (MOB-1836). This flow never catches: a transient
     * Keystore failure surfacing here is left to crash the sharing coroutine, the same terminal
     * outcome the app already reaches today via [co.electriccoin.zcash.ui.common.provider.SynchronizerProvider]
     * collecting the same underlying store, rather than misrouting an intact-but-unreadable store
     * to onboarding.
     */
    override val secretState: StateFlow<SecretState> =
        combine(
            configurationRepository.configurationFlow,
            onboardingWithWallet
        ) { config, (onboardingState, hasWallet) ->
            resolveSecretState(
                isConfigurationLoaded = config != null,
                onboardingState = onboardingState,
                hasWallet = hasWallet
            )
        }.stateIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = SecretState.LOADING
        )

    override val isIronwoodAnnouncementShown: StateFlow<Boolean?> =
        isIronwoodAnnouncementShownStorageProvider
            .observe()
            .stateIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private var previousFastestEndpoints: FastestServersState? = null

    @Suppress("ComplexCondition", "ReturnCount")
    @OptIn(ExperimentalCoroutinesApi::class)
    override val fastestEndpoints =
        refreshFastestServersRequest
            .onStart { emit(Unit) }
            .flatMapLatest {
                var synchronizerEmitted = false

                synchronizerProvider
                    .synchronizer
                    .mapLatest { synchronizer ->
                        val previousState = previousFastestEndpoints
                        val result =
                            if (synchronizer == null || (
                                    synchronizerEmitted &&
                                        !previousState?.servers.isNullOrEmpty() &&
                                        !previousState.isLoading
                                )
                            ) {
                                null
                            } else {
                                synchronizer
                            }

                        if (synchronizer != null) {
                            synchronizerEmitted = true
                        }

                        result
                    }
            }.flatMapLatest { synchronizer ->
                synchronizer
                    ?.getFastestServers(lightWalletEndpointProvider.getEndpoints())
                    ?.map {
                        when (it) {
                            FastestServersResult.Measuring -> {
                                previousFastestEndpoints?.copy(isLoading = true)
                                    ?: FastestServersState(servers = null, isLoading = true)
                            }

                            is FastestServersResult.Validating -> {
                                FastestServersState(servers = it.servers, isLoading = true)
                            }

                            is FastestServersResult.Done -> {
                                FastestServersState(servers = it.servers, isLoading = false)
                            }
                        }
                    } ?: flowOf(
                    previousFastestEndpoints ?: FastestServersState(
                        servers = emptyList(),
                        isLoading = false
                    )
                )
            }.onEach {
                previousFastestEndpoints = it
            }.stateIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = FastestServersState(servers = emptyList(), isLoading = true)
            )

    override val walletRestoringState: StateFlow<WalletRestoringState> =
        walletRestoringStateProvider
            .observe()
            .stateIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = WalletRestoringState.NONE
            )

    @Suppress("TooGenericExceptionCaught")
    override fun init() {
        scope.launch {
            try {
                walletMutation.withLock {
                    val wallet = persistableWalletProvider.getPersistableWallet()
                    val onboarding = onboardingState.first()
                    migrateDecommissionedEndpointIfNeeded(wallet)
                    resetOnboardingIfWalletMissing(wallet = wallet, onboardingState = onboarding)
                }
            } catch (e: Exception) {
                Twig.error(e) { "Startup wallet consistency checks failed; will retry on next launch" }
            }
        }
    }

    private suspend fun migrateDecommissionedEndpointIfNeeded(wallet: PersistableWallet?) {
        if (wallet == null) return
        if (wallet.endpoint.host in lightWalletEndpointProvider.getDecommissionedHosts()) {
            persistWalletInternal(wallet.copy(endpoint = lightWalletEndpointProvider.getDefaultEndpoint()))
        }
    }

    /**
     * Self-heals a wallet-less READY state (MOB-1836): the onboarding flag and the encrypted
     * wallet store live in separate preference files and can diverge when the encrypted store is
     * recreated after provable corruption. The onboarding flag is written back to NONE first, so a
     * Create/Restore the user completes while the SDK-store repair or the erase below is still
     * running cannot be clobbered by this write landing afterwards. The SDK's own encrypted store
     * is then repaired the same way (MOB-1836): it shares this app's Keystore master key but has
     * no corruption recovery of its own. Finally, any stale SDK databases are erased directly,
     * since no synchronizer exists yet at this point to route through the usual delete-and-restore
     * paths.
     *
     * [wallet] and [onboardingState] are both read by [init] under [walletMutation], which
     * [createNewWallet] and [restoreWallet] hold too, so neither can interleave with this. The
     * wallet is nevertheless re-read immediately before the erase: that erase destroys databases
     * for good, and a wallet stored outside those two paths while the SDK secret store was being
     * repaired must not be erased under it.
     */
    private suspend fun resetOnboardingIfWalletMissing(
        wallet: PersistableWallet?,
        onboardingState: OnboardingState
    ) {
        if (wallet != null || onboardingState != OnboardingState.READY) return

        Twig.warn { "Onboarding state is READY but no wallet is stored; returning to onboarding" }
        persistOnboardingStateInternal(OnboardingState.NONE)

        runCatching { sdkEncryptedPreferenceRecoveryProvider.ensureReadable() }
            .onFailure { Twig.error(it) { "Repairing the SDK secret store failed; continuing" } }

        if (persistableWalletProvider.getPersistableWallet() != null) {
            Twig.warn { "A wallet was stored while self-healing; keeping its data instead of erasing" }
            return
        }

        runCatching { Synchronizer.erase(application, ZcashNetwork.fromResources(application)) }
            .onFailure { Twig.error(it) { "Erasing stale wallet data failed; continuing" } }
    }

    override suspend fun updateWalletEndpoint(endpoint: LightWalletEndpoint) {
        val selectedWallet = persistableWalletProvider.getPersistableWallet() ?: return
        val selectedEndpoint = selectedWallet.endpoint
        if (selectedEndpoint == endpoint) return
        persistWalletInternal(selectedWallet.copy(endpoint = endpoint))
    }

    private suspend fun persistWalletInternal(persistableWallet: PersistableWallet) {
        persistableWalletProvider.store(persistableWallet)
    }

    override fun createNewWallet() {
        scope.launch {
            walletMutation.withLock {
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
                persistOnboardingStateInternal(OnboardingState.READY)
            }
        }
    }

    override suspend fun markIronwoodAnnouncementShown() {
        isIronwoodAnnouncementShownStorageProvider.store(true)
    }

    private suspend fun persistOnboardingStateInternal(onboardingState: OnboardingState) {
        StandardPreferenceKeys.ONBOARDING_STATE.putValue(
            preferenceProvider = standardPreferenceProvider(),
            newValue = onboardingState.toNumber()
        )
    }

    override fun refreshFastestServers() {
        scope.launch {
            if (!fastestEndpoints.value.isLoading) {
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
            walletMutation.withLock {
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
    }
}

/**
 * READY requires both the plain onboarding flag and a stored wallet, because the two live in
 * separate preference files and can diverge when the encrypted store is recreated after provable
 * corruption (MOB-1836).
 *
 * Every other [OnboardingState] resolves to [SecretState.NONE], a stored wallet notwithstanding.
 * [OnboardingState.NEEDS_WARN] and [OnboardingState.NEEDS_BACKUP] are ordinals of an earlier
 * onboarding flow that nothing writes any more — [WalletRepositoryImpl] only ever persists
 * [OnboardingState.NONE] or [OnboardingState.READY] — and both mean onboarding never ran to
 * completion, so routing a wallet found under them back to onboarding is the intended outcome.
 */
internal fun resolveSecretState(
    isConfigurationLoaded: Boolean,
    onboardingState: OnboardingState,
    hasWallet: Boolean,
): SecretState =
    when {
        !isConfigurationLoaded -> SecretState.LOADING
        onboardingState == OnboardingState.READY && hasWallet -> SecretState.READY
        else -> SecretState.NONE
    }
