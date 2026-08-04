package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.ZERO
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Observes the balance of the selected account broken down per Zcash pool.
 *
 * Unlike [AccountDataSource] (which aggregates Orchard + Ironwood into a single unified balance),
 * this reads the raw per-pool balances from the SDK so the UI can present each pool separately.
 * Ironwood reflects whatever the SDK reports; it is [Zatoshi.ZERO] when the pool is empty or not
 * yet reported.
 */
class GetBalancePoolsUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val accountDataSource: AccountDataSource,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<BalancePools?> =
        combine(
            synchronizerProvider.synchronizer,
            accountDataSource.selectedAccount
        ) { synchronizer, account ->
            synchronizer to account
        }.flatMapLatest { (synchronizer, account) ->
            if (synchronizer == null || account == null) {
                flowOf(null)
            } else {
                synchronizer.walletBalances.map { balances ->
                    val balance = balances?.get(account.sdkAccount.accountUuid)
                    val orchard = balance?.orchard?.total ?: Zatoshi.ZERO
                    val sapling = balance?.sapling?.total ?: Zatoshi.ZERO
                    val transparent = balance?.unshielded ?: Zatoshi.ZERO
                    val ironwood = balance?.ironwood?.total ?: Zatoshi.ZERO
                    BalancePools(
                        total = orchard + sapling + transparent + ironwood,
                        orchard = orchard,
                        sapling = sapling,
                        transparent = transparent,
                        ironwood = ironwood,
                    )
                }
            }
        }
}

data class BalancePools(
    val total: Zatoshi,
    val orchard: Zatoshi,
    val sapling: Zatoshi,
    val transparent: Zatoshi,
    val ironwood: Zatoshi,
)
