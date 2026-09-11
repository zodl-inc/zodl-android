package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.WalletCoordinator
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.model.PersistableWallet
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Recovers from the SDK's `SeedNotRelevant` state ([WalletCoordinator.isSeedMismatch]) by erasing the SDK
 * databases through the lockout-aware [WalletCoordinator.deleteSdkDataFlow] (wallet secrets are untouched)
 * and re-storing the wallet with [WalletInitMode.RestoreWallet] so the next synchronizer build rescans.
 * A failed or timed-out erase throws [SeedMismatchRecoveryException]; after [MAX_RECOVERY_ATTEMPTS]
 * failures no further erase is attempted. The store is skipped when the persisted wallet changed during
 * the erase (concurrent Reset Zashi or wallet restore).
 */
class RecoverFromSeedMismatchUseCase(
    private val persistableWalletProvider: PersistableWalletProvider,
    private val walletCoordinator: WalletCoordinator,
) {
    private var failedAttempts = 0

    @Suppress("ThrowsCount")
    suspend operator fun invoke() {
        val wallet = persistableWalletProvider.getPersistableWallet() ?: return

        if (failedAttempts >= MAX_RECOVERY_ATTEMPTS) {
            Twig.error {
                "Seed-mismatch recovery: giving up after $failedAttempts failed attempt(s), not retrying erase again"
            }
            throw SeedMismatchRecoveryException(failedAttempts)
        }

        val recoveryWallet = wallet.copy(walletInitMode = WalletInitMode.RestoreWallet)

        val erased =
            try {
                withTimeout(ERASE_TIMEOUT) { walletCoordinator.deleteSdkDataFlow().first() }
            } catch (e: TimeoutCancellationException) {
                failedAttempts += 1
                Twig.error(e) {
                    "Seed-mismatch recovery: deleteSdkDataFlow timed out after $ERASE_TIMEOUT " +
                        "(attempt $failedAttempts/$MAX_RECOVERY_ATTEMPTS)"
                }
                throw SeedMismatchRecoveryException(failedAttempts)
            }
        if (!erased) {
            failedAttempts += 1
            Twig.error {
                "Seed-mismatch recovery: deleteSdkDataFlow reported failure " +
                    "(attempt $failedAttempts/$MAX_RECOVERY_ATTEMPTS)"
            }
            throw SeedMismatchRecoveryException(failedAttempts)
        }

        failedAttempts = 0

        val currentWallet = persistableWalletProvider.getPersistableWallet()
        if (currentWallet == null || !currentWallet.identifiesSameWalletAs(wallet)) {
            Twig.error {
                "Seed-mismatch recovery: skipping store() — the persisted wallet changed during the erase " +
                    "(cleared by a concurrent Reset Zashi, or replaced by a concurrent wallet restore)"
            }
            return
        }

        persistableWalletProvider.store(recoveryWallet)
    }

    companion object {
        private const val MAX_RECOVERY_ATTEMPTS = 3
        private val ERASE_TIMEOUT = 30.seconds
    }
}

/**
 * Wallet identity is network plus seed phrase. `walletInitMode` is excluded because it is not serialized
 * and reflects process state rather than identity.
 */
private fun PersistableWallet.identifiesSameWalletAs(other: PersistableWallet) =
    network == other.network && seedPhrase == other.seedPhrase

/**
 * Thrown when the SDK erase fails, times out, or [RecoverFromSeedMismatchUseCase.MAX_RECOVERY_ATTEMPTS]
 * is exhausted.
 */
class SeedMismatchRecoveryException(
    val failedAttempts: Int
) : Exception("Seed-mismatch recovery failed after $failedAttempts attempt(s)")
