package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.retainWhileWalletExists
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Observes the balance of the selected account broken down per Zcash pool.
 *
 * Unlike [AccountDataSource] (which aggregates Orchard + Ironwood into a single unified balance),
 * this reads the raw per-pool balances from the SDK so the UI can present each pool separately.
 * Every field of [cash.z.ecc.android.sdk.model.AccountBalance] is non-null, so once the account's
 * entry is found in the map there is nothing left to default to zero — a missing entry (map
 * present but the account not in it yet) is the only "not loaded" case, and it emits null.
 *
 * Each pool's displayed total is `raw.total + raw.locked` — [cash.z.ecc.android.sdk.model.WalletBalance.locked]
 * is real, owned value the wallet currently sees as committed to a transaction proposal or PCZT
 * (e.g. a migration transfer's Orchard input notes from the moment it's proved), which
 * [cash.z.ecc.android.sdk.model.WalletBalance.total] deliberately excludes (it keeps its
 * established meaning for every other consumer in the app — see that field's kdoc in the SDK).
 * Without adding [cash.z.ecc.android.sdk.model.WalletBalance.locked] back in here, that value
 * would be invisible on this screen even though it is genuinely still the user's — confirmed live
 * 2026-08-06 during an Orchard->Ironwood migration, where funds appeared to vanish from the
 * Balance Breakdown sheet for as long as a transfer's notes stayed locked.
 *
 * This reads a real field straight from the wallet's own accounting (`Balance::locked_value()` in
 * the SDK's Rust dependency) rather than reconstructing an approximation of it from live migration
 * transfer states, which an earlier version of this use case did — see git history
 * (`MigrationPoolCorrectionSource`, removed 2026-08-06) for the multi-round saga that motivated
 * switching to this simpler, more direct source of truth: no migration-specific code needed here
 * at all, no async correction call to race against the balance stream, no window where the two can
 * disagree.
 */
class GetBalancePoolsUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val accountDataSource: AccountDataSource,
    private val persistableWalletProvider: PersistableWalletProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<BalancePools?> =
        combine(
            accountDataSource.selectedAccount,
            synchronizerProvider.synchronizer.flatMapLatest { it?.walletBalances ?: flowOf(null) },
        ) { account, balances ->
            if (account == null || balances == null) return@combine null
            val balance = balances[account.sdkAccount.accountUuid] ?: return@combine null
            val orchard = balance.orchard.total + balance.orchard.locked
            val sapling = balance.sapling.total + balance.sapling.locked
            val ironwood = balance.ironwood.total + balance.ironwood.locked
            val transparent = balance.unshielded
            BalancePools(
                total = orchard + sapling + transparent + ironwood,
                orchard = orchard,
                sapling = sapling,
                transparent = transparent,
                ironwood = ironwood,
            )
        }.retainWhileWalletExists(persistableWalletProvider)
}

data class BalancePools(
    val total: Zatoshi,
    val orchard: Zatoshi,
    val sapling: Zatoshi,
    val transparent: Zatoshi,
    val ironwood: Zatoshi,
)
