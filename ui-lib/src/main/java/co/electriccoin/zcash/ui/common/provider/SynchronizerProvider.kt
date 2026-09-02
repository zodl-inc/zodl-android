package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.WalletCoordinator
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.migration.MigrationSyncedHook
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.usecase.RecoverFromSeedMismatchUseCase
import co.electriccoin.zcash.ui.common.usecase.SeedMismatchRecoveryException
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface SynchronizerProvider {
    val error: StateFlow<SynchronizerError?>

    val synchronizer: StateFlow<Synchronizer?>

    val isSeedMismatch: StateFlow<Boolean>

    /**
     * MOB-1664: [Synchronizer.walletBalances] is per-synchronizer-instance state that resets to
     * `null` every time the synchronizer is rebuilt (e.g. an automatic server switch tears down
     * and reconstructs the whole engine), which would otherwise flash every balance display
     * (top-line, per-pool breakdown) to 0.000 for the few seconds the new instance takes to
     * re-establish itself. This retains the last known non-null value across that gap so
     * consumers never observe a spurious drop to zero. Central here (rather than patched into
     * each consumer) since [AccountDataSource], [co.electriccoin.zcash.ui.common.usecase.GetBalancePoolsUseCase],
     * [co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase] and
     * [co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel] all read this independently.
     */
    val walletBalances: Flow<Map<AccountUuid, AccountBalance>?>

    /**
     * Get synchronizer and wait for it to be ready.
     */
    suspend fun getSynchronizer(): Synchronizer

    /**
     * Returns null if there is no persistable wallet, otherwise waits for the loaded synchronizer.
     */
    suspend fun getSynchronizerOrNull(): Synchronizer?

    suspend fun getVotingWalletDbPath(): String

    fun resetSynchronizer()
}

