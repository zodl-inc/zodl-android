package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.ZERO
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.retainWhileWalletExists
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * The real, spendable Orchard balance for the currently selected wallet account — the balance
 * migration actually moves to Ironwood.
 *
 * Reads the raw, un-folded per-pool Orchard balance directly from the synchronizer, mirroring
 * [GetBalancePoolsUseCase]'s pattern. This deliberately avoids
 * `co.electriccoin.zcash.ui.common.model.WalletAccount.unified`: that field folds Orchard +
 * Ironwood balances together, so once migration completes (real Orchard = 0) it would keep
 * reporting the full post-migration total as "still Orchard" — which is exactly the bug that made
 * the home screen's "Migration required" banner stick around forever after a full migration.
 */
class GetOrchardBalanceUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val accountDataSource: AccountDataSource,
    private val persistableWalletProvider: PersistableWalletProvider,
) {
    suspend operator fun invoke(): Zatoshi {
        val synchronizer = synchronizerProvider.getSynchronizer()
        val account = accountDataSource.getSelectedAccount()
        val balances = synchronizer.walletBalances.filterNotNull().first()
        return balances[account.sdkAccount.accountUuid]?.orchard?.available ?: Zatoshi.ZERO
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<Zatoshi?> =
        combine(
            accountDataSource.selectedAccount,
            synchronizerProvider.synchronizer.flatMapLatest { it?.walletBalances ?: flowOf(null) },
        ) { account, balances ->
            if (account == null || balances == null) {
                null
            } else {
                balances[account.sdkAccount.accountUuid]?.orchard?.available
            }
        }.retainWhileWalletExists(persistableWalletProvider)
}
