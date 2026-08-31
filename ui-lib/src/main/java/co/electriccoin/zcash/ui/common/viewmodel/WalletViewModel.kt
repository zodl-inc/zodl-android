package co.electriccoin.zcash.ui.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.migration.MIGRATION_DUST_THRESHOLD_ZATOSHI
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.usecase.RecoverFromSeedMismatchUseCase
import co.electriccoin.zcash.ui.screen.ironwood.IronwoodActivation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

// To make this more multiplatform compatible, we need to remove the dependency on Context
// for loading the preferences.
// TODO [#292]: Should be moved to SDK-EXT-UI module.
// TODO [#292]: https://github.com/Electric-Coin-Company/zashi-android/issues/292
class WalletViewModel(
    synchronizerProvider: SynchronizerProvider,
    private val walletRepository: WalletRepository,
    private val recoverFromSeedMismatch: RecoverFromSeedMismatchUseCase,
) : ViewModel() {
    val synchronizer = synchronizerProvider.synchronizer

    val isSeedMismatch: StateFlow<Boolean> = synchronizerProvider.isSeedMismatch

    val secretState: StateFlow<SecretState> = walletRepository.secretState

    /**
     * Emits `true` once — on the first launch that satisfies all Ironwood-announcement conditions:
     * the wallet has synced past the Ironwood activation height, holds a spendable Orchard balance
     * above the dust threshold, and the one-time announcement has not been shown yet. Stays `false`
     * while syncing or while the balance is unknown.
     *
     * Gated on [MIGRATION_DUST_THRESHOLD_ZATOSHI] rather than a bare `> 0L`, matching the same
     * dust floor `migrationMessageFor`'s home banner uses (MOB-1620): `isIronwoodAnnouncementShown`
     * is a local flag that resets on wallet re-import even though on-chain state didn't change, so
     * a wallet with only dust-level residual Orchard balance (e.g. a previously locked residue)
     * used to re-trigger this full-screen announcement on every re-import — a bare `> 0L` check
     * doesn't distinguish that from a genuinely unmigrated balance worth announcing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val shouldShowIronwoodAnnouncement: StateFlow<Boolean> =
        synchronizer
            .flatMapLatest { synchronizer ->
                if (synchronizer == null) {
                    flowOf(false)
                } else {
                    combine(
                        synchronizer.fullyScannedHeight,
                        synchronizerProvider.walletBalances,
                        walletRepository.isIronwoodAnnouncementShown,
                    ) { scannedHeight, balances, isShown ->
                        val activationHeight = IronwoodActivation.heightFor(synchronizer.network)
                        // `isShown` is null when never set and true once dismissed — show while it is not true.
                        isShown != true &&
                            scannedHeight != null &&
                            scannedHeight >= activationHeight &&
                            balances != null &&
                            balances.values.any { it.orchard.available.value > MIGRATION_DUST_THRESHOLD_ZATOSHI }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
                initialValue = false
            )

    // MOB-1397 review, follow-up not addressed here (lower-priority, needs a larger restructure
    // than this fix-up's scope):
    // 1. This recovery is anchored to an activity-scoped ViewModel, so a mismatch hit by
    //    background synchronizer use won't auto-recover until the UI is next opened. The
    //    provider/repository layer (e.g. SynchronizerProvider itself) would be a more robust
    //    home for this collector so it runs regardless of UI lifecycle.
    // 2. If the wallet is concurrently nulled out (Reset Zashi races this), deleteSdkDataFlow()
    //    may never emit and RecoverFromSeedMismatchUseCase's `.first()` suspends forever. Not
    //    observed in practice, but worth guarding (e.g. a timeout or cooperating with the reset
    //    flow) in a follow-up.
    init {
        viewModelScope.launch {
            isSeedMismatch
                .filter { it }
                .collect {
                    runCatching { recoverFromSeedMismatch() }
                        .onFailure { Twig.error(it) { "Auto-recovery from seed mismatch failed" } }
                }
        }
    }

    fun createNewWallet() {
        walletRepository.createNewWallet()
    }

    fun persistExistingWalletWithSeedPhrase(
        network: ZcashNetwork,
        seedPhrase: SeedPhrase,
        birthday: BlockHeight
    ) {
        walletRepository.restoreWallet(network, seedPhrase, birthday)
    }
}

/**
 * Represents the state of the wallet secret.
 */
enum class SecretState {
    LOADING,
    NONE,
    READY
}

/**
 * This constant sets the default limitation on the length of the stack trace in the [co.electriccoin.zcash.ui.common.model.SynchronizerError]
 */
const val STACKTRACE_LIMIT = 250

// TODO [#529]: Localize Synchronizer Errors
// TODO [#529]: https://github.com/Electric-Coin-Company/zashi-android/issues/529