@OptIn(ExperimentalCoroutinesApi::class)
class SynchronizerProviderImpl(
    private val walletCoordinator: WalletCoordinator,
    private val persistableWalletProvider: PersistableWalletProvider,
    // Lazy on purpose: the hook's implementation resolves (via AccountDataSource) back to
    // SynchronizerProvider — eager constructor injection forms a Koin resolution cycle that
    // crashes startup with a StackOverflowError (caught on-emulator 2026-07-28). Resolved at
    // first hook fire instead, when the graph is fully built.
    private val migrationSyncedHook: Lazy<MigrationSyncedHook>,
    private val recoverFromSeedMismatch: RecoverFromSeedMismatchUseCase,
    private val navigateToErrorUseCase: NavigateToErrorUseCase,
) : SynchronizerProvider {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val error = MutableStateFlow<SynchronizerError?>(null)

    override val isSeedMismatch: StateFlow<Boolean> = walletCoordinator.isSeedMismatch

    @OptIn(ExperimentalCoroutinesApi::class)
    override val synchronizer: StateFlow<Synchronizer?> =
        walletCoordinator
            .synchronizer
            .flatMapLatest { synchronizer ->
                channelFlow {
                    if (synchronizer != null) {
                        val pipeline = initializeErrorHandling(synchronizer)

                        launch {
                            pipeline.collect { new ->
                                error.update { new }
                            }
                        }
                    }

                    send(synchronizer)
                    awaitClose {
                        synchronizer?.onProcessorErrorHandler = null
                        synchronizer?.onProcessorErrorResolved = null
                        synchronizer?.onSetupErrorHandler = null
                        synchronizer?.onChainErrorHandler = null
                    }
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Lazily,
                initialValue = walletCoordinator.synchronizer.value
            )

    private val lastKnownWalletBalances = MutableStateFlow<Map<AccountUuid, AccountBalance>?>(null)

    override val walletBalances: Flow<Map<AccountUuid, AccountBalance>?> =
        synchronizer
            .flatMapLatest { it?.walletBalances ?: flowOf(null) }
            .onEach { if (it != null) lastKnownWalletBalances.value = it }
            .map { it ?: lastKnownWalletBalances.value }

    init {
        scope.launch {
            synchronizer
                .flatMapLatest { s -> s?.status ?: emptyFlow() }
                .distinctUntilChanged()
                .collect { status ->
                    if (status == Synchronizer.Status.SYNCED) {
                        runCatching { migrationSyncedHook.value.onSynced() }
                            .onFailure { Twig.warn { "MIGRATION_DIAG foreground SYNCED hook: ${it.message}" } }
                    }
                }
        }
        startSeedMismatchRecoveryCollector()
    }

    /**
     * MOB-1397: auto-triggers [RecoverFromSeedMismatchUseCase] the moment
     * [WalletCoordinator.isSeedMismatch] flips to `true`, with no user interaction required.
     *
     * Lives here — on [scope], which is scoped to this singleton provider — rather than on an
     * activity-scoped ViewModel (where this collector previously lived), for three reasons:
     * 1. UI-lifecycle independence: a mismatch hit by background synchronizer use (e.g. a
     *    [co.electriccoin.zcash.ui.common.migration.MigrationWorker] run) recovers immediately
     *    instead of waiting for the UI to next be opened.
     * 2. Exactly one collector: an activity-scoped ViewModel can end up constructed more than
     *    once in the same process (observed: [co.electriccoin.zcash.ui.common.viewmodel.AuthenticationViewModel]
     *    constructor-injecting [co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel] creates
     *    a second, never-cleared instance), which would fan out into duplicate concurrent
     *    destructive erases against the same wallet. This provider is a Koin single, so there is
     *    only ever one collector.
     * 3. It naturally covers [co.electriccoin.zcash.ui.common.migration.MigrationWorker]'s
     *    background-only synchronizer use, which never has a ViewModel around to react at all.
     */
    private fun startSeedMismatchRecoveryCollector() {
        scope.launch {
            walletCoordinator.isSeedMismatch
                .filter { it }
                .collect { runRecovery() }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runRecovery() {
        try {
            recoverFromSeedMismatch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SeedMismatchRecoveryException) {
            Twig.error(e) { "Auto-recovery from seed mismatch failed (terminal, retries exhausted)" }
            navigateToErrorUseCase(ErrorArgs.General(e))
        } catch (e: Exception) {
            Twig.error(e) { "Auto-recovery from seed mismatch failed" }
        }
    }

    override suspend fun getSynchronizer(): Synchronizer =
        withContext(Dispatchers.IO) {
            synchronizer
                .filterNotNull()
                .first()
        }

    override suspend fun getSynchronizerOrNull(): Synchronizer? =
        withContext(Dispatchers.IO) {
            if (persistableWalletProvider.getPersistableWallet() == null) {
                null
            } else {
                getSynchronizer()
            }
        }

    override suspend fun getVotingWalletDbPath(): String =
        getSynchronizer().getWalletDbPathForVoting()

    override fun resetSynchronizer() {
        walletCoordinator.resetSynchronizer()
    }

    private fun initializeErrorHandling(synchronizer: Synchronizer): Flow<SynchronizerError?> {
        val pipeline = MutableStateFlow<SynchronizerError?>(null)

        // synchronizer.onCriticalErrorHandler = { error ->
        //     Twig.error { "WALLET - Error Critical: $error" }
        //     pipeline.update { SynchronizerError.Critical(error)}
        //     false
        // }
        synchronizer.onProcessorErrorHandler = { error ->
            Twig.error { "WALLET - Error Processor: $error" }
            pipeline.update { SynchronizerError.Processor(error) }
            true
        }
        synchronizer.onProcessorErrorResolved = {
            Twig.error { "WALLET - Processor error resolved" }
            pipeline.update { null }
        }
        synchronizer.onSetupErrorHandler = { error ->
            Twig.error { "WALLET - Error Setup: $error" }
            pipeline.update { SynchronizerError.Setup(error) }
            false
        }
        synchronizer.onChainErrorHandler = { x, y ->
            Twig.error { "WALLET - Error Chain: $x, $y" }
            pipeline.update { SynchronizerError.Chain(x, y) }
        }

        return pipeline
    }
}
