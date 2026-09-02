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
 * wiped, and the collector re-invoking this use case on every `isSeedMismatch` flip would turn
 * into an unthrottled automatic wipe-retry loop. Instead, a failed erase is retried up to
 * [MAX_RECOVERY_ATTEMPTS] times (tracked for the lifetime of this instance, which is
 * process-scoped — see this use case's Koin registration) and, once exhausted, further
 * invocations throw [SeedMismatchRecoveryException] without attempting another erase — an
 * explicit, observable failure instead of a silent infinite loop.
 *
 * The erase goes through the lockout-aware [WalletCoordinator.deleteSdkDataFlow] so any lingering
 * synchronizer is stopped before the DB wipe, and is bounded by [ERASE_TIMEOUT]: with a
 * concurrently nulled wallet (e.g. Reset
 * Zashi racing this recovery), [WalletCoordinator.deleteSdkDataFlow]'s inner `first()`-read of the
 * persisted wallet can return null and the flow never emits or closes, which would otherwise hang
 * this use case — and by extension the collector invoking it — forever. A timeout here counts as a
 * failed attempt exactly like an explicit `false` result.
 *
 * [store] guards against the same reset race on its own side: after a successful erase, the
 * currently persisted wallet is re-read and compared against the wallet snapshotted at the start
 * of this invocation. If it is null (Reset Zashi cleared prefs concurrently) or identifies a
 * different wallet (the user restored another wallet mid-erase), [store] is skipped — calling it
 * unconditionally would resurrect a deleted wallet's seed, or overwrite a newly restored wallet
 * with the stale, now-wiped one.
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
 * Identity comparison for [PersistableWallet] used to detect whether the persisted wallet changed
 * out from under [RecoverFromSeedMismatchUseCase] while its erase was in flight. Deliberately
 * narrower than [PersistableWallet.equals]: `walletInitMode` is excluded from JSON serialization
 * and re-populated from process-global state on deserialization (see the SDK's
 * `PersistableWallet.from`), so comparing it here would compare unrelated process state rather
 * than wallet identity. Network plus seed phrase is exactly the wallet's identity — the same seed
 * on the same network is the same wallet regardless of birthday, endpoint, or init-mode churn.
 */
private fun PersistableWallet.identifiesSameWalletAs(other: PersistableWallet) =
    network == other.network && seedPhrase == other.seedPhrase

/**
 * Thrown by [RecoverFromSeedMismatchUseCase] when [WalletCoordinator.deleteSdkDataFlow] reports a
 * failed erase, times out, or when [RecoverFromSeedMismatchUseCase.MAX_RECOVERY_ATTEMPTS] has
 * already been exhausted. Serves as the explicit error-state signal callers can distinguish from
 * "recovery succeeded" rather than silently retrying forever.
 */
class SeedMismatchRecoveryException(
    val failedAttempts: Int
) : Exception("Seed-mismatch recovery failed after $failedAttempts attempt(s)")
