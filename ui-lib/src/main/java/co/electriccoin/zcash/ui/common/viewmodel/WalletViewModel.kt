package co.electriccoin.zcash.ui.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.usecase.RecoverFromSeedMismatchUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

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
