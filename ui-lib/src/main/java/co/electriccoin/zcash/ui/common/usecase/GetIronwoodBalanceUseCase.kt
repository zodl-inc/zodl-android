package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.ZERO
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The live Ironwood pool balance for the currently selected wallet account, read straight from
 * the synchronizer (mirrors [GetOrchardBalanceUseCase]'s pattern) rather than reconstructed from
 * the migration engine's own campaign-scoped bookkeeping
 * ([cash.z.ecc.android.sdk.OrchardMigrationSdk.getMigrationSummary]).
 *
 * `total + locked`, matching [GetBalancePoolsUseCase]'s display semantics (see its kdoc for why
 * `locked` must be added back in) — this is "what the user currently owns in Ironwood", not "how
 * much this app's migration engine has moved there", which is a real but narrower fact that can
 * read zero/stale when the engine's own tracking doesn't have a row for it (e.g. after a debug
 * migration restart, or funds that arrived in Ironwood some other way).
 */
class GetIronwoodBalanceUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val accountDataSource: AccountDataSource,
) {
    suspend operator fun invoke(): Zatoshi {
        synchronizerProvider.getSynchronizer()
        val account = accountDataSource.getSelectedAccount()
        val balances = synchronizerProvider.walletBalances.filterNotNull().first()
        val ironwood = balances[account.sdkAccount.accountUuid]?.ironwood ?: return Zatoshi.ZERO
        return ironwood.total + ironwood.locked
    }
}
