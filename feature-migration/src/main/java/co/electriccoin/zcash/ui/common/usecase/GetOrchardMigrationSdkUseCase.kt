package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.MigrationGateBypassStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider

/**
 * Resolves the real, Rust-backed [OrchardMigrationSdk] for the wallet account the migration flow
 * is actually running against.
 *
 * [OrchardMigrationSdk.new] needs the wallet's network and lightwalletd endpoint (only known once
 * a wallet exists — read from [PersistableWalletProvider]) and the specific account (whichever one
 * is currently selected in the app — Zodl/Keystone or Zashi — via [GetSelectedWalletAccountUseCase],
 * never auto-picked by the SDK itself). This use case is the single seam combining all three, so
 * every migration call site injects just this instead of wiring them separately.
 *
 * The no-arg [invoke] returns non-null: it suspends until a selected account exists (see
 * [AccountDataSource.getSelectedAccount]) and throws if no wallet is persisted by then — a wallet
 * is a precondition for every migration flow, so callers treat absence as a programming error
 * rather than a state to branch on. Callers on general, wallet-independent code paths
 * (`CheckMigrationRecoveryUseCase` runs on every `MainActivity` launch, including a fresh install
 * before onboarding) must therefore gate on wallet existence BEFORE calling this — the account
 * wait would otherwise suspend them indefinitely on a wallet-less install.
 *
 * Unlike `WalletCoordinatorFactory`'s own `OrchardMigrationSdk.new(... account = null)` call (which
 * gates sync before any `Synchronizer`/account selection exists), this always resolves a real
 * selected account whenever a wallet exists.
 *
 * An explicit-account overload [invoke(accountKeyId)] is provided for contexts where the account
 * is known by its storage key ID (e.g. [co.electriccoin.zcash.work.MigrationWorker]) rather than
 * being read from the current selection.
 */
class GetOrchardMigrationSdkUseCase(
    private val context: Context,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val accountDataSource: AccountDataSource,
    private val gateBypassStorage: MigrationGateBypassStorageProvider,
) {
    /** Resolves the SDK for the currently selected wallet account. */
    suspend operator fun invoke(): OrchardMigrationSdk =
        buildFor(getSelectedWalletAccount())

    /**
     * Like [invoke], but distinguishes WHY the SDK is unavailable so self-rechaining workers can
     * tell a boot race (retry) from a deleted wallet / disconnected account (kill the chain —
     * a worker retrying forever for an account that no longer exists is a zombie).
     */
    suspend fun lookup(accountKeyId: String): MigrationSdkLookup {
        persistableWalletProvider.getPersistableWallet()
            ?: return MigrationSdkLookup.Gone // the wallet itself was deleted
        val accounts = runCatching { accountDataSource.getAllAccounts() }.getOrNull()
        if (accounts.isNullOrEmpty()) return MigrationSdkLookup.NotReady // boot race — accounts not loaded yet
        val account = accounts.findByAccountKeyId(accountKeyId) ?: return MigrationSdkLookup.Gone
        val sdk = buildFor(account)
        return MigrationSdkLookup.Ready(sdk)
    }

    /**
     * Resolves the SDK for the account identified by [accountKeyId] (its
     * [co.electriccoin.zcash.ui.common.model.toStorageKeyId] value), or `null` if no such account
     * exists or there is no persisted wallet. [lookup] is the boot-race-aware alternative for
     * self-rechaining workers; this one-shot form suits callers that already know the account
     * must exist right now and simply degrade on absence.
     */
    suspend operator fun invoke(accountKeyId: String): OrchardMigrationSdk? {
        val account =
            accountDataSource.getAllAccounts().findByAccountKeyId(accountKeyId) ?: run {
                migrationLog("GetOrchardMigrationSdk: no account for keyId=$accountKeyId")
                return null
            }
        return buildFor(account)
    }

    private suspend fun buildFor(account: WalletAccount): OrchardMigrationSdk {
        val wallet = persistableWalletProvider.requirePersistableWallet()
        return OrchardMigrationSdk
            .new(
                appContext = context,
                zcashNetwork = wallet.network,
                lightWalletEndpoint = wallet.endpoint,
                account = account.sdkAccount.accountUuid,
            ).also {
                // The SDK's flag is process-wide: re-apply the stored switch so a restart, or a wallet reset that
                // cleared it, never leaves a stale value behind.
                it.setMigrationNetworkUpgradeGateBypass(gateBypassStorage.isEnabled())
            }
    }
}

/**
 * Returns the first [WalletAccount] in the list whose
 * [co.electriccoin.zcash.ui.common.model.toStorageKeyId] matches [accountKeyId], or `null` if
 * none match.
 */
internal fun List<WalletAccount>.findByAccountKeyId(accountKeyId: String): WalletAccount? =
    firstOrNull { it.sdkAccount.accountUuid.toStorageKeyId() == accountKeyId }

/** Result of [GetOrchardMigrationSdkUseCase.lookup] — see its kdoc. */
sealed interface MigrationSdkLookup {
    data class Ready(
        val sdk: OrchardMigrationSdk
    ) : MigrationSdkLookup

    /** The wallet or this account no longer exists — migration work must cancel itself. */
    data object Gone : MigrationSdkLookup

    /** Persistence/SDK not reachable yet (typically right after an app update/reboot) — retry. */
    data object NotReady : MigrationSdkLookup
}
