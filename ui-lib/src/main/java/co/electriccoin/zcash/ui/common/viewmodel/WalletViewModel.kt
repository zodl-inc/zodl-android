package co.electriccoin.zcash.ui.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.screen.ironwood.IronwoodActivation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

// To make this more multiplatform compatible, we need to remove the dependency on Context
// for loading the preferences.
// TODO [#292]: Should be moved to SDK-EXT-UI module.
// TODO [#292]: https://github.com/Electric-Coin-Company/zashi-android/issues/292
class WalletViewModel(
    synchronizerProvider: SynchronizerProvider,
    private val walletRepository: WalletRepository,
) : ViewModel() {
    val synchronizer = synchronizerProvider.synchronizer

    val secretState: StateFlow<SecretState> = walletRepository.secretState

    /**
     * Emits `true` once — on the first launch that satisfies all Ironwood-announcement conditions:
     * the wallet has synced past the Ironwood activation height, holds a non-zero spendable Orchard
     * balance, and the one-time announcement has not been shown yet. Stays `false` while syncing or
     * while the balance is unknown.
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
                        synchronizer.walletBalances,
                        walletRepository.isIronwoodAnnouncementShown,
                    ) { scannedHeight, balances, isShown ->
                        val activationHeight = IronwoodActivation.heightFor(synchronizer.network)
                        // `isShown` is null when never set and true once dismissed — show while it is not true.
                        isShown != true &&
                            scannedHeight != null &&
                            scannedHeight >= activationHeight &&
                            balances != null &&
                            balances.values.any { it.orchard.available.value > 0L }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
                initialValue = false
            )

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
