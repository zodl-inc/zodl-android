package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.WalletCoordinator
import cash.z.ecc.android.sdk.WalletInitMode
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.flow.first

/**
 * Recovers from the SDK reporting a seed mismatch (the `SeedNotRelevant` crash trigger, surfaced
 * as [cash.z.ecc.android.sdk.WalletCoordinator.isSeedMismatch]) by wiping the mismatched SDK
 * database and re-persisting the current wallet so the next synchronizer build rescans clean.
 *
 * [WalletCoordinator.deleteSdkDataFlow] preserves wallet secrets — the seed and app prefs are
 * never touched by the erase, it only wipes the SDK-owned databases. [store] below is not
 * "re-persisting a seed that the erase cleared"; its only job is flipping [WalletInitMode] to
 * [WalletInitMode.RestoreWallet] so the next synchronizer build treats this as a rescan of an
 * existing wallet rather than a brand-new one.
 *
 * Known trade-off, not fixed here (MOB-1397 review item 7): once [WalletCoordinator]'s
 * erase-lockout releases, the synchronizer-provider's `flatMapLatest` on
 * [WalletCoordinator.synchronizer] reacts immediately and rebuilds a synchronizer from the *old*
 * stored wallet against the freshly wiped DB, and then [store] below triggers a second rebuild
 * with the corrected [WalletInitMode]. This is a wasted double init (harmless: the first instance
 * is torn down again almost immediately, before it does meaningful work) rather than a
 * correctness bug — plus a benign process-death window between the erase completing and [store]
 * running, where the init-mode flip hasn't been applied yet (worst case on next launch: recovery
 * simply restarts from the top against an already-clean DB). Properly eliminating the double init
 * would mean persisting the recovery wallet *before* erasing so the eventual resync only happens
 * once, which needs a bigger restructure of how [WalletCoordinator] watches
 * [PersistableWalletProvider] than belongs in this fix-up.
 *
 * Bounded by [MAX_RECOVERY_ATTEMPTS]: [WalletCoordinator.deleteSdkDataFlow] reports a failed
 * erase as `false` (e.g. an engine synchronizer still keyed) rather than throwing. Blindly calling
 * [store] anyway would re-hit the same seed mismatch against a database that was never actually
 * wiped, and [co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel]'s collector would call
 * back into this use case again — an unthrottled automatic wipe-retry loop. Instead, a failed
 * erase is retried up to [MAX_RECOVERY_ATTEMPTS] times (tracked for the lifetime of this instance,
 * which is owned for the lifetime of the injecting ViewModel) and, once exhausted, further
 * invocations throw [SeedMismatchRecoveryException] without attempting another erase — an
 * explicit, observable failure instead of a silent infinite loop.
 */
class RecoverFromSeedMismatchUseCase(
    private val persistableWalletProvider: PersistableWalletProvider,
    private val walletCoordinator: WalletCoordinator,
) {
    private var failedAttempts = 0

    suspend operator fun invoke() {
        val wallet = persistableWalletProvider.getPersistableWallet() ?: return

        if (failedAttempts >= MAX_RECOVERY_ATTEMPTS) {
            Twig.error {
                "Seed-mismatch recovery: giving up after $failedAttempts failed attempt(s), not retrying erase again"
            }
            throw SeedMismatchRecoveryException(failedAttempts)
        }

        val recoveryWallet = wallet.copy(walletInitMode = WalletInitMode.RestoreWallet)

        // Use lockout-aware erase so any lingering synchronizer is stopped before the DB wipe.
        val erased = walletCoordinator.deleteSdkDataFlow().first()
        if (!erased) {
            failedAttempts += 1
            Twig.error {
                "Seed-mismatch recovery: deleteSdkDataFlow reported failure " +
                    "(attempt $failedAttempts/$MAX_RECOVERY_ATTEMPTS)"
            }
            throw SeedMismatchRecoveryException(failedAttempts)
        }

        failedAttempts = 0
        persistableWalletProvider.store(recoveryWallet)
    }

    companion object {
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }
}

/**
 * Thrown by [RecoverFromSeedMismatchUseCase] when [WalletCoordinator.deleteSdkDataFlow] reports a
 * failed erase, or when [RecoverFromSeedMismatchUseCase.MAX_RECOVERY_ATTEMPTS] has already been
 * exhausted. Serves as the explicit error-state signal callers can distinguish from "recovery
 * succeeded" rather than silently retrying forever.
 */
class SeedMismatchRecoveryException(
    val failedAttempts: Int
) : Exception("Seed-mismatch recovery failed after $failedAttempts attempt(s)")
